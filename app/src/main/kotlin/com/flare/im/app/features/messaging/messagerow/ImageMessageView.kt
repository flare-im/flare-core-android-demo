package com.flare.im.app.features.messaging.messagerow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.features.messaging.MessagingViewModel
import com.flare.im.ui.ImageMessage

/** 图片消息（IMAGE / IMAGE_GROUP）：本地直链即时显示 → [MessagingViewModel.resolveMediaUrl]
 *  换远端签名 URL，交给 flare-im-design 的 [ImageMessage] 渲染（弹性尺寸 + Coil）；点击全屏预览。
 *  URL 解析仍归 app（SDK 侧），组件只吃已解析的 src。 */
@Composable
internal fun ImageMessageView(message: AppMessage, vm: MessagingViewModel, outgoing: Boolean, onPreview: (String) -> Unit) {
    val content = message.core.content
    var path by remember(message.appStableId) { mutableStateOf(imagePath(content)) }
    LaunchedEffect(message.appStableId) { vm.resolveMediaUrl(message)?.let { path = it } }
    ImageMessage(
        src = path,
        maxWidth = 220,
        maxHeight = 280,
        alt = content?.str("description", "title") ?: "Image",
        onTap = { path?.let(onPreview) },
    )
}
