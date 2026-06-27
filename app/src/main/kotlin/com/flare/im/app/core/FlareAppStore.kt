package com.flare.im.app.core

import com.flare.im.app.core.data.AppEnvironment
import com.flare.im.app.core.data.ViewDataRepository
import com.flare.im.app.core.domain.AppSection
import com.flare.im.app.core.domain.RuntimeStatus
import com.flare.im.app.core.session.AppLifecycle
import com.flare.im.app.core.session.AppSession
import com.flare.im.app.features.auth.AuthViewModel
import com.flare.im.app.features.messaging.MessagingViewModel
import com.flare.im.app.features.search.SearchViewModel
import com.flare.im.app.features.sdklab.SdkLabViewModel
import com.flare.im.app.features.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 组合根 / 协调器（对应 iOS `FlareAppStore`）：装配 Core（session/repository/environment），
 * 持有跨切面登录/登出/释放编排（[AppLifecycle]），并把 session 的 onViewUpdate 接线到 repository。
 * 特性 ViewModel 在 Phase 2+ 装配进来。
 */
class FlareAppStore(
    private val dataDir: String,
    private val scope: CoroutineScope,
) : AppLifecycle {
    val session = AppSession()
    val repository = ViewDataRepository()
    val environment = AppEnvironment()

    val messagingViewModel = MessagingViewModel(session, repository, environment, scope)
    val sdkLabViewModel = SdkLabViewModel(session, environment, scope)
    val searchViewModel = SearchViewModel(session, environment, scope)
    val authViewModel = AuthViewModel(environment, scope)
    val settingsViewModel = SettingsViewModel(session, environment, sdkLabViewModel, scope)

    init {
        // 反应式接线：core 推 ViewUpdate → 重投影本地视图（在 scope 上 re-fetch）。
        session.onViewUpdate = { update ->
            scope.launch { session.client?.let { repository.apply(it, update) } }
        }
        session.onMessageSendFailed = { key -> messagingViewModel.markSendFailed(key) }
        repository.onLog = { operation, detail -> environment.appendLab(operation, "ok", detail) }
        // 装配后回填生命周期（破 store↔VM 环）。
        messagingViewModel.bind(this)
        sdkLabViewModel.bind(this)
        authViewModel.bind(this)
        settingsViewModel.bind(this)
    }

    override suspend fun login() {
        environment.run("login") {
            session.start(environment.loginDraft.value, dataDir) { stage ->
                environment.setRuntimeStatus(RuntimeStatus.Loading(stage))
            }
            environment.setSection(AppSection.Conversations)
            environment.setRuntimeStatus(RuntimeStatus.Ready)
            session.client?.let { repository.openConversationList(it, "login") }
        }
    }

    override suspend fun logout() {
        environment.run("logout") {
            session.logout()
            clearLocalData()
        }
    }

    override suspend fun dispose() {
        environment.run("dispose") {
            session.dispose()
            clearLocalData()
        }
    }

    private fun clearLocalData() {
        environment.setSelectedConversationId(null)
        repository.reset()
        environment.setRuntimeStatus(RuntimeStatus.Idle)
    }
}
