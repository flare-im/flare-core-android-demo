package com.flare.im.app.features.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flare.im.model.entity.NetworkInterfaceKind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flare.im.app.core.FlareAppStore
import com.flare.im.app.core.designsystem.FlareAppTheme
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.AppSection
import com.flare.im.app.core.domain.ThemeChoice
import com.flare.im.app.features.auth.LoginScreen
import com.flare.im.app.features.messaging.chat.ChatScreen
import com.flare.im.app.features.messaging.conversationlist.ConversationListScreen
import com.flare.im.app.features.sdklab.SdkLabScreen
import com.flare.im.app.features.search.SearchScreen
import com.flare.im.app.features.settings.SettingsScreen

/** App 根：主题 + 登录路由 + 自适应 workbench 导航。 */
@Composable
fun FlareApp(store: FlareAppStore) {
    val theme by store.environment.theme.collectAsState()
    val loggedIn by store.session.isLoggedIn.collectAsState()
    val dark = when (theme) {
        ThemeChoice.System -> null
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    // 热启动：有会话档案则本地出图直进工作台；登录页只在无档案/恢复失败时出现（不闪屏）。
    var resumeAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!resumeAttempted) {
            store.resumeSavedSession()
            resumeAttempted = true
        }
    }
    // 平台原始信号 → core：网络变化（主动重连）+ 前后台（心跳降配/前台收敛）。策略全在 core。
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(loggedIn) {
        if (!loggedIn) return@DisposableEffect onDispose {}
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                store.notifyNetworkChange(true, currentInterfaceKind(connectivity), "network_available")
            }

            override fun onLost(network: Network) {
                store.notifyNetworkChange(false, NetworkInterfaceKind.UNKNOWN, "network_lost")
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                store.notifyNetworkChange(true, interfaceKindOf(capabilities), "capabilities_changed")
            }
        }
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(loggedIn, lifecycleOwner) {
        if (!loggedIn) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> store.setAppForeground(true)
                Lifecycle.Event.ON_STOP -> store.setAppForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    FlareAppTheme(dark = dark) {
        val colors = FlareTheme.colors
        Box(Modifier.fillMaxSize().background(colors.background)) {
            when {
                loggedIn -> WorkbenchScreen(store)
                resumeAttempted -> LoginScreen(store)
                else -> Unit
            }
            val busy by store.environment.isBusy.collectAsState()
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.08f)), Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
        }
    }
}

private fun interfaceKindOf(capabilities: NetworkCapabilities?): NetworkInterfaceKind = when {
    capabilities == null -> NetworkInterfaceKind.UNKNOWN
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkInterfaceKind.WIFI
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkInterfaceKind.CELLULAR
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkInterfaceKind.ETHERNET
    else -> NetworkInterfaceKind.OTHER
}

private fun currentInterfaceKind(connectivity: ConnectivityManager?): NetworkInterfaceKind {
    val network = connectivity?.activeNetwork ?: return NetworkInterfaceKind.UNKNOWN
    return interfaceKindOf(connectivity.getNetworkCapabilities(network))
}

@Composable
private fun WorkbenchScreen(store: FlareAppStore) {
    val colors = FlareTheme.colors
    val section by store.environment.section.collectAsState()
    val selectedId by store.environment.selectedConversationId.collectAsState()
    val showChat = section == AppSection.Conversations && selectedId != null

    Scaffold(
        containerColor = colors.background,
        bottomBar = { if (!showChat) FlareNavBar(store, section) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (section) {
                AppSection.Conversations -> if (showChat) ChatScreen(store) else ConversationListScreen(store)
                AppSection.Search -> SearchScreen(store)
                AppSection.SdkLab -> SdkLabScreen(store)
                AppSection.Settings -> SettingsScreen(store)
            }
        }
    }
}

@Composable
private fun FlareNavBar(store: FlareAppStore, section: AppSection) {
    val colors = FlareTheme.colors
    NavigationBar(containerColor = colors.surface) {
        AppSection.entries.forEach { item ->
            NavigationBarItem(
                selected = section == item,
                onClick = { store.environment.setSection(item) },
                icon = { Box(Modifier.size(6.dp).clip(CircleShape).background(if (section == item) colors.brand else colors.textTertiary)) },
                label = { Text(stringResource(item.titleRes), style = FlareTheme.type.caption) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = colors.brandSoft),
            )
        }
    }
}
