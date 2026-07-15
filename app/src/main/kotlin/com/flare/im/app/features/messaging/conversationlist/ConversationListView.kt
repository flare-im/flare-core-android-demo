package com.flare.im.app.features.messaging.conversationlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flare.im.app.R
import com.flare.im.app.core.FlareAppStore
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppConversation
import com.flare.im.app.core.domain.ConversationFilter
import com.flare.im.app.features.shell.EmptyState
import com.flare.im.app.features.shell.StatusDot
import com.flare.im.app.features.shell.statusLabel

/** 会话列表屏：眉标头 + 过滤 + 列表 + 起会话。 */
@Composable
fun ConversationListScreen(store: FlareAppStore) {
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    val vm = store.messagingViewModel
    val conversations by vm.visibleConversations.collectAsState()
    val filter by store.environment.filter.collectAsState()
    val status by store.environment.runtimeStatus.collectAsState()
    val all by vm.conversations.collectAsState()

    var startOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (all.isEmpty()) vm.bootstrapHome() }
    if (startOpen) StartConversationDialog(store) { startOpen = false }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(tk.lg), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.brand_eyebrow), style = FlareTheme.type.eyebrow, color = colors.textSecondary)
                Text(stringResource(R.string.conversations_title), style = FlareTheme.type.largeTitle, color = colors.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(status)
                    Spacer(Modifier.width(tk.xs))
                    Text(
                        stringResource(R.string.conversations_subtitle, all.size, all.count { it.core.isPinned }, statusLabel(status)),
                        style = FlareTheme.type.captionStrong, color = colors.textSecondary,
                    )
                }
            }
            IconButton(onClick = { startOpen = true }) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(colors.brand), Alignment.Center) {
                    Text("+", color = colors.outgoingText, style = FlareTheme.type.title)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = tk.lg).padding(bottom = tk.sm), horizontalArrangement = Arrangement.spacedBy(tk.sm)) {
            ConversationFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { store.environment.setFilter(f); vm.refreshConversations() },
                    label = { Text(stringResource(f.titleRes), style = FlareTheme.type.caption) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.brandSoft, selectedLabelColor = colors.brand),
                )
            }
        }
        HorizontalDivider(color = colors.hairline)
        if (conversations.isEmpty()) {
            EmptyState(stringResource(R.string.conversations_empty_title), stringResource(R.string.conversations_empty_message))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(conversations, key = { it.conversationId }) { c -> ConversationRow(store, c) }
            }
        }
    }
}

/** Feishu-style relative time label for the inbox row (HH:mm today / Yesterday / M/d / yyyy/M/d). */
private fun conversationTimeLabel(millis: Long): String {
    if (millis <= 0L) return ""
    val cal = java.util.Calendar.getInstance()
    val nowYear = cal.get(java.util.Calendar.YEAR)
    val nowDoy = cal.get(java.util.Calendar.DAY_OF_YEAR)
    cal.timeInMillis = millis
    val year = cal.get(java.util.Calendar.YEAR)
    val doy = cal.get(java.util.Calendar.DAY_OF_YEAR)
    val loc = java.util.Locale.getDefault()
    return when {
        year == nowYear && doy == nowDoy -> java.text.SimpleDateFormat("HH:mm", loc).format(cal.time)
        year == nowYear && doy == nowDoy - 1 -> "Yesterday"
        year == nowYear -> java.text.SimpleDateFormat("M/d", loc).format(cal.time)
        else -> java.text.SimpleDateFormat("yyyy/M/d", loc).format(cal.time)
    }
}

/** Inline title tags for a conversation row (Group / Bot / Official), mapped into the kit. */
private fun conversationRowTags(c: AppConversation): List<com.flare.im.ui.ConversationRowTag> = buildList {
    if (c.core.conversationType == com.flare.im.model.common.enums.ConversationType.GROUP) {
        add(com.flare.im.ui.ConversationRowTag("Group", com.flare.im.ui.FlareTagTone.Info))
    }
    c.core.role?.trim()?.takeIf { it.isNotEmpty() }?.let { role ->
        val lower = role.lowercase()
        val label = when {
            lower.contains("bot") || lower.contains("robot") -> "Bot"
            lower.contains("official") -> "Official"
            else -> role.take(10)
        }
        add(com.flare.im.ui.ConversationRowTag(label, com.flare.im.ui.FlareTagTone.Warning))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(store: FlareAppStore, c: AppConversation) {
    val vm = store.messagingViewModel
    var menu by remember { mutableStateOf(false) }
    Box {
        com.flare.im.ui.ConversationRow(
            item = com.flare.im.ui.ConversationRowData(
                id = c.conversationId,
                title = c.appTitle,
                avatarUrl = c.core.avatarUrl.takeIf { it.isNotBlank() },
                preview = c.appPreview,
                timestampLabel = conversationTimeLabel(c.appSortTimestamp),
                unreadCount = c.core.unreadCount,
                pinned = c.core.isPinned,
                muted = c.core.isMuted,
                mentioned = c.core.mentionMe,
                tags = conversationRowTags(c),
            ),
            onSelect = { vm.openConversation(c.conversationId) },
            onLongPress = { menu = true },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(
                (if (c.core.isPinned) "Unpin" else "Pin") to "pin",
                (if (c.core.isMuted) "Unmute" else "Mute") to "mute",
                (if (c.core.isArchived) "Unarchive" else "Archive") to "archive",
                "Mark unread" to "unread", "Clear local" to "clear", "Delete" to "delete",
            ).forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; vm.conversationAction(action, c) })
            }
        }
    }
}

/** 起会话对话框（单聊/群聊）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartConversationDialog(store: FlareAppStore, onDismiss: () -> Unit) {
    val tk = FlareTheme.tokens
    val vm = store.messagingViewModel
    val draft by vm.startConversationDraft.collectAsState()
    var group by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (group) vm.openGroupConversation() else vm.openPeerConversation(); onDismiss() }) {
                Text(stringResource(R.string.action_open))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.conversations_start), style = FlareTheme.type.headline) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(tk.sm)) {
                    FilterChip(!group, { group = false }, { Text("Direct") })
                    FilterChip(group, { group = true }, { Text("Group") })
                }
                Spacer(Modifier.height(tk.sm))
                if (group) {
                    OutlinedTextField(draft.groupUserIds, { v -> vm.updateStartDraft { it.copy(groupUserIds = v) } }, label = { Text("Member IDs (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(draft.peerUserId, { v -> vm.updateStartDraft { it.copy(peerUserId = v) } }, label = { Text("Peer ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    )
}
