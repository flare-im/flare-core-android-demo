package com.flare.im.app.features.messaging.messagerow

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.features.messaging.MessagingViewModel
import com.flare.im.model.common.enums.MessageContentType

/** 内容分发：按 contentType 路由到对应消息类型组件（每类型独立文件）。
 *  撤回优先短路；未知类型走 [Fallback]。 */
@Composable
internal fun MessageContentView(message: AppMessage, outgoing: Boolean, vm: MessagingViewModel, onPreview: (String) -> Unit) {
    val colors = FlareTheme.colors
    val textColor = if (outgoing) colors.outgoingText else colors.textPrimary

    if (message.core.isRecalled) {
        Text("Message recalled", style = FlareTheme.type.body, color = colors.textTertiary)
        return
    }
    when (message.core.content?.contentType ?: MessageContentType.TEXT) {
        MessageContentType.EMOJI -> EmojiMessageView(message, textColor)
        MessageContentType.STICKER -> StickerMessageView(message, textColor)
        MessageContentType.TEXT, MessageContentType.RICH_TEXT -> TextMessageView(message, textColor)
        MessageContentType.IMAGE, MessageContentType.IMAGE_GROUP -> ImageMessageView(message, vm, outgoing, onPreview)
        MessageContentType.VIDEO -> VideoMessageView(message, vm, outgoing)
        MessageContentType.AUDIO -> AudioMessageView(message, vm, outgoing)
        MessageContentType.FILE -> FileMessageView(message, outgoing)
        MessageContentType.LOCATION -> LocationMessageView(message, outgoing)
        MessageContentType.CARD -> CardMessageView(message, outgoing)
        MessageContentType.LINK_CARD -> LinkCardMessageView(message, outgoing)
        MessageContentType.MINI_PROGRAM -> MiniProgramMessageView(message, outgoing)
        MessageContentType.VOTE -> VoteMessageView(message, outgoing)
        MessageContentType.TASK -> TaskMessageView(message, outgoing)
        MessageContentType.SCHEDULE -> ScheduleMessageView(message, outgoing)
        MessageContentType.NOTIFICATION, MessageContentType.ANNOUNCEMENT, MessageContentType.SYSTEM -> SystemMessageView(message, outgoing)
        else -> Fallback(message, textColor)
    }
}
