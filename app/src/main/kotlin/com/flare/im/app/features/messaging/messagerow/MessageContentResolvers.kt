package com.flare.im.app.features.messaging.messagerow

import com.flare.im.app.features.messaging.media.EmojiPresentation
import com.flare.im.model.common.enums.MessageContentType
import com.flare.im.model.entity.MessageContent

// 消息内容字段解析与判定（无 UI）。各内容视图复用，集中在此避免散落。

/** 取首个非空字符串字段（content.data[key]）。 */
internal fun MessageContent.str(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { (data[it] as? String)?.takeIf { s -> s.isNotBlank() } }

internal val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "👏")

internal val mediaContentTypes = setOf(
    MessageContentType.IMAGE, MessageContentType.IMAGE_GROUP, MessageContentType.VIDEO,
    MessageContentType.AUDIO, MessageContentType.FILE,
)

/** 媒体本地直链 / 远端 URL 候选字段。 */
internal fun imagePath(content: MessageContent?): String? =
    content?.str("sourceUrl", "url", "path", "localPath")?.takeIf { it.isNotBlank() }

internal fun emojiAsset(content: MessageContent?): String? =
    EmojiPresentation.normalizedPackKey(content?.str("emoji", "key"))?.let { EmojiPresentation.emojiAssetPath(it) }

internal fun stickerAsset(content: MessageContent?): String? {
    val id = content?.str("stickerId", "id") ?: return null
    val pkg = content.str("packageId", "package_id") ?: "classic"
    return EmojiPresentation.stickerAssetPath(pkg, id)
}

/** 是否为「独立资源」（emoji/sticker 大图、整段 emoji 文本）——此类不套气泡。 */
internal fun isStandaloneAsset(content: MessageContent?, preview: String): Boolean {
    if (content == null) return false
    return when (content.contentType) {
        MessageContentType.EMOJI -> emojiAsset(content) != null
        MessageContentType.STICKER -> stickerAsset(content) != null
        MessageContentType.TEXT -> content.str("docJson") == null
        // delegated to self-contained flare-im-design kit cards (they carry their own surface)
        MessageContentType.FILE, MessageContentType.LOCATION, MessageContentType.CARD,
        MessageContentType.LINK_CARD, MessageContentType.VOTE, MessageContentType.TASK,
        MessageContentType.IMAGE, MessageContentType.IMAGE_GROUP,
        MessageContentType.VIDEO, MessageContentType.AUDIO,
        MessageContentType.NOTIFICATION, MessageContentType.ANNOUNCEMENT, MessageContentType.SYSTEM -> true
        else -> false
    }
}
