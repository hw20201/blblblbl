package com.github.arca.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.arca.android.ui.glass.GlassRadius
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.glass.LocalArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.LocalShortcutBlurEnabled
import com.github.arca.android.ui.glass.NavigationLensState
import io.github.neoruaa.glasmio.glass.progressiveBlurBackdrop
import kotlin.math.roundToInt

internal enum class ShortcutMorphMotionProfile(
    val sourceFaceFadeEnd: Float,
    val panelContentFadeStart: Float
) {
    /** Dashboard content handoff; timing is shared by every profile. */
    STANDARD(0.52f, 0.30f),
    /** A menu-like handoff: the source face has a broad, late cross-fade as the panel closes without spring overshoot. */
    QUVEN_MENU(0.60f, 0.30f)
}

/** Geometry interpolation kept separate so its endpoint behavior can be unit-tested. */
internal fun interpolateShortcutMorphRect(start: Rect, end: Rect, progress: Float): Rect {
    val fraction = progress.coerceIn(0f, 1f)
    fun mix(from: Float, to: Float) = from + (to - from) * fraction
    return Rect(mix(start.left, end.left), mix(start.top, end.top), mix(start.right, end.right), mix(start.bottom, end.bottom))
}

/**
 * Menu-style morph geometry with two independent progresses, after a liquid-glass drop: [spread] carries
 * the sides across, [reach] carries the far edge along the way the panel opens, and the near edge leaves
 * the control only as the spread finishes. Both may swing slightly past 1 on their springs.
 */
internal fun interpolateShortcutMorphRectDual(start: Rect, end: Rect, spreadRaw: Float, reachRaw: Float): Rect {
    // The row first lifts at its own size and place; growth then runs over what is left of the progress.
    fun grow(p: Float) = ((p - MENU_LIFT_END) / (1f - MENU_LIFT_END)).coerceAtLeast(0f)
    val spread = grow(spreadRaw)
    val reach = grow(reachRaw)
    fun mix(from: Float, to: Float, f: Float) = from + (to - from) * f
    // Size and position are separate: the slab keeps the row's top edge while it lengthens, and if the finished
    // panel must stand higher, the whole slab slides there; it never flips into growing upward from the row.
    val width = mix(start.width, end.width, spread)
    val height = mix(start.height, end.height, reach)
    val slide = smoothMorphWindow(0.25f, 0.95f, reach)
    val left = mix(start.left, end.left, spread)
    val top = mix(start.top, end.top, slide)
    return Rect(left, top, left + width.coerceAtLeast(0f), top + height.coerceAtLeast(0f))
}

/** Share of the opening spent only lifting the row out of its group, before it starts to grow. */
internal const val MENU_LIFT_END = 0.16f

/** 0 to 1 over the lift phase: the row rises out of its group at its own size. */
internal fun menuLiftAmount(spread: Float): Float = smoothMorphWindow(0f, MENU_LIFT_END, spread)

/** The four corner radii of a source row, in dp. */
internal data class ShortcutMorphCorners(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp)

/**
 * Corner radius of one corner while a row grows into a panel: it leaves the row's own radius and reaches the
 * panel's, with no detour through a capsule, so the glass is recognisably the row for as long as it is small.
 */
internal fun menuMorphRadiusPx(sourcePx: Float, targetPx: Float, minDimensionPx: Float, spread: Float): Float {
    val target = targetPx.coerceAtMost(minDimensionPx / 2f)
    // Nothing happens to the corners while the row is only being lifted; then they travel straight to the panel's.
    val t = smoothMorphWindow(MENU_LIFT_END, 1f, spread)
    return sourcePx + (target - sourcePx) * t
}

internal fun shortcutMorphContentAlphas(
    progress: Float,
    sourceFaceFadeEnd: Float = ShortcutMorphMotionProfile.STANDARD.sourceFaceFadeEnd,
    panelContentFadeStart: Float = ShortcutMorphMotionProfile.STANDARD.panelContentFadeStart
): Pair<Float, Float> {
    val fraction = progress.coerceIn(0f, 1f)
    val faceEnd = sourceFaceFadeEnd.coerceIn(0.001f, 1f)
    val contentStart = panelContentFadeStart.coerceIn(0f, 0.999f)
    val sourceFace = (1f - fraction / faceEnd).coerceIn(0f, 1f)
    val panelContent = ((fraction - contentStart) / (1f - contentStart)).coerceIn(0f, 1f)
    return sourceFace to panelContent
}

internal val SETTINGS_MORPH_TOP_BACKDROP_BLUR_HEIGHT = 64.dp
private val SETTINGS_MORPH_TOP_BACKDROP_BLUR_RADIUS = 16.dp
private const val SETTINGS_MORPH_TOP_BACKDROP_BLUR_ALPHA = 0.96f
private const val SETTINGS_MORPH_TOP_BACKDROP_FADE_START = 0.68f
private val SETTINGS_MORPH_BOTTOM_FADE_HEIGHT = 10.dp
private val SETTINGS_MORPH_BOTTOM_RIM_CLEARANCE = 2.dp
private const val SETTINGS_MORPH_BOTTOM_MAX_FADE_ALPHA = 0.045f

internal fun settingsMorphBottomFadeAlpha(progress: Float, closing: Boolean): Float {
    if (!closing) return 0f
    return (1f - progress.coerceIn(0f, 1f)).coerceIn(0f, 1f) * SETTINGS_MORPH_BOTTOM_MAX_FADE_ALPHA
}

internal fun settingsMorphBottomFadeRect(
    widthPx: Float,
    heightPx: Float,
    fadeHeightPx: Float,
    rimClearancePx: Float
): Rect {
    val safeHeight = heightPx.coerceAtLeast(0f)
    val lower = (safeHeight - rimClearancePx.coerceAtLeast(0f)).coerceIn(0f, safeHeight)
    val upper = (lower - fadeHeightPx.coerceAtLeast(0f)).coerceAtLeast(0f)
    return Rect(0f, upper, widthPx.coerceAtLeast(0f), lower)
}

internal fun Modifier.settingsMorphTopBackdropBlur(backdrop: io.github.neoruaa.glasmio.glass.render.Backdrop): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fade = Brush.verticalGradient(
                0f to Color.White,
                SETTINGS_MORPH_TOP_BACKDROP_FADE_START to Color.White,
                1f to Color.Transparent,
                startY = 0f,
                endY = size.height.coerceAtLeast(1f)
            )
            onDrawWithContent {
                drawContent()
                // Fade the sampled blur itself into the main Glasmio material; a
                // zero-radius blur would otherwise paint a sharp, separate strip.
                drawRect(fade, blendMode = BlendMode.DstIn)
            }
        }
        .progressiveBlurBackdrop(
            backdrop = backdrop,
            progress = SETTINGS_MORPH_TOP_BACKDROP_BLUR_ALPHA,
            maxRadius = SETTINGS_MORPH_TOP_BACKDROP_BLUR_RADIUS
        )

private fun Modifier.settingsMorphBottomCloseFade(alpha: Float): Modifier {
    val fadeAlpha = alpha.coerceIn(0f, SETTINGS_MORPH_BOTTOM_MAX_FADE_ALPHA)
    if (fadeAlpha <= 0f) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fadeRect = settingsMorphBottomFadeRect(
                widthPx = size.width,
                heightPx = size.height,
                fadeHeightPx = SETTINGS_MORPH_BOTTOM_FADE_HEIGHT.toPx(),
                rimClearancePx = SETTINGS_MORPH_BOTTOM_RIM_CLEARANCE.toPx()
            )
            val mask = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = fadeAlpha),
                startY = fadeRect.top,
                endY = fadeRect.bottom.coerceAtLeast(fadeRect.top + 1f)
            )
            onDrawWithContent {
                drawContent()
                if (fadeRect.width > 0f && fadeRect.height > 0f) {
                    drawRect(
                        brush = mask,
                        topLeft = Offset(fadeRect.left, fadeRect.top),
                        size = Size(fadeRect.width, fadeRect.height),
                        blendMode = BlendMode.DstOut
                    )
                }
            }
        }
}

/**
 * A native Compose shortcut-to-floating-panel morph. The source corner stays near
 * the same screen edge; the glass grows in place and its content cross-fades.
 * The existing scene lens is sampled by GlassSurface; this does not capture the
 * whole screen per animation frame. No physical haptics are added here.
 */
internal fun shortcutMorphTargetRect(
    source: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    requestedWidthPx: Float,
    requestedHeightPx: Float,
    topInsetPx: Float,
    bottomInsetPx: Float,
    sideMarginPx: Float
): Rect {
    val safeHeight = (rootHeightPx - topInsetPx - bottomInsetPx).coerceAtLeast(0f)
    val targetHeight = requestedHeightPx.coerceIn(0f, safeHeight)
    val targetWidth = requestedWidthPx.coerceIn(0f, (rootWidthPx - 2f * sideMarginPx).coerceAtLeast(0f))
    val leftAnchor = (source.left + source.right) * 0.5f <= rootWidthPx * 0.5f
    val targetLeft = if (leftAnchor) sideMarginPx else
        (rootWidthPx - targetWidth - sideMarginPx).coerceAtLeast(0f)
    val maxTop = (rootHeightPx - targetHeight - bottomInsetPx).coerceAtLeast(topInsetPx)
    val targetTop = source.top.coerceIn(topInsetPx, maxTop)
    return Rect(targetLeft, targetTop, targetLeft + targetWidth, targetTop + targetHeight)
}

@Composable
internal fun ShortcutMorphOverlay(
    visible: Boolean,
    sourceBounds: Rect?,
    liveSourceBounds: (() -> Rect?)? = null,
    sourceTitle: String,
    sourceSubtitle: String,
    sourceIcon: @Composable () -> Unit,
    lens: NavigationLensState?,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    panelTestTag: String = "quick-node-selector-sheet",
    contentTestTag: String = "quick-node-selector-content",
    panelHeightFraction: Float = 0.72f,
    panelHeight: Dp? = null,
    sideMargin: Dp = 16.dp,
    maxPanelWidth: Dp = 420.dp,
    reserveDockSpace: Boolean = true,
    motionProfile: ShortcutMorphMotionProfile = ShortcutMorphMotionProfile.STANDARD,
    /** Settings uses a direct four-edge morph so its top and bottom never take separate paths. */
    linearEdgeMotion: Boolean = false,
    settingsTopBackdropBlur: Boolean = false,
    settingsBottomCloseFade: Boolean = false,
    sourceCornerRadius: Dp? = null,
    /** Corner radii of the row this panel grows from (top-start, top-end, bottom-end, bottom-start). */
    sourceCorners: ShortcutMorphCorners? = null,
    panelSurfaceCategory: GlassSurfaceCategory = GlassSurfaceCategory.SHORTCUT_CARDS,
    sourceContent: (@Composable () -> Unit)? = null,
    /**
     * Drawn above the body in the panel's own layer: never scrolled, faded, blurred or scaled with
     * the body. Appears only once the panel has fully opened and is removed the moment it starts closing.
     */
    pinnedOverlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val progress = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val animationsEnabled = LocalShortcutMotionEnabled.current ?: true
    val latestSpeed by rememberUpdatedState(LocalPanelAnimationSpeed.current)
    val latestOnDismissed by rememberUpdatedState(onDismissed)

    // Changing a preference does not restart an in-flight animation. The next
    // open/close transition samples it, including closing the speed chooser itself.
    LaunchedEffect(visible, animationsEnabled) {
        if (visible) {
            val newlyMounted = !mounted
            closing = false
            mounted = true
            if (animationsEnabled) {
                if (newlyMounted) {
                    // Prepare either panel body at its final size before motion begins.
                    withFrameNanos { }
                    withFrameNanos { }
                }
                progress.animateTo(1f, panelMorphAnimationSpec(latestSpeed, opening = true, from = progress.value, linearEdgeMotion = linearEdgeMotion))
            } else {
                progress.snapTo(1f)
            }
        } else if (mounted) {
            closing = true
            if (animationsEnabled) {
                progress.animateTo(0f, panelMorphAnimationSpec(latestSpeed, opening = false, from = progress.value, linearEdgeMotion = linearEdgeMotion))
            } else {
                progress.snapTo(0f)
            }
            mounted = false
            closing = false
            latestOnDismissed()
        }
    }
    BackHandler(enabled = visible, onBack = onDismissRequest)

    if (visible || mounted) {
        BoxWithConstraints(Modifier.fillMaxSize().zIndex(100f)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val rootWidthPx = with(density) { maxWidth.toPx() }
            val rootHeightPx = with(density) { maxHeight.toPx() }
            val fallbackAnchor = Rect(
                rootWidthPx * 0.5f - with(density) { 24.dp.toPx() },
                rootHeightPx * 0.5f - with(density) { 24.dp.toPx() },
                rootWidthPx * 0.5f + with(density) { 24.dp.toPx() },
                rootHeightPx * 0.5f + with(density) { 24.dp.toPx() }
            )
            // Resolve the morph anchor live every frame instead of trusting the
            // open-time snapshot: the dashboard can scroll while the panel is open
            // (the bounds map keeps updating), so a stale start rect lands the
            // panel where the card used to be and the card visibly jumps when the
            // overlay hands back. Same class of bug as the SSH client morph landing.
            val start = liveSourceBounds?.invoke() ?: sourceBounds ?: fallbackAnchor
            val targetWidth = (maxWidth - sideMargin * 2).coerceAtLeast(0.dp).coerceAtMost(maxPanelWidth)
            val targetWidthPx = with(density) { targetWidth.toPx() }
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
            val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomInset = (if (reserveDockSpace) mainDockContentInset(navigationInset) else navigationInset) + 8.dp
            val topInsetPx = with(density) { topInset.toPx() }
            val bottomInsetPx = with(density) { bottomInset.toPx() }
            val maxPanelHeightPx = (rootHeightPx - topInsetPx - bottomInsetPx).coerceAtLeast(0f)
            val targetHeightPx = minOf(
                panelHeight?.let { with(density) { it.toPx() } }
                    ?: with(density) { maxHeight.toPx() * panelHeightFraction.coerceIn(0.1f, 1f) },
                maxPanelHeightPx
            )
            val targetHeight = with(density) { targetHeightPx.toDp() }
            val target = shortcutMorphTargetRect(
                source = start,
                rootWidthPx = rootWidthPx,
                rootHeightPx = rootHeightPx,
                requestedWidthPx = targetWidthPx,
                requestedHeightPx = targetHeightPx,
                topInsetPx = topInsetPx,
                bottomInsetPx = bottomInsetPx,
                sideMarginPx = with(density) { sideMargin.toPx() }
            )
            val fraction = progress.value.coerceIn(0f, 1f)
            val sharedGlasmioBackdrop = LocalArcaGlasmioBackdrop.current
            val bottomFadeAlpha = if (settingsBottomCloseFade) settingsMorphBottomFadeAlpha(fraction, closing) else 0f
            val menuProfile = motionProfile == ShortcutMorphMotionProfile.QUVEN_MENU
            // Settings edges travel directly between their endpoints. Separately easing the
            // top while changing height can send the bottom past both endpoints on close.
            // Use this same mapping in both directions, including interrupted reversals.
            val interpolated = when {
                linearEdgeMotion -> interpolateShortcutMorphRect(start, target, fraction)
                menuProfile -> interpolateShortcutMorphRectDual(start, target, fraction, fraction)
                else -> interpolateShortcutMorphRect(start, target, panelMorphGrowth(fraction))
            }
            // Round the actual edges, not top and height independently: a short bottom
            // path must not wobble by one pixel when two separate roundings disagree.
            val current = if (linearEdgeMotion) Rect(
                interpolated.left.roundToInt().toFloat(), interpolated.top.roundToInt().toFloat(),
                interpolated.right.roundToInt().toFloat(), interpolated.bottom.roundToInt().toFloat()
            ) else interpolated
            val currentWidth = with(density) { current.width.toDp() }.coerceAtLeast(1.dp)
            val currentHeight = with(density) { current.height.toDp() }.coerceAtLeast(1.dp)
            val cornerShape: RoundedCornerShape
            val currentRadius: Dp
            if (menuProfile) {
                val sourceRadius = sourceCornerRadius ?: with(density) { minOf(start.width, start.height).toDp() / 2f }
                val src = sourceCorners ?: ShortcutMorphCorners(sourceRadius, sourceRadius, sourceRadius, sourceRadius)
                fun corner(from: Dp): Dp = with(density) {
                    menuMorphRadiusPx(
                        sourcePx = from.toPx(),
                        targetPx = 28.dp.toPx(),
                        minDimensionPx = minOf(current.width, current.height),
                        spread = fraction
                    ).toDp()
                }
                val ts = corner(src.topStart); val te = corner(src.topEnd)
                cornerShape = RoundedCornerShape(topStart = ts, topEnd = te, bottomEnd = corner(src.bottomEnd), bottomStart = corner(src.bottomStart))
                currentRadius = maxOf(ts, te)
            } else {
                currentRadius = GlassRadius.card + (28.dp - GlassRadius.card) * fraction
                cornerShape = RoundedCornerShape(currentRadius)
            }
            // Opening retires the source row sooner so it does not ghost under the arriving panel
            // body; closing keeps the broad late cross-fade so the row is back before the panel lands.
            val faceFadeEnd = if (menuProfile && !closing && !linearEdgeMotion)
                0.48f else motionProfile.sourceFaceFadeEnd
            val (collapsedAlpha, expandedAlpha) = shortcutMorphContentAlphas(
                fraction,
                sourceFaceFadeEnd = faceFadeEnd,
                panelContentFadeStart = motionProfile.panelContentFadeStart
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.045f * fraction))
                    .clickable(interactionSource = null, indication = null, onClick = onDismissRequest)
            )
            GlassSurface(
                modifier = Modifier
                    .offset { IntOffset(current.left.roundToInt(), current.top.roundToInt()) }
                    .size(currentWidth, currentHeight)
                    .then(if (menuProfile && !linearEdgeMotion) Modifier.graphicsLayer {
                        // Lifted out of its group: a small rise in scale and a deeper shadow, at the row's own size.
                        val lift = menuLiftAmount(fraction)
                        val settleOut = 1f - smoothMorphWindow(MENU_LIFT_END, MENU_LIFT_END + 0.2f, fraction)
                        val k = lift * settleOut
                        scaleX = 1f + 0.018f * k
                        scaleY = 1f + 0.018f * k
                        shadowElevation = 18.dp.toPx() * k
                        shape = cornerShape
                        clip = false
                    } else Modifier)
                    .clip(cornerShape)
                    .settingsMorphBottomCloseFade(bottomFadeAlpha)
                    .zIndex(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        }
                    }
                    .testTag(panelTestTag),
                shape = cornerShape,
                lens = lens,
                blurEnabled = LocalShortcutBlurEnabled.current,
                shortcutContactFrost = true,
                shortcutDockLens = true,
                honorShape = menuProfile,
                surfaceCategory = panelSurfaceCategory,
                surfaceAlphaScale = if (menuProfile) 0.72f + 0.28f * smoothMorphWindow(0.55f, 0.95f, fraction) else 1f
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.matchParentSize().testTag("$panelTestTag-glasmio-main-material"))
                    if (settingsTopBackdropBlur && sharedGlasmioBackdrop != null) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(SETTINGS_MORPH_TOP_BACKDROP_BLUR_HEIGHT)
                                .clip(RoundedCornerShape(topStart = currentRadius, topEnd = currentRadius))
                                .settingsMorphTopBackdropBlur(sharedGlasmioBackdrop)
                                .testTag("$panelTestTag-glasmio-top-backdrop-blur")
                        )
                    }
                    // During close, keep the source face mounted from the first frame so the
                    // row can fade back in while the panel is shrinking. Its alpha is still zero
                    // at the open endpoint, so this adds no flash.
                    if (closing || fraction < faceFadeEnd) {
                        Box(
                            Modifier.matchParentSize().graphicsLayer {
                                alpha = collapsedAlpha
                                val scale = if (motionProfile == ShortcutMorphMotionProfile.QUVEN_MENU) {
                                    0.98f + 0.02f * collapsedAlpha
                                } else 1f
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                            if (sourceContent != null) {
                                sourceContent()
                            } else {
                                ShortcutCardContent(
                                    title = sourceTitle,
                                    subtitle = sourceSubtitle,
                                    icon = sourceIcon,
                                    isEditing = false,
                                    onDelete = null
                                )
                            }
                        }
                    }
                    // Keep the body mounted from frame zero for both profiles.
                    run {
                        val menu = motionProfile == ShortcutMorphMotionProfile.QUVEN_MENU
                        // Content comes into focus as the glass spreads: fades in from 40%, from blurred to sharp.
                        val contentAlpha = if (menu)
                            ((fraction - 0.34f) / 0.66f).coerceIn(0f, 1f) else expandedAlpha
                        val blurPx = with(density) { 10.dp.toPx() }
                        val finalWidth = target.width.roundToInt()
                        val finalHeight = target.height.roundToInt()
                        Box(
                            Modifier.fillMaxSize().layout { measurable, constraints ->
                                // Lay the body out once at the final panel size: the growing panel
                                // only reveals it, so text never reflows and sliders never resize
                                // while the shape is still moving.
                                val placeable = measurable.measure(Constraints.fixed(finalWidth, finalHeight))
                                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                            }
                        ) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = contentAlpha
                                        if (menu) {
                                            val r = (1f - contentAlpha) * blurPx
                                            renderEffect = if (r > 0.5f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                                                androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal)
                                            else null
                                        } else {
                                            val contentScale = 0.96f + 0.04f * expandedAlpha
                                            scaleX = contentScale
                                            scaleY = contentScale
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                                    .testTag(contentTestTag)
                            ) {
                                content()
                            }
                        }
                    }
                    // Pinned overlay: only once the panel is fully open and settled.
                    val panelFullyOpen = visible && !closing && fraction >= 0.999f
                    if (pinnedOverlay != null && panelFullyOpen) {
                        Box(Modifier.matchParentSize().testTag("$panelTestTag-pinned-overlay")) {
                            pinnedOverlay()
                        }
                    }
                }
            }
        }
    }
}

private fun smoothMorphWindow(start: Float, end: Float, value: Float): Float {
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
