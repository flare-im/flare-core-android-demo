package com.flare.im.app.features.messaging.messagerow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.core.domain.MessageBuildOp
import com.flare.im.app.features.messaging.MessagingViewModel

/** 一行消息：气泡外壳 + 长按动作菜单 + 发送状态 + 表情回应条。
 *  内容渲染按 contentType 委派给 [MessageContentView]（每类型独立组件文件）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(message: AppMessage, outgoing: Boolean, vm: MessagingViewModel) {
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    var menu by remember { mutableStateOf(false) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    val pending by vm.pendingMessageKeys.collectAsState()
    val failed by vm.failedMessageKeys.collectAsState()
    val clipboard = LocalClipboardManager.current
    val me by vm.currentUserId.collectAsState()
    val content = message.core.content
    val standalone = !message.core.isRecalled && isStandaloneAsset(content, message.previewText)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
        Box {
            Box(
                Modifier.widthIn(max = 280.dp)
                    .then(
                        if (standalone) Modifier
                        else Modifier.clip(tk.radiusLarge).background(
                            if (message.core.isRecalled) colors.surfaceAlt else if (outgoing) colors.outgoing else colors.incomingBubble,
                        ),
                    )
                    .combinedClickable(onClick = {}, onLongClick = { if (!message.core.isRecalled) menu = true })
                    .padding(if (standalone) PaddingValues(2.dp) else PaddingValues(horizontal = tk.md, vertical = tk.sm)),
            ) {
                MessageContentView(message, outgoing, vm) { previewPath = it }
            }
            MessageActionMenu(message, content, menu, clipboard, vm) { menu = it }
        }
        when {
            message.appStableId in failed -> Text("Failed · tap to retry", style = FlareTheme.type.caption, color = colors.danger, modifier = Modifier.clickable { vm.retry(message) })
            message.appStableId in pending -> Text("Sending…", style = FlareTheme.type.caption, color = colors.textTertiary)
        }
        ReactionStrip(message, me, vm)
    }
    previewPath?.let { p -> MediaPreviewDialog(p) { previewPath = null } }
}

/** 长按动作菜单：快捷表情回应 + 编辑/撤回/删除/置顶/标记 + 复制/转发/保存。 */
@Composable
private fun MessageActionMenu(
    message: AppMessage,
    content: com.flare.im.model.entity.MessageContent?,
    expanded: Boolean,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    vm: MessagingViewModel,
    onExpandedChange: (Boolean) -> Unit,
) {
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        Row(Modifier.padding(horizontal = tk.md, vertical = tk.xs), horizontalArrangement = Arrangement.spacedBy(tk.sm)) {
            quickReactions.forEach { emoji ->
                Text(
                    emoji,
                    style = FlareTheme.type.title,
                    modifier = Modifier.clip(tk.pill).clickable { onExpandedChange(false); vm.messageAction("react", message, emoji) }.padding(2.dp),
                )
            }
        }
        HorizontalDivider(color = colors.hairline)
        listOf(
            "Pin" to "pin", "Pin for me" to "pinSelf", "Unpin" to "unpin", "Flag" to "mark",
            "Edit text" to "edit", "Edit rich" to "editRich", "Recall" to "recall", "Delete for me" to "deleteSelf",
        ).forEach { (label, action) ->
            DropdownMenuItem(text = { Text(label) }, onClick = { onExpandedChange(false); vm.messageAction(action, message) })
        }
        DropdownMenuItem(text = { Text("Copy") }, onClick = { onExpandedChange(false); clipboard.setText(AnnotatedString(message.previewText)) })
        DropdownMenuItem(text = { Text("Forward") }, onClick = { onExpandedChange(false); vm.buildAndSend(MessageBuildOp.CreateForward) })
        if (content?.contentType in mediaContentTypes) {
            DropdownMenuItem(text = { Text("Save to downloads") }, onClick = { onExpandedChange(false); vm.saveToDownloads(message) })
        }
    }
}

/** 表情回应条：每个 emoji + 计数为可点 chip，点己有=取消、点他人=追加。 */
@Composable
private fun ReactionStrip(message: AppMessage, me: String?, vm: MessagingViewModel) {
    val reactions = message.core.reactions
    if (reactions.isEmpty()) return
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    Row(
        Modifier.padding(top = 2.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tk.xs),
    ) {
        reactions.forEach { r ->
            val mine = me != null && me in r.userIds
            Row(
                Modifier.clip(tk.pill)
                    .background(if (mine) colors.brandSoft else colors.surfaceAlt)
                    .clickable { vm.messageAction(if (mine) "unreact" else "react", message, r.emoji) }
                    .padding(horizontal = tk.sm, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.emoji, style = FlareTheme.type.caption)
                if (r.count > 0) {
                    Spacer(Modifier.width(2.dp))
                    Text("${r.count}", style = FlareTheme.type.caption, color = if (mine) colors.brand else colors.textSecondary)
                }
            }
        }
    }
}
