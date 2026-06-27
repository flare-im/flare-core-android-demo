package com.flare.im.app.core.domain

import androidx.annotation.StringRes
import com.flare.im.app.R
import com.flare.im.app.core.designsystem.FlareTone

/** 顶层区段。 */
enum class AppSection(@StringRes val titleRes: Int) {
    Conversations(R.string.nav_messages),
    Search(R.string.nav_search),
    SdkLab(R.string.nav_sdk_status),
    Settings(R.string.nav_settings),
}

/** 会话列表过滤。 */
enum class ConversationFilter(@StringRes val titleRes: Int) {
    All(R.string.filter_all),
    Unread(R.string.filter_unread),
    Mentions(R.string.filter_mentions),
    Pinned(R.string.filter_pinned),
    Archived(R.string.filter_archived),
    Muted(R.string.filter_muted),
    Drafts(R.string.filter_drafts),
}

/** 主题选择。null=跟随系统。 */
enum class ThemeChoice { System, Light, Dark }

/** 起会话类型。 */
enum class StartConversationKind { Single, Group }

/**
 * 运行时状态，sealed 保留关联消息。
 * productLabel/tone/icon 提供产品化呈现。
 */
sealed interface RuntimeStatus {
    object Idle : RuntimeStatus
    data class Loading(val message: String) : RuntimeStatus
    object Ready : RuntimeStatus
    data class Offline(val reason: String) : RuntimeStatus
    data class Error(val message: String) : RuntimeStatus
    data class Unavailable(val message: String) : RuntimeStatus

    val isBlocking: Boolean
        get() = this is Error || this is Unavailable

    /** 用户可见状态文案的 string-res（连接态用）。 */
    @get:StringRes
    val productLabelRes: Int
        get() = when (this) {
            Idle -> R.string.status_not_connected
            is Loading -> R.string.status_connecting
            Ready -> R.string.status_online
            is Offline -> R.string.status_offline
            is Error -> R.string.status_needs_attention
            is Unavailable -> R.string.status_unavailable
        }

    val tone: FlareTone
        get() = when (this) {
            Idle -> FlareTone.Neutral
            is Loading -> FlareTone.Info
            Ready -> FlareTone.Success
            is Offline, is Unavailable -> FlareTone.Warning
            is Error -> FlareTone.Danger
        }
}

/** 传输模式；保留现有 AndroidTransportMode 行为。 */
enum class LoginTransportMode(val title: String) {
    WebSocket("WebSocket"),
    Quic("QUIC"),
    Race("QUIC Race"),
}

/** 登录默认值（与 LoginDraft 等价，含 transportConfig 生成）。 */
data class LoginDraft(
    val userId: String = "android-demo",
    val transportMode: LoginTransportMode = LoginTransportMode.WebSocket,
    val wsUrl: String = com.flare.im.app.BuildConfig.DEFAULT_WS_URL,
    val quicUrl: String = "quic://10.0.2.2:60052",
    val tlsCaCertPath: String = "",
    val tenantId: String = "0",
    val tokenSecret: String = "flare-im-dev-secret",
    val tokenIssuer: String = "flare-im-core",
    val tokenTtlSeconds: String = "3600",
    val dataSubfolder: String = "flare-core-android-app",
) {
    /** 当前传输模式下用户可编辑的服务地址。 */
    val visibleServerAddress: String
        get() = when (transportMode) {
            LoginTransportMode.WebSocket -> wsUrl
            LoginTransportMode.Quic, LoginTransportMode.Race -> quicUrl
        }

    fun withVisibleServerAddress(value: String): LoginDraft = when (transportMode) {
        LoginTransportMode.WebSocket -> copy(wsUrl = value)
        LoginTransportMode.Quic, LoginTransportMode.Race -> copy(quicUrl = value)
    }

    /** race 模式下的备用 WebSocket 地址（否则 null）。 */
    val secondaryServerAddress: String?
        get() = if (transportMode == LoginTransportMode.Race) wsUrl else null

    fun withSecondaryServerAddress(value: String): LoginDraft =
        if (transportMode == LoginTransportMode.Race) copy(wsUrl = value) else this

    /** 生成 SDK init 的 transport 配置（与现有 androidTransportConfig 等价，含 tenant/token 元信息留给调用方拼装）。 */
    fun transportConfig(): Map<String, Any> {
        val ws = wsUrl.trim()
        require(ws.isNotEmpty()) { "WebSocket URL is required" }
        val tls = tlsCaCertPath.trim().takeIf { it.isNotEmpty() }?.let { mapOf("tlsCaCertPath" to it) } ?: emptyMap()
        if (transportMode == LoginTransportMode.WebSocket) {
            return mapOf(
                "wsUrl" to ws,
                "transportPolicy" to "websocket_only",
                "defaultTransport" to "websocket",
            ) + tls
        }
        val quic = quicUrl.trim()
        require(quic.isNotEmpty()) { "QUIC URL is required for selected transport" }
        return when (transportMode) {
            LoginTransportMode.WebSocket -> error("unreachable")
            LoginTransportMode.Quic -> mapOf(
                "wsUrl" to ws, "quicUrl" to quic,
                "transportPolicy" to "auto", "defaultTransport" to "quic",
                "protocolRaceOrder" to listOf("quic"),
            ) + tls
            LoginTransportMode.Race -> mapOf(
                "wsUrl" to ws, "quicUrl" to quic,
                "transportPolicy" to "protocol_race", "defaultTransport" to "quic",
                "protocolRaceOrder" to listOf("quic", "websocket"),
            ) + tls
        }
    }
}

/** SDK Lab 操作记录。 */
data class LabResult(
    val operation: String,
    val status: String,
    val detail: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

/** 连接事件日志条目。 */
data class EventLogEntry(
    val domain: String,
    val name: String,
    val detail: String,
    val timestampMs: Long = System.currentTimeMillis(),
)
