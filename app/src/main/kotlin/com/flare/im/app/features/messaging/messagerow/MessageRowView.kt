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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.flare.im.app.core.domain.MessageDelivery
import com.flare.im.app.core.domain.MessageDeliveryState
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.flare.im.app.R
import com.flare.im.model.common.enums.MessageContentType
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
    // 送达状态：判定用核心那一份（MessageDelivery，由 sdk-spec 向量钉住）。
    val deliveryState = MessageDelivery.state(
        isSelf = outgoing,
        status = message.core.status,
        isRead = message.core.isRead,
        isPending = message.appStableId in pending,
        isFailed = message.appStableId in failed,
    )
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
                // 送达状态在**气泡内部**（与 iOS 的 bottomTrailing 同位置）。
                //
                // 文本走 kit 的 trailing 插槽：文本气泡由 kit 绘制，只有它能为
                // 勾号**留出空间**；叠加也能放对位置，但正文一长就压住末行文字。
                // 其余内容类型是自带留白的卡片，叠加到右下角即可。
                val statusSlot: (@Composable () -> Unit)? =
                    if (deliveryState == MessageDeliveryState.NONE) {
                        null
                    } else {
                        {
                            MessageDeliveryStatus(
                                state = deliveryState,
                                onBubble = outgoing,
                                onRetry = { vm.retry(message) },
                            )
                        }
                    }
                val textLike = content?.contentType == null ||
                    content.contentType == MessageContentType.TEXT ||
                    content.contentType == MessageContentType.RICH_TEXT
                MessageContentView(
                    message,
                    outgoing,
                    vm,
                    deliveryStatus = if (textLike) statusSlot else null,
                ) { previewPath = it }
                if (!textLike && statusSlot != null) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp),
                    ) { statusSlot() }
                }
            }
            MessageActionMenu(message, content, menu, clipboard, vm) { menu = it }
        }

        ReactionStrip(message, me, vm)
    }
    previewPath?.let { p -> MediaPreviewDialog(p) { previewPath = null } }
}

/** 长按动作菜单：可用性由**核心**判定（`domain::message_actions`），这里只渲染结果。
 *
 *  曾经是一个无条件全显的静态列表：Pin 与 Unpin 同时出现、别人的消息上也显示
 *  Recall、图片上也显示 Edit —— 点了必然失败。规则散在各端就会这样，
 *  现在四端共用核心那一份。 */
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
    var availability by remember(message.appStableId) { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var editing by remember { mutableStateOf(false) }

    // 菜单打开时才问核心：长按是低频动作，一次 FFI 往返远在 100ms 交互预算之内，
    // 而把判定留在端上就等于再抄一份规则。
    LaunchedEffect(expanded, message.appStableId) {
        if (expanded) availability = vm.actionAvailability(message)
    }
    fun can(key: String): Boolean = availability[key] == true

    if (editing) {
        MessageEditDialog(
            initial = message.previewText,
            onDismiss = { editing = false },
            onConfirm = { text ->
                editing = false
                vm.messageAction("edit", message, text = text)
            },
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        if (can("canReact")) {
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
        }

        @Composable
        fun item(labelRes: Int, onClick: () -> Unit) {
            DropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                onClick = { onExpandedChange(false); onClick() },
            )
        }

        if (can("canResend")) item(R.string.msg_action_resend) { vm.retry(message) }
        if (can("canReply")) item(R.string.msg_action_reply) { vm.replyTo(message) }
        if (can("canForward")) item(R.string.msg_action_forward) { vm.buildAndSend(MessageBuildOp.CreateForward) }
        if (can("canCopy")) item(R.string.msg_action_copy) { clipboard.setText(AnnotatedString(message.previewText)) }
        if (can("canEdit")) {
            item(R.string.msg_action_edit) { editing = true }
            item(R.string.msg_action_edit_rich) { vm.messageAction("editRich", message) }
        }
        if (can("canRecall")) item(R.string.msg_action_recall) { vm.messageAction("recall", message) }
        if (can("canPin")) {
            item(R.string.msg_action_pin) { vm.messageAction("pin", message) }
            item(R.string.msg_action_pin_self) { vm.messageAction("pinSelf", message) }
        }
        if (can("canUnpin")) item(R.string.msg_action_unpin) { vm.messageAction("unpin", message) }
        if (can("canDelete")) {
            item(R.string.msg_action_mark) { vm.messageAction("mark", message) }
            item(R.string.msg_action_delete_self) { vm.messageAction("deleteSelf", message) }
            if (can("canRecall")) item(R.string.msg_action_delete_everyone) { vm.messageAction("deleteEveryone", message) }
        }
        if (can("canSave") && content?.contentType in mediaContentTypes) {
            item(R.string.msg_action_save) { vm.saveToDownloads(message) }
        }
    }
}

/** 编辑消息：曾经点一下"编辑"就把原文替换成写死的 "Edited from Android example"，
 *  没有任何输入入口 —— 用户的内容就这么没了。 */
@Composable
private fun MessageEditDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.msg_edit_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.msg_edit_hint)) },
                singleLine = false,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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

/** 送达状态指示：贴在气泡右下角，与 iOS DeliveryStatusGlyph 同视觉。
 *
 *  全部用图标而不是文字：文字（"发送中…" / "发送失败 · 点击重试"）在气泡里放不下，
 *  而且会把气泡撑成一个奇怪的宽度。失败图标可点重发。 */
@Composable
private fun MessageDeliveryStatus(
    state: MessageDeliveryState,
    onBubble: Boolean,
    onRetry: () -> Unit,
) {
    val colors = FlareTheme.colors
    val glyph = 14.dp
    val onBubbleTint = colors.outgoingText.copy(alpha = 0.9f)
    when (state) {
        MessageDeliveryState.NONE -> Unit
        MessageDeliveryState.SENDING -> CircularProgressIndicator(
            strokeWidth = 1.5.dp,
            color = if (onBubble) onBubbleTint else colors.textTertiary,
            modifier = Modifier.size(glyph).semantics { contentDescription = "发送中" },
        )
        MessageDeliveryState.FAILED -> Icon(
            Icons.Default.Warning,
            contentDescription = "发送失败，点击重试",
            tint = colors.danger,
            modifier = Modifier.size(glyph).clickable { onRetry() },
        )
        MessageDeliveryState.DELIVERED, MessageDeliveryState.READ -> Row(
            horizontalArrangement = Arrangement.spacedBy((-5).dp),
            modifier = Modifier.semantics {
                contentDescription = if (state == MessageDeliveryState.READ) "已读" else "已送达"
            },
        ) {
            val tint = when {
                state == MessageDeliveryState.READ && onBubble -> colors.outgoingText
                state == MessageDeliveryState.READ -> colors.brand
                onBubble -> onBubbleTint.copy(alpha = 0.65f)
                else -> colors.textTertiary
            }
            Icon(Icons.Default.Check, contentDescription = null, tint = tint, modifier = Modifier.size(glyph))
            if (state == MessageDeliveryState.READ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = tint, modifier = Modifier.size(glyph))
            }
        }
    }
}
