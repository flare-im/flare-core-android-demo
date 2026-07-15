package com.flare.im.app.features.messaging.messagerow

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.ui.FlareEmojiPackMessage

/** 表情消息：委托 kit 的表情包组件（资源来自 flare-im-design 中心源，随 im-ui-compose 打包）。 */
@Composable
internal fun EmojiMessageView(message: AppMessage, textColor: Color) {
    FlareEmojiPackMessage(emoji = message.core.content?.str("emoji", "key") ?: message.previewText)
}
