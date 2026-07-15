package com.flare.im.app.features.messaging.messagerow

import androidx.compose.runtime.Composable
import com.flare.im.app.core.domain.AppMessage
// flare-im-design compose kit (com.flare.im:im-ui-compose) — rich, self-contained
// message cards. props in / events out; the app maps its content → props.
import com.flare.im.ui.ContactMessage
import com.flare.im.ui.FileMessage
import com.flare.im.ui.LinkCardMessage
import com.flare.im.ui.LocationMessage
import com.flare.im.ui.SystemMessage
import com.flare.im.ui.TaskMessage
import com.flare.im.ui.VoteMessage

// Card-type content → delegated to the flare-im-design kit's rich cards. These
// render standalone (the kit card carries its own surface/border, so MessageRow
// no longer wraps them in a bubble — see isStandaloneAsset). MiniProgram /
// Schedule have no kit equivalent yet → keep the CardRow placeholder.

@Composable
internal fun FileMessageView(message: AppMessage, outgoing: Boolean) {
    val c = message.core.content
    FileMessage(
        name = c?.str("name", "fileName") ?: "File",
        size = c?.str("size", "fileSize") ?: "",
        ext = c?.str("ext", "extension"),
    )
}

@Composable
internal fun LocationMessageView(message: AppMessage, outgoing: Boolean) {
    val c = message.core.content
    LocationMessage(
        title = c?.str("title", "address") ?: "Location",
        address = c?.str("address") ?: "",
    )
}

@Composable
internal fun CardMessageView(message: AppMessage, outgoing: Boolean) {
    val c = message.core.content
    ContactMessage(
        name = c?.str("title", "name") ?: "Contact",
        subtitle = c?.str("subtitle", "id"),
        avatarUrl = c?.str("avatarUrl", "avatar"),
    )
}

@Composable
internal fun LinkCardMessageView(message: AppMessage, outgoing: Boolean) {
    val c = message.core.content
    LinkCardMessage(
        title = c?.str("title", "url") ?: "Link",
        domain = c?.str("domain") ?: "",
        thumb = c?.str("thumbnailUrl", "thumb"),
        description = c?.str("description"),
    )
}

@Composable
internal fun MiniProgramMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("▦", message.core.content?.str("title") ?: "Mini program", outgoing)

@Composable
internal fun VoteMessageView(message: AppMessage, outgoing: Boolean) {
    VoteMessage(title = message.core.content?.str("title") ?: "Vote")
}

@Composable
internal fun TaskMessageView(message: AppMessage, outgoing: Boolean) {
    TaskMessage(title = message.core.content?.str("title") ?: "Task")
}

@Composable
internal fun ScheduleMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("📅", message.core.content?.str("title") ?: "Schedule", outgoing)

/** Notification / announcement / system → the kit's system pill. */
@Composable
internal fun SystemMessageView(message: AppMessage, outgoing: Boolean) {
    SystemMessage(text = message.previewText)
}
