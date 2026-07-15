package com.flare.im.app.features.messaging.messagerow

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.features.messaging.media.EmojiPresentation
import com.flare.im.ui.FlareEmojiPackMessage

/** 文本 / 富文本消息：
 *  - 有 docJson → RichDoc v2 带格式渲染（heading/列表/bold…，见 RichDocRenderer）。
 *  - 整段表情包 key → kit 表情包大图；单 emoji → 大字号；否则普通文本。 */
@Composable
internal fun TextMessageView(message: AppMessage, textColor: Color, outgoing: Boolean) {
    val content = message.core.content
    val docJson = content?.str("docJson")
    if (docJson != null) {
        RichDocText(docJson, message.previewText, textColor)
        return
    }
    val raw = content?.str("text", "plainText") ?: message.previewText
    val lone = EmojiPresentation.lonePackKey(raw)
    val single = EmojiPresentation.singleEmoji(raw)
    when {
        lone != null -> FlareEmojiPackMessage(emoji = lone)
        single != null -> Text(
            single,
            style = FlareTheme.type.largeTitle.copy(fontSize = TextUnit.Unspecified),
            color = textColor,
        )
        else -> com.flare.im.ui.TextMessage(text = message.previewText, self = outgoing)
    }
}
