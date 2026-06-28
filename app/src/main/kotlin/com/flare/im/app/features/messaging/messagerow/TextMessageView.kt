package com.flare.im.app.features.messaging.messagerow

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.features.messaging.media.EmojiPresentation

/** 文本 / 富文本消息：
 *  - 有 docJson → RichDoc v2 带格式渲染（heading/列表/bold…，见 RichDocRenderer）。
 *  - 整段表情包 key → 大图；单 emoji → 大字号；否则普通文本。 */
@Composable
internal fun TextMessageView(message: AppMessage, textColor: Color) {
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
        lone != null -> FlareAssetEmoji(lone, message, textColor)
        single != null -> Text(
            single,
            style = FlareTheme.type.largeTitle.copy(fontSize = TextUnit.Unspecified),
            color = textColor,
        )
        else -> Text(message.previewText, style = FlareTheme.type.body, color = textColor)
    }
}

@Composable
private fun FlareAssetEmoji(loneKey: String, message: AppMessage, textColor: Color) {
    com.flare.im.app.core.platform.FlareAssetImage(
        EmojiPresentation.emojiAssetPath(loneKey),
        "emoji",
        78.dp,
    ) { Text(message.previewText, style = FlareTheme.type.body, color = textColor) }
}
