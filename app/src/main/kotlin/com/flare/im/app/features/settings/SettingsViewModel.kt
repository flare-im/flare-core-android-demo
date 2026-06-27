package com.flare.im.app.features.settings

import com.flare.im.api.ConnectionState
import com.flare.im.app.core.data.AppEnvironment
import com.flare.im.app.core.domain.LoginDraft
import com.flare.im.app.core.domain.ThemeChoice
import com.flare.im.app.core.session.AppLifecycle
import com.flare.im.app.core.session.AppSession
import com.flare.im.app.features.sdklab.SdkLabViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 设置特性 ViewModel：主题/登录默认值 + 会话态 + 诊断/登出/释放。 */
class SettingsViewModel(
    private val session: AppSession,
    private val environment: AppEnvironment,
    private val sdkLab: SdkLabViewModel,
    private val scope: CoroutineScope,
) {
    private var lifecycle: AppLifecycle? = null
    fun bind(lifecycle: AppLifecycle) { this.lifecycle = lifecycle }

    val theme: StateFlow<ThemeChoice> = environment.theme
    val loginDraft: StateFlow<LoginDraft> = environment.loginDraft
    val currentUserId: StateFlow<String?> = session.currentUserId
    val connectionState: StateFlow<ConnectionState> = session.connectionState

    fun setTheme(value: ThemeChoice) = environment.setTheme(value)
    fun updateDraft(transform: (LoginDraft) -> LoginDraft) = environment.updateLoginDraft(transform)

    fun refreshDiagnostics() { sdkLab.refreshDiagnostics() }
    fun logout() = scope.launch { lifecycle?.logout() }
    fun dispose() = scope.launch { lifecycle?.dispose() }

    // ---- 媒体缓存管理（用 SDK 托管的磁盘缓存：用量 / 上限 / 清空）----
    private val _cacheStats = MutableStateFlow<String?>(null)
    val cacheStats: StateFlow<String?> = _cacheStats.asStateFlow()

    fun refreshCacheStats() = scope.launch { session.client?.let { loadCacheStats(it) } }

    fun setCacheMaxBytes(bytes: Long) = scope.launch {
        val sdk = session.client ?: return@launch
        runCatching { sdk.media.setMediaCacheMaxBytes(mapOf("maxBytes" to bytes)) }
        loadCacheStats(sdk)
    }

    fun clearCache() = scope.launch {
        val sdk = session.client ?: return@launch
        runCatching { sdk.media.clearMediaCache() }
        loadCacheStats(sdk)
    }

    private suspend fun loadCacheStats(sdk: com.flare.im.api.FlareImClient) {
        runCatching { sdk.media.getMediaCacheStats() }.getOrNull()?.let { _cacheStats.value = formatCacheStats(it) }
    }

    private fun formatCacheStats(stats: Map<String, Any?>): String {
        fun num(vararg keys: String): Long? {
            for (k in keys) (stats[k] as? Number)?.let { return it.toLong() }
            return null
        }
        fun mb(b: Long) = "%.1f MB".format(b / 1_048_576.0)
        val used = num("usedBytes", "totalBytes", "sizeBytes", "bytes")
        val max = num("maxBytes", "limitBytes", "capacityBytes")
        val count = num("entryCount", "fileCount", "count", "entries")
        return buildString {
            append(used?.let { mb(it) } ?: "—")
            max?.let { append(" / ${mb(it)}") }
            count?.let { append(" · $it files") }
        }
    }
}
