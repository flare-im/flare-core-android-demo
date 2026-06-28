package com.flare.im.app.features.messaging.messagerow

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.core.platform.FlareLocalImage
import com.flare.im.app.core.designsystem.FlareTheme

/** 卡片型内容统一行：图标 + 标题（file/location/card/link/...）。 */
@Composable
internal fun CardRow(icon: String, label: String, outgoing: Boolean) {
    val colors = FlareTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text(icon, style = FlareTheme.type.title)
        Spacer(Modifier.width(FlareTheme.tokens.sm))
        androidx.compose.material3.Text(
            label,
            style = FlareTheme.type.body,
            color = if (outgoing) colors.outgoingText else colors.textPrimary,
        )
    }
}

/** 兜底：未知/未渲染内容显示预览文本。 */
@Composable
internal fun Fallback(message: AppMessage, color: Color) {
    androidx.compose.material3.Text(message.previewText, style = FlareTheme.type.body, color = color)
}

/** 媒体全屏预览：双指缩放 + 拖动平移 + 双击放大复位；单击关闭。 */
@Composable
internal fun MediaPreviewDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero },
                        onTap = { onDismiss() },
                    )
                },
            Alignment.Center,
        ) {
            FlareLocalImage(
                path = path,
                contentDescription = "Preview",
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                },
                maxPx = 2048,
                contentScale = ContentScale.Fit,
            ) {
                androidx.compose.material3.Text(
                    "Cannot preview",
                    style = FlareTheme.type.body,
                    color = Color.White,
                    modifier = Modifier.padding(FlareTheme.tokens.lg),
                )
            }
        }
    }
}
