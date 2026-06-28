package com.flare.im.app.features.messaging.messagerow

import androidx.compose.runtime.Composable
import com.flare.im.app.core.domain.AppMessage

// 卡片型内容消息：各自一个语义清晰的组件，统一委派给 [CardRow]（图标 + 标题）。
// 这些类型当前仅展示标题摘要；后续可在各函数内独立扩展为富卡片，互不影响。

@Composable
internal fun FileMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("📄", message.core.content?.str("name", "fileName") ?: "File", outgoing)

@Composable
internal fun LocationMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("📍", message.core.content?.str("title", "address") ?: "Location", outgoing)

@Composable
internal fun CardMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("🪪", message.core.content?.str("title") ?: "Card", outgoing)

@Composable
internal fun LinkCardMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("🔗", message.core.content?.str("title", "url") ?: "Link", outgoing)

@Composable
internal fun MiniProgramMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("▦", message.core.content?.str("title") ?: "Mini program", outgoing)

@Composable
internal fun VoteMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("📊", message.core.content?.str("title") ?: "Vote", outgoing)

@Composable
internal fun TaskMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("✓", message.core.content?.str("title") ?: "Task", outgoing)

@Composable
internal fun ScheduleMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("📅", message.core.content?.str("title") ?: "Schedule", outgoing)

/** 通知 / 公告 / 系统消息：统一图标 + 预览文本。 */
@Composable
internal fun SystemMessageView(message: AppMessage, outgoing: Boolean) =
    CardRow("📢", message.previewText, outgoing)
