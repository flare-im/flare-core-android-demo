package com.flare.im.app.features.messaging.messagerow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.features.messaging.MessagingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 视频消息：MediaMetadataRetriever 取首帧做缩略图（本地/远端），点击内联播放。 */
@Composable
internal fun VideoMessageView(message: AppMessage, vm: MessagingViewModel, outgoing: Boolean) {
    val context = LocalContext.current
    val content = message.core.content
    var url by remember(message.appStableId) { mutableStateOf(imagePath(content)) }
    var thumb by remember(message.appStableId) { mutableStateOf<ImageBitmap?>(null) }
    var showPlayer by remember(message.appStableId) { mutableStateOf(false) }
    LaunchedEffect(message.appStableId) {
        vm.resolveMediaUrl(message)?.let { url = it }
        url?.let { p ->
            thumb = withContext(Dispatchers.IO) {
                runCatching {
                    val r = android.media.MediaMetadataRetriever()
                    when {
                        p.startsWith("content://") || p.startsWith("file://") -> r.setDataSource(context, android.net.Uri.parse(p))
                        p.startsWith("http") -> r.setDataSource(p, HashMap<String, String>())
                        else -> r.setDataSource(p)
                    }
                    val f = r.frameAtTime
                    r.release()
                    f?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(
        Modifier.sizeIn(maxWidth = 220.dp, maxHeight = 280.dp).clip(FlareTheme.tokens.radiusLarge)
            .background(FlareTheme.colors.surfaceAlt)
            .clickable { if (url != null) showPlayer = true },
        Alignment.Center,
    ) {
        val t = thumb
        if (t != null) {
            Image(bitmap = t, contentDescription = "video", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
        } else {
            CardRow("🎬", content?.str("description", "title") ?: "Video", outgoing)
        }
        androidx.compose.material3.Text("▶", style = FlareTheme.type.largeTitle, color = Color.White)
    }
    if (showPlayer) url?.let { VideoPlayerDialog(it) { showPlayer = false } }
}

/** 内联视频播放器：全屏 VideoView + MediaController（播放/暂停/拖动），点空白关闭。 */
@Composable
private fun VideoPlayerDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() }, Alignment.Center) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        val mc = android.widget.MediaController(ctx)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        if (url.startsWith("/")) setVideoPath(url) else setVideoURI(android.net.Uri.parse(url))
                        setOnPreparedListener { it.start() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
