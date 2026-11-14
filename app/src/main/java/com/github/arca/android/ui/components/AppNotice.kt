package com.github.arca.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.LightGlassSurface
import com.github.arca.android.ui.theme.AppPalette
import com.github.arca.android.ui.theme.LoonCard
import com.github.arca.android.ui.theme.LoonTextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NoticeKind { Success, Error, Info }

data class Notice(
    val message: String,
    val kind: NoticeKind,
    val long: Boolean,
    val id: Long = System.nanoTime()
)

/**
 * Process-wide notice channel replacing Toast. Callable from any thread (Activity callbacks,
 * IO coroutines, composables); [AppNoticeHost] renders the latest notice as a glass capsule.
 */
object AppNotice {
    private val mutable = MutableStateFlow<Notice?>(null)
    val current = mutable.asStateFlow()

    private val errorHints = listOf("失败", "错误", "无法", "无效", "需要授予", "未能", "error", "failed")

    fun show(message: String, long: Boolean = false, kind: NoticeKind? = null) {
        val resolved = kind ?: if (errorHints.any { message.contains(it, ignoreCase = true) }) NoticeKind.Error else NoticeKind.Success
        // Errors stay longer by default so the reason can be read.
        mutable.value = Notice(message, resolved, long || resolved == NoticeKind.Error)
    }

    fun success(message: String) = show(message, kind = NoticeKind.Success)
    fun error(message: String) = show(message, long = true, kind = NoticeKind.Error)
    fun info(message: String) = show(message, kind = NoticeKind.Info)

    fun dismiss(id: Long) {
        if (mutable.value?.id == id) mutable.value = null
    }
}

/** Place once at the root, above all content. Tapping the capsule dismisses it. */
@Composable
fun BoxScope.AppNoticeHost() {
    val notice by AppNotice.current.collectAsState()
    LaunchedEffect(notice?.id) {
        val n = notice ?: return@LaunchedEffect
        delay(if (n.long) 4200L else 2200L)
        AppNotice.dismiss(n.id)
    }
    // Keep the last notice composed during the exit animation.
    val last = remember { arrayOfNulls<Notice>(1) }
    if (notice != null) last[0] = notice
    val shown = notice ?: last[0]
    AnimatedVisibility(
        visible = notice != null,
        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(1000f)
            .statusBarsPadding()
            .padding(top = 8.dp, start = 20.dp, end = 20.dp)
    ) {
        if (shown != null) NoticeCapsule(shown)
    }
}

@Composable
private fun NoticeCapsule(notice: Notice) {
    val shape = RoundedCornerShape(22.dp)
    val (icon, tint) = when (notice.kind) {
        NoticeKind.Success -> Icons.Default.CheckCircle to AppPalette.success
        NoticeKind.Error -> Icons.Default.ErrorOutline to AppPalette.danger
        NoticeKind.Info -> Icons.Default.Info to AppPalette.info
    }
    val body: @Composable BoxScope.() -> Unit = {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                notice.message,
                color = LoonTextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (notice.long) 4 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    val modifier = Modifier
        .widthIn(max = 520.dp)
        .testTag("app-notice")
        .semantics { liveRegion = LiveRegionMode.Polite }
        .clickable(remember { MutableInteractionSource() }, indication = null) { AppNotice.dismiss(notice.id) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        if (GlassDefaults.enabled) {
            LightGlassSurface(modifier = modifier, shape = shape, accent = tint, content = body)
        } else {
            Surface(modifier = modifier, shape = shape, color = LoonCard, shadowElevation = 6.dp) { Box(content = body) }
        }
    }
}
