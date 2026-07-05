package com.flare.im.app.features.messaging

import android.util.Log
import com.flare.im.api.FlareImClient
import com.flare.im.app.core.data.AppEnvironment
import com.flare.im.app.core.data.ViewDataRepository
import com.flare.im.app.core.domain.ConversationFilter
import com.flare.im.app.core.domain.MessageBuildOp
import com.flare.im.app.core.domain.MessageBuilder
import com.flare.im.app.core.session.AppLifecycle
import com.flare.im.app.core.session.AppSession
import com.flare.im.app.core.domain.AppConversation
import com.flare.im.app.core.domain.AppMessage
import com.flare.im.app.core.domain.SdkModelMapper
import com.flare.im.model.command.message.SendMessageRequest
import com.flare.im.model.entity.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 起会话草稿。 */
data class StartConversationDraft(
    val peerUserId: String = "",
    val groupUserIds: String = "",
)

/**
 * 消息特性 ViewModel：会话列表 + 时间线 + 全部消息/会话操作。
 * 持共享 session/repository/environment + weak lifecycle；经 scope 调 SDK（Map 形态 payload）。
 */
class MessagingViewModel(
    private val session: AppSession,
    private val repository: ViewDataRepository,
    private val environment: AppEnvironment,
    private val scope: CoroutineScope,
) {
    private var lifecycle: AppLifecycle? = null
    fun bind(lifecycle: AppLifecycle) {
        this.lifecycle = lifecycle
        session.onViewUpdate = { update ->
            scope.launch {
                client?.let { repository.apply(it, update) }
            }
        }
        session.onMessageReceived = { conversationId ->
            scope.launch {
                val sdk = client ?: return@launch
                repository.refreshAfterMessageEvent(sdk, conversationId, "realtime")
                if (environment.selectedConversationId.value == conversationId) {
                    maxPositiveSeq(repository.messages(conversationId))?.let { readSeq ->
                        sdk.conversations.markConversationRead(mapOf("conversationId" to conversationId, "readSeq" to readSeq))
                    }
                }
            }
        }
    }

    private val client: FlareImClient? get() = session.client

    val conversations: StateFlow<List<AppConversation>> = repository.conversations
    val runtimeStatus get() = environment.runtimeStatus
    val currentUserId get() = session.currentUserId
    val lastError get() = environment.lastError

    private val _pendingMessageKeys = MutableStateFlow<Set<String>>(emptySet())
    val pendingMessageKeys: StateFlow<Set<String>> = _pendingMessageKeys.asStateFlow()
    private val _failedMessageKeys = MutableStateFlow<Set<String>>(emptySet())
    val failedMessageKeys: StateFlow<Set<String>> = _failedMessageKeys.asStateFlow()

    private val _startConversationDraft = MutableStateFlow(StartConversationDraft())
    val startConversationDraft: StateFlow<StartConversationDraft> = _startConversationDraft.asStateFlow()
    fun updateStartDraft(transform: (StartConversationDraft) -> StartConversationDraft) = _startConversationDraft.update(transform)

    /** 按当前过滤可见的会话。 */
    val visibleConversations: StateFlow<List<AppConversation>> =
        combine(repository.conversations, environment.filter) { list, filter -> list.filter { matchesFilter(it, filter) } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val selectedConversation: StateFlow<AppConversation?> =
        combine(repository.conversations, environment.selectedConversationId) { list, id ->
            id?.let { sel -> list.firstOrNull { it.conversationId == sel } }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val selectedMessages: StateFlow<List<AppMessage>> =
        combine(repository.messagesByConversation, environment.selectedConversationId) { byId, id ->
            id?.let { byId[it] } ?: emptyList()
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val selectedHasMore: StateFlow<Boolean> =
        combine(repository.hasMoreByConversation, environment.selectedConversationId) { byId, id ->
            id?.let { byId[it] } ?: true
        }.stateIn(scope, SharingStarted.Eagerly, true)

    /** 由 session 的 send-failed 事件路由进来。 */
    fun markSendFailed(key: String) { _failedMessageKeys.update { it + key } }

    fun logout() = scope.launch { lifecycle?.logout() }

    fun refreshConversations() = scope.launch {
        environment.run("view.conversation_list.open") { syncConversationList("refresh") }
    }

    fun bootstrapHome() = scope.launch {
        environment.run("view.conversation_list.open") {
            syncConversationList("bootstrap")
            environment.selectedConversationId.value?.let { openConversationInternal(it) }
        }
    }

    fun openConversation(conversationId: String) = scope.launch { openConversationInternal(conversationId) }

    private suspend fun openConversationInternal(conversationId: String) {
        environment.run("view.timeline.open") {
            val sdk = client ?: error("Login before opening a conversation")
            environment.setSelectedConversationId(conversationId)
            repository.openTimeline(sdk, conversationId, "open")
            maxPositiveSeq(repository.messages(conversationId))?.let { readSeq ->
                sdk.conversations.markConversationRead(mapOf("conversationId" to conversationId, "readSeq" to readSeq))
            }
        }
    }

    fun loadOlderMessages() = scope.launch {
        val id = environment.selectedConversationId.value ?: return@launch
        environment.run("view.timeline.load_older", showBusy = false) {
            client?.let { repository.loadOlderTimeline(it, id) }
        }
    }

    fun sendText(text: String): Unit = sendTextBlocking(text).let {}
    private fun sendTextBlocking(text: String) = scope.launch {
        val trimmed = text.trim()
        val conversationId = environment.selectedConversationId.value ?: return@launch
        if (trimmed.isEmpty()) return@launch
        environment.run("message.send_text") {
            val sdk = client ?: error("Login before sending messages")
            val message = sdk.messageBuilder.buildText(
                com.flare.im.model.command.message.build.BuildTextMessageRequest(conversationId, trimmed),
            )
            sendBuilt(sdk, message, conversationId)
        }
    }

    /** 图片选择 → media.uploadImage（best-effort，需后端）→ buildImage → 发送。 */
    fun sendPickedImage(path: String) = scope.launch {
        val conversationId = environment.selectedConversationId.value ?: return@launch
        environment.run("media.uploadImage") {
            val sdk = client ?: error("Login before sending images")
            val imageId = "picked-${System.currentTimeMillis()}"
            runCatching { sdk.media.uploadImage(mapOf("path" to path, "imageId" to imageId)) }
            val message = MessageBuilder.build(
                sdk, conversationId, MessageBuildOp.CreateImage,
                mapOf("imageId" to imageId, "sourceUrl" to path, "description" to "Picked image"),
                selectedMessages.value,
            )
            sendBuilt(sdk, message, conversationId)
        }
    }

    /** 语音录制完成 → media.uploadFile（best-effort）→ buildAudio → 发送。 */
    fun sendAudio(path: String, durationMs: Int) = scope.launch {
        val conversationId = environment.selectedConversationId.value ?: return@launch
        environment.run("media.uploadFile") {
            val sdk = client ?: error("Login before sending audio")
            val audioId = "audio-${System.currentTimeMillis()}"
            runCatching { sdk.media.uploadFile(mapOf("path" to path)) }
            val message = MessageBuilder.build(
                sdk, conversationId, MessageBuildOp.CreateAudio,
                mapOf("audioId" to audioId, "sourceUrl" to path, "durationMs" to durationMs),
                selectedMessages.value,
            )
            sendBuilt(sdk, message, conversationId)
        }
    }

    fun buildAndSend(op: MessageBuildOp, payload: Map<String, Any?> = emptyMap()) = scope.launch {
        val conversationId = environment.selectedConversationId.value ?: return@launch
        environment.run("message_builder.${op.name}") {
            val sdk = client ?: error("Login before building messages")
            val message = MessageBuilder.build(sdk, conversationId, op, payload, selectedMessages.value)
            sendBuilt(sdk, message, conversationId)
        }
    }

    private suspend fun sendBuilt(sdk: FlareImClient, message: Message, conversationId: String) {
        val appMessage = SdkModelMapper.messageFromCore(message)
        val key = appMessage.appStableId
        // 乐观：发送前立即显示（optimistic always）；ack 后 re-fetch 用权威快照对账。
        repository.insertOptimistic(appMessage)
        _pendingMessageKeys.update { it + key }
        try {
            val ack = sdk.messages.sendMessage(SendMessageRequest(message), null)
            _pendingMessageKeys.update { it - key }
            environment.appendLab("message.send", "ok", "seq ${ack.seq}, server ${ack.serverId}")
            repository.openTimeline(sdk, conversationId, "send")
        } catch (error: Throwable) {
            _pendingMessageKeys.update { it - key }
            _failedMessageKeys.update { it + key }
            val detail = error.message ?: error.toString()
            environment.appendLab("message.send", "error", detail)
            Log.e("FlareAndroidSmoke", "message.send failed", error)
            throw error
        }
    }

    fun retry(message: AppMessage) = scope.launch {
        environment.run("message.retry") {
            val sdk = client ?: error("Login before retrying messages")
            _failedMessageKeys.update { it - message.appStableId }
            sendBuilt(sdk, message.core, message.conversationId)
        }
    }

    /**
     * 解析消息媒体到可显示 URL：本地路径直接用；远端经 `media.resolveMediaAccess(fileId)` 换签名 URL。
     * 解析媒体展示地址。失败兜底直链。
     */
    suspend fun resolveMediaUrl(message: AppMessage): String? {
        val data = message.core.content?.data ?: return null
        fun pick(keys: List<String>): String? {
            for (k in keys) (data[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            (data["source"] as? Map<*, *>)?.let { src ->
                for (k in keys) (src[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            return null
        }
        val urlKeys = listOf(
            "sourceUrl", "source_url", "url", "cdnUrl", "cdn_url", "mediaUrl", "media_url",
            "downloadUrl", "download_url", "accessUrl", "access_url", "tempUrl", "temp_url",
            "signedUrl", "signed_url", "path", "localPath",
        )
        val direct = pick(urlKeys)
        if (direct != null && (direct.startsWith("content://") || direct.startsWith("file://") || direct.startsWith("/"))) return direct
        val fileId = pick(listOf("fileId", "file_id", "imageId", "audioId", "videoId", "mediaId", "media_id", "uuid", "id"))
        val sdk = client
        if (fileId != null && sdk != null) {
            // 优先经 SDK 托管磁盘缓存拿本地路径（去重 + LRU + 离线，不重复下载）。
            val cached = runCatching { sdk.media.cacheRemoteMedia(mapOf("fileId" to fileId, "expiresIn" to 3600)) }.getOrNull()
            (cached?.get("localPath") as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            // 退而求其次：签名 URL（不落缓存）。
            val resolved = runCatching { sdk.media.resolveMediaAccess(mapOf("fileId" to fileId, "expiresIn" to 3600)) }.getOrNull()
            if (resolved != null) {
                for (k in urlKeys) (resolved[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return direct
    }

    /** 把媒体存到系统下载目录（对应主流 IM「保存到相册/下载」）：走 SDK media.downloadFileToDownloads。 */
    fun saveToDownloads(message: AppMessage) = scope.launch {
        environment.run("media.downloadFileToDownloads") {
            val sdk = client ?: error("Login before saving media")
            val fileId = mediaFileId(message) ?: error("No downloadable media id")
            val saved = sdk.media.downloadFileToDownloads(mapOf("fileId" to fileId))
            environment.appendLab("media.download", "ok", saved.toString())
        }
    }

    private fun mediaFileId(message: AppMessage): String? {
        val data = message.core.content?.data ?: return null
        val keys = listOf("fileId", "file_id", "imageId", "audioId", "videoId", "mediaId", "media_id", "uuid", "id")
        for (k in keys) (data[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        (data["source"] as? Map<*, *>)?.let { src ->
            for (k in keys) (src[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun messageAction(action: String, message: AppMessage, reaction: String = "like") = scope.launch {
        environment.run("message.$action") {
            val sdk = client ?: error("Login before message actions")
            val req = mutationRequest(message)
            when (action) {
                "recall" -> sdk.messages.recallMessage(req)
                "edit" -> sdk.messages.editTextByMessageId(req + ("text" to "Edited from Android example"))
                "editRich" -> sdk.messages.editRichDocByMessageId(req + ("markdown" to "## Edited rich doc\n\n- **bold** point\n- _italic_ point"))
                "deleteSelf" -> sdk.messages.deleteMessageForSelf(req)
                "deleteEveryone" -> sdk.messages.deleteMessageForEveryone(req)
                "react" -> sdk.messages.addReaction(req + ("reaction" to reaction))
                "unreact" -> sdk.messages.removeReaction(req + ("reaction" to reaction))
                "pin" -> sdk.messages.pinMessageById(req + ("scope" to 0))
                "pinSelf" -> sdk.messages.pinMessageById(req + ("scope" to 1))
                "unpin" -> sdk.messages.unpinMessageById(req + ("scope" to 0))
                "mark" -> sdk.messages.markMessageById(req)
                "unmark" -> sdk.messages.unmarkMessageById(req)
                else -> error("Unsupported action $action")
            }
            openConversationInternal(message.conversationId)
        }
    }

    fun setTyping(typing: Boolean) = scope.launch {
        val id = environment.selectedConversationId.value ?: return@launch
        environment.run("message.typing", showBusy = false) {
            client?.messages?.setTyping(mapOf("conversationId" to id, "typing" to typing))
        }
    }

    fun openPeerConversation() = scope.launch {
        val peer = startConversationDraft.value.peerUserId.trim()
        if (peer.isEmpty()) return@launch
        environment.run("conversation.get_one") {
            val sdk = client ?: error("Login before starting chat")
            val conv = sdk.conversations.getOneConversation(mapOf("sourceId" to peer, "conversationType" to "single"))
            openConversationInternal(conv.conversationId)
            syncConversationList("open_peer")
        }
    }

    fun openGroupConversation() = scope.launch {
        val ids = startConversationDraft.value.groupUserIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return@launch
        environment.run("conversation.get_group_by_user_ids") {
            val sdk = client ?: error("Login before starting group chat")
            val conv = sdk.conversations.getGroupConversationByUserIds(mapOf("userIds" to ids))
            openConversationInternal(conv.conversationId)
            syncConversationList("open_group")
        }
    }

    fun conversationAction(action: String, conversation: AppConversation) = scope.launch {
        environment.run("conversation.$action") {
            val sdk = client ?: error("Login before conversation actions")
            val req = mapOf("conversationId" to conversation.conversationId)
            when (action) {
                "pin" -> sdk.conversations.setConversationPinned(req + ("pinned" to !conversation.core.isPinned))
                "mute" -> sdk.conversations.setConversationMuted(req + ("muted" to !conversation.core.isMuted))
                "archive" -> sdk.conversations.setConversationArchived(req + ("archived" to !conversation.core.isArchived))
                "unread" -> sdk.conversations.markConversationUnread(req)
                "clear" -> { sdk.conversations.clearLocalChatHistory(req); repository.clearMessages(conversation.conversationId) }
                "delete" -> { sdk.conversations.deleteConversation(req); repository.removeConversation(conversation.conversationId) }
                else -> error("Unsupported action $action")
            }
            syncConversationList("action")
        }
    }

    fun saveDraft(text: String) = scope.launch {
        val id = environment.selectedConversationId.value ?: return@launch
        environment.run("conversation.draft", showBusy = false) {
            client?.conversations?.updateConversationDraft(
                com.flare.im.model.command.UpdateConversationDraftRequest(conversationId = id, draft = text),
            )
            syncConversationList("draft")
        }
    }

    private suspend fun syncConversationList(reason: String) {
        val sdk = client ?: error("Login before loading conversations")
        repository.openConversationList(sdk, reason)
        if (environment.selectedConversationId.value == null) {
            environment.setSelectedConversationId(repository.conversations.value.firstOrNull()?.conversationId)
        }
    }

    private fun mutationRequest(message: AppMessage): Map<String, Any?> = mapOf(
        "conversationId" to message.conversationId,
        "messageId" to message.core.serverId.ifBlank { message.core.clientMsgId },
        "clientMsgId" to message.core.clientMsgId,
        "seq" to message.core.conversationSeq,
    )

    private fun matchesFilter(c: AppConversation, filter: ConversationFilter): Boolean = when (filter) {
        ConversationFilter.All -> !c.core.isArchived
        ConversationFilter.Unread -> c.core.unreadCount > 0 && !c.core.isArchived
        ConversationFilter.Mentions -> c.core.mentionMe && !c.core.isArchived
        ConversationFilter.Pinned -> c.core.isPinned && !c.core.isArchived
        ConversationFilter.Archived -> c.core.isArchived
        ConversationFilter.Muted -> c.core.isMuted && !c.core.isArchived
        ConversationFilter.Drafts -> !c.core.draft.isNullOrBlank() && !c.core.isArchived
    }

    private fun maxPositiveSeq(messages: List<AppMessage>): Long? =
        messages.maxOfOrNull { it.core.conversationSeq }.takeIf { (it ?: 0L) > 0L }
}
