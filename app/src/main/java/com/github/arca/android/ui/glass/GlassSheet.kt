package com.github.arca.android.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Bottom sheet with the self-developed glass container: when glass is enabled
 * the sheet paints a GlassSurface instead of a solid container color, so sheets
 * match the glass pages. Otherwise it behaves exactly like ModalBottomSheet.
 * All sheet content roots are plain Columns, so wrapping them in one more
 * Column is layout-safe. [heightFraction] sizes the handle and content together
 * inside the sheet's anchor coordinate space, before applying [contentWindowInsets].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    heightFraction: Float? = null,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit
) {
    require(heightFraction == null || heightFraction > 0f && heightFraction <= 1f)
    val glass = GlassDefaults.enabled
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = if (glass) Color.Transparent else containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        // With glass the handle is drawn inside the glass surface; otherwise it would float
        // above the sheet on the bare scrim while the glass starts below it.
        dragHandle = if (glass || heightFraction != null) null else dragHandle,
        // Fractional sheets size the entire shell before its insets. Otherwise M3's
        // offset-dependent top inset changes the height and moves the expanded anchor.
        contentWindowInsets = { if (heightFraction == null) contentWindowInsets() else WindowInsets(0) }
    ) {
        val shellModifier = Modifier.fillMaxWidth().then(
            if (heightFraction == null) Modifier else Modifier.fillMaxHeight(heightFraction)
        )
        val shellContent: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().then(
                if (heightFraction == null) Modifier else Modifier.windowInsetsPadding(contentWindowInsets())
            )) {
                dragHandle?.let { handle ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { handle() }
                }
                content()
            }
        }
        if (glass) {
            GlassSurface(modifier = shellModifier, shape = shape) { shellContent() }
        } else if (heightFraction != null) {
            Box(shellModifier) { shellContent() }
        } else {
            content()
        }
    }
}

/**
 * Alert dialog with the self-developed glass container, mirroring AlertDialog's
 * slots. When glass is disabled it is exactly AlertDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation
) {
    if (GlassDefaults.enabled) {
        Dialog(onDismissRequest = onDismissRequest) {
            GlassSurface(modifier = modifier, shape = shape) {
                Column(Modifier.padding(24.dp)) {
                    icon?.let {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { it() }
                        if (title != null || text != null) Spacer(Modifier.height(16.dp))
                    }
                    title?.let {
                        it()
                        if (text != null) Spacer(Modifier.height(16.dp))
                    }
                    text?.let { it() }
                    if (title != null || text != null) Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation
        )
    }
}
