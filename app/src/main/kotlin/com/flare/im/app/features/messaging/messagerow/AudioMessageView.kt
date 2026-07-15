package com.flare.im.app.features.messaging.messagerow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.features.messaging.MessagingViewModel
import com.flare.im.ui.VoiceMessage

/** 语音消息：UI 用 flare-im-design 的 [VoiceMessage]（波形 + ▶/⏸ + 时长），
 *  播放仍归 app（MediaPlayer.prepareAsync 不卡主线程）——组件吃 playing/onPlay。 */
@Composable
internal fun AudioMessageView(message: AppMessage, vm: MessagingViewModel, outgoing: Boolean) {
    val context = LocalContext.current
    var path by remember(message.appStableId) { mutableStateOf(imagePath(message.core.content)) }
    LaunchedEffect(message.appStableId) { vm.resolveMediaUrl(message)?.let { path = it } }
    var playing by remember { mutableStateOf(false) }
    val player = remember { android.media.MediaPlayer() }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }
    val seconds = message.core.content?.str("duration", "seconds")?.toDoubleOrNull()?.toInt() ?: 1

    VoiceMessage(
        seconds = seconds,
        playing = playing,
        onPlay = {
            val p = path ?: return@VoiceMessage
            runCatching {
                if (playing) {
                    player.pause(); playing = false
                } else {
                    player.reset()
                    if (p.startsWith("content://") || p.startsWith("file://")) {
                        player.setDataSource(context, android.net.Uri.parse(p))
                    } else {
                        player.setDataSource(p)
                    }
                    player.setOnPreparedListener { it.start(); playing = true }
                    player.setOnCompletionListener { playing = false }
                    player.prepareAsync()
                }
            }
        },
    )
}
