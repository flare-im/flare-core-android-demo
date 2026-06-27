package com.flare.im.app.features.messaging.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flare.im.app.R
import com.flare.im.app.core.FlareAppStore
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.features.messaging.composer.ComposerBar
import com.flare.im.app.features.messaging.messagerow.MessageRow
import com.flare.im.app.features.shell.EmptyState
import com.flare.im.app.features.shell.StatusDot
import com.flare.im.app.features.shell.statusLabel

/** 聊天屏（对应 iOS Chat/ChatView）：返回 + 标题头(状态副标题 + 会话内搜索) + 时间线 + 输入区(ComposerBar)。 */
@Composable
fun ChatScreen(store: FlareAppStore) {
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    val vm = store.messagingViewModel
    val conversation by vm.selectedConversation.collectAsState()
    val messages by vm.selectedMessages.collectAsState()
    val me by vm.currentUserId.collectAsState()
    val status by store.environment.runtimeStatus.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(colors.surface).padding(tk.md), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { store.environment.setSelectedConversationId(null) }) { Text("‹", style = FlareTheme.type.title, color = colors.brand) }
            Column(Modifier.weight(1f)) {
                Text(conversation?.appTitle ?: "", style = FlareTheme.type.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(status)
                    Spacer(Modifier.width(tk.xs))
                    Text(statusLabel(status), style = FlareTheme.type.caption, color = colors.textSecondary)
                }
            }
            IconButton(onClick = { searchOpen = true }) { Text("🔍", style = FlareTheme.type.title) }
        }
        HorizontalDivider(color = colors.hairline)
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f)) { EmptyState(stringResource(R.string.chat_empty_title), stringResource(R.string.chat_empty_message)) }
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = tk.md), contentPadding = PaddingValues(vertical = tk.md), verticalArrangement = Arrangement.spacedBy(tk.sm)) {
                items(messages, key = { it.appStableId }) { m -> MessageRow(m, outgoing = m.core.senderId == me, vm = vm) }
            }
        }
        ComposerBar(vm, conversation?.appTitle ?: "")
    }

    if (searchOpen) InChatSearchSheet(store) { searchOpen = false }
}

/** 会话内消息搜索（对应 iOS ChatSearchSheet）：复用 SearchViewModel 的 conversation-scoped 搜索。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InChatSearchSheet(store: FlareAppStore, onDismiss: () -> Unit) {
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    val searchVm = store.searchViewModel
    val draft by searchVm.draft.collectAsState()
    val results by searchVm.results.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = tk.lg).padding(bottom = tk.lg)) {
            Text(stringResource(R.string.chat_search_title), style = FlareTheme.type.headline, color = colors.textPrimary)
            Spacer(Modifier.height(tk.sm))
            OutlinedTextField(
                value = draft.keyword,
                onValueChange = { v -> searchVm.updateDraft { it.copy(keyword = v, conversationScoped = true) } },
                label = { Text(stringResource(R.string.chat_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(tk.sm))
            TextButton(onClick = { searchVm.updateDraft { it.copy(conversationScoped = true) }; searchVm.search() }) {
                Text(stringResource(R.string.chat_search_action), color = colors.brand)
            }
            HorizontalDivider(color = colors.hairline)
            if (results.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                    Text(stringResource(R.string.chat_search_empty), style = FlareTheme.type.callout, color = colors.textTertiary)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(results, key = { it.appStableId }) { m ->
                        Column(Modifier.fillMaxWidth().padding(vertical = tk.sm)) {
                            Text(m.core.senderId, style = FlareTheme.type.captionStrong, color = colors.textSecondary)
                            Text(m.previewText, style = FlareTheme.type.callout, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
