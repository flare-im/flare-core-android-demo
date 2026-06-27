package com.flare.im.app.core.session

import com.flare.im.FlareCoreSdk
import com.flare.im.api.ConnectionState
import com.flare.im.api.FlareImClient
import com.flare.im.app.core.domain.EventLogEntry
import com.flare.im.app.core.domain.LoginDraft
import com.flare.im.listener.EventSubscription
import com.flare.im.model.command.CoreTokenRequest
import com.flare.im.model.entity.ViewUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 跨切面会话核心：唯一持有 `FlareImClient` 的地方（其余层经它拿门面）。
 * 拥有客户端生命周期 + 认证态 + 连接态 + 原始事件流，
 * 事件经 `onViewUpdate` / `onMessageSendFailed` 钩子路由给数据层/聊天层。
 */
class AppSession {
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _eventLog = MutableStateFlow<List<EventLogEntry>>(emptyList())
    val eventLog: StateFlow<List<EventLogEntry>> = _eventLog.asStateFlow()

    /** 唯一客户端持有点。 */
    var client: FlareImClient? = null
        private set

    /** 事件路由钩子（由数据层/聊天层注入）。 */
    var onViewUpdate: ((ViewUpdate) -> Unit)? = null
    var onMessageSendFailed: ((String) -> Unit)? = null

    private val subscriptions = mutableListOf<EventSubscription>()

    /** 创建 → 订阅 → init → 取 token → 登录。`progress` 回报阶段。 */
    suspend fun start(
        draft: LoginDraft,
        dataDir: String,
        progress: (String) -> Unit,
    ): FlareImClient {
        progress("Creating Android SDK client")
        val sdk = FlareCoreSdk.createClient()

        subscribeEvents(sdk)

        progress("Initializing SDK")
        // Canonical initRequest wrapper `{ environment, sdkConfig }`. `dataUrl` is a URL
        // filesDir is absolute, so this yields file:///data/...
        val sdkConfig = draft.transportConfig() + mapOf(
            "dataUrl" to "file://$dataDir",
            "tenantId" to draft.tenantId,
            "platform" to "android",
            "runtime" to "compose-example",
        )
        val initConfig = mapOf(
            "environment" to "production",
            "sdkConfig" to sdkConfig,
        )
        sdk.init(initConfig)

        progress("Generating core token")
        val ttl = draft.tokenTtlSeconds.toLongOrNull() ?: 86_400L
        val token = sdk.generateCoreToken(
            CoreTokenRequest(
                userId = draft.userId,
                secret = draft.tokenSecret,
                issuer = draft.tokenIssuer,
                ttlSecs = ttl,
                deviceId = "android-example",
                tenantId = draft.tenantId,
            ),
        ).token

        progress("Logging in")
        sdk.login(
            mapOf(
                "userId" to draft.userId,
                "token" to token,
                "storeConfigJson" to "{}",
            ),
        )

        // 配置 SDK 托管的媒体磁盘缓存（LRU + 去重，核心已实现）：设根目录 + 上限，
        // 之后消息媒体经 media.cacheRemoteMedia 落到这里（离线可用、不重复下载）。
        runCatching {
            sdk.media.setMediaCacheRoot(mapOf("root" to "$dataDir/media-cache"))
            sdk.media.setMediaCacheMaxBytes(mapOf("maxBytes" to MEDIA_CACHE_MAX_BYTES))
        }

        client = sdk
        _currentUserId.value = draft.userId
        _isLoggedIn.value = true
        refreshConnectionState()
        return sdk
    }

    suspend fun logout() {
        val sdk = client ?: error("SDK client is not initialized")
        sdk.logout()
        _isLoggedIn.value = false
        _currentUserId.value = null
        refreshConnectionState()
    }

    suspend fun dispose() {
        val sdk = client ?: error("SDK client is not initialized")
        runCatching { sdk.dispose() }
        teardown()
    }

    suspend fun refreshConnectionState() {
        runCatching { client?.connection?.getConnectionState() }
            .getOrNull()
            ?.let { _connectionState.value = it }
    }

    private fun subscribeEvents(sdk: FlareImClient) {
        subscriptions += sdk.events.onViewUpdated { update -> onViewUpdate?.invoke(update) }
        subscriptions += sdk.events.onConnectSuccess { event ->
            _connectionState.value = ConnectionState.CONNECTED
            appendEvent("connection", event.name.name, event.reason ?: "")
        }
        subscriptions += sdk.events.onConnectReady { event ->
            _connectionState.value = ConnectionState.READY
            appendEvent("connection", event.name.name, event.reason ?: "")
        }
        subscriptions += sdk.events.onDisconnected { event ->
            _connectionState.value = ConnectionState.DISCONNECTED
            appendEvent("connection", event.name.name, event.reason ?: "")
        }
        subscriptions += sdk.events.onLoginSucceeded { event ->
            appendEvent("lifecycle", event.name.name, event.operation)
        }
    }

    private fun teardown() {
        subscriptions.forEach { runCatching { it.unsubscribe() } }
        subscriptions.clear()
        client = null
        _isLoggedIn.value = false
        _currentUserId.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun appendEvent(domain: String, name: String, detail: String) {
        _eventLog.update { (it + EventLogEntry(domain, name, detail)).takeLast(200) }
    }

    companion object {
        /** 媒体磁盘缓存默认上限 256MB（对齐主流 IM；可在设置页调整）。 */
        const val MEDIA_CACHE_MAX_BYTES = 256L * 1024 * 1024
    }
}
