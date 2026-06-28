package com.flare.im.app.features.messaging.messagerow

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.core.platform.FlareAssetImage

/** 表情消息：表情包资源大图，缺失则回退预览文本。 */
@Composable
internal fun EmojiMessageView(message: AppMessage, textColor: Color) {
    val asset = emojiAsset(message.core.content)
    if (asset != null) {
        FlareAssetImage(asset, "emoji", 78.dp) { Fallback(message, textColor) }
    } else {
        Fallback(message, textColor)
    }
}
