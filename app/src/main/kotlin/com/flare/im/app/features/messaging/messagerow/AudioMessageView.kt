package com.flare.im.app.features.messaging.messagerow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.features.messaging.MessagingViewModel

/** 语音气泡：▶/⏸ 播放（MediaPlayer.prepareAsync 不卡主线程）；远端音频经 resolveMediaUrl。 */
@Composable
internal fun AudioMessageView(message: AppMessage, vm: MessagingViewModel, outgoing: Boolean) {
    val colors = FlareTheme.colors
    val context = LocalContext.current
    var path by remember(message.appStableId) { mutableStateOf(imagePath(message.core.content)) }
    LaunchedEffect(message.appStableId) { vm.resolveMediaUrl(message)?.let { path = it } }
    var playing by remember { mutableStateOf(false) }
    val player = remember { android.media.MediaPlayer() }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (playing) "⏸" else "▶",
            style = FlareTheme.type.title,
            color = if (outgoing) colors.outgoingText else colors.brand,
            modifier = Modifier.clickable {
                val p = path ?: return@clickable
                runCatching {
                    if (playing) {
                        player.pause(); playing = false
                    } else {
                        player.reset()
                        if (p.startsWith("content://") || p.startsWith("file://")) player.setDataSource(context, android.net.Uri.parse(p)) else player.setDataSource(p)
                        player.setOnPreparedListener { it.start(); playing = true }
                        player.setOnCompletionListener { playing = false }
                        player.prepareAsync()
                    }
                }
            },
        )
        Spacer(Modifier.width(FlareTheme.tokens.sm))
        Text("Voice message", style = FlareTheme.type.body, color = if (outgoing) colors.outgoingText else colors.textPrimary)
    }
}
