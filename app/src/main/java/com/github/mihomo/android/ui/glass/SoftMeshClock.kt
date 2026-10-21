package com.github.mihomo.android.ui.glass

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Fixed-time injection for actual Compose previews/tests, not a second renderer. */
internal val LocalSoftMeshPaused = staticCompositionLocalOf<() -> Boolean> { { false } }
internal val LocalSoftMeshTime = staticCompositionLocalOf<State<Float>?> { null }
internal const val MESH_UPDATE_DELAY_MS = 42L // <=24 Hz; align each update to a display frame.

/** One timeline/route for main tabs and sliding sub-screens: navigation never resets the field. */
@Composable
internal fun ProvideSoftMeshMotion(paused: () -> Boolean = { false }, content: @Composable () -> Unit) {
    if (!GlassDefaults.enabled) {
        content()
        return
    }
    val time = rememberSoftMeshTime(paused)
    val seed = remember { kotlin.random.Random.nextLong() }
    CompositionLocalProvider(LocalSoftMeshTime provides time, LocalSoftMeshSeed provides seed, LocalSoftMeshPaused provides paused, content = content)
}

/** Accumulates visible time only: resuming never jumps over time spent in the background. */
internal class MeshActiveTime {
    var seconds: Float = 0f
        private set
    private var lastNanos: Long? = null

    fun pause() { lastNanos = null }

    fun frame(nanos: Long): Float {
        lastNanos?.let { previous ->
            // A stalled UI must not make a large visual jump on the next frame.
            seconds += ((nanos - previous).coerceIn(0L, 100_000_000L) / 1_000_000_000.0).toFloat()
        }
        lastNanos = nanos
        return seconds
    }
}

internal fun meshMayAnimate(resumed: Boolean, focused: Boolean, shown: Boolean, animations: Boolean) =
    resumed && focused && shown && animations

/** State is read ONLY in drawBehind. No per-frame recomposition of the foreground or layout. */
@Composable
internal fun rememberSoftMeshTime(paused: () -> Boolean = LocalSoftMeshPaused.current): State<Float> {
    LocalSoftMeshTime.current?.let { return it }
    val elapsed = remember { mutableFloatStateOf(0f) }
    if (LocalInspectionMode.current) return elapsed
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var focused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    var shown by remember(view) { mutableStateOf(view.isShown && view.windowVisibility == View.VISIBLE) }
    var animations by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    val activeTime = remember { MeshActiveTime() }
    val pause by rememberUpdatedState(paused)

    DisposableEffect(lifecycle, view) {
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            shown = view.isShown && view.windowVisibility == View.VISIBLE
        }
        val focusObserver = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            focused = hasFocus
            shown = view.isShown && view.windowVisibility == View.VISIBLE
        }
        val layoutObserver = ViewTreeObserver.OnGlobalLayoutListener {
            shown = view.isShown && view.windowVisibility == View.VISIBLE
        }
        // A newly visible window necessarily draws, but need not refocus or relayout.
        // Sample on that draw (API 26+), without an inactive polling timer or requesting a draw.
        val visibilityObserver = ViewTreeObserver.OnPreDrawListener {
            focused = view.hasWindowFocus()
            shown = view.isShown && view.windowVisibility == View.VISIBLE
            true
        }
        val attachObserver = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                focused = v.hasWindowFocus()
                shown = v.isShown && v.windowVisibility == View.VISIBLE
            }
            override fun onViewDetachedFromWindow(v: View) { shown = false }
        }
        val scaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { animations = ValueAnimator.areAnimatorsEnabled() }
        }
        lifecycle.addObserver(lifecycleObserver)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusObserver)
        view.viewTreeObserver.addOnGlobalLayoutListener(layoutObserver)
        view.viewTreeObserver.addOnPreDrawListener(visibilityObserver)
        view.addOnAttachStateChangeListener(attachObserver)
        view.context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, scaleObserver
        )
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focusObserver)
                view.viewTreeObserver.removeOnGlobalLayoutListener(layoutObserver)
                view.viewTreeObserver.removeOnPreDrawListener(visibilityObserver)
            }
            view.removeOnAttachStateChangeListener(attachObserver)
            view.context.contentResolver.unregisterContentObserver(scaleObserver)
        }
    }
    LaunchedEffect(lifecycle, view) {
        snapshotFlow { meshMayAnimate(resumed, focused, shown, animations) && !pause() }.collectLatest { active ->
            activeTime.pause()
            if (active) {
                while (true) {
                    delay(MESH_UPDATE_DELAY_MS)
                    withInfiniteAnimationFrameNanos { nanos ->
                        elapsed.floatValue = activeTime.frame(nanos)
                    }
                }
            }
        }
    }
    return elapsed
}
