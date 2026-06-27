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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/** App 根（对应 iOS FlareImRootView/RootWorkbenchView）：主题 + 登录路由 + 自适应 workbench 导航。 */
@Composable
fun FlareApp(store: FlareAppStore) {
    val theme by store.environment.theme.collectAsState()
    val loggedIn by store.session.isLoggedIn.collectAsState()
    val dark = when (theme) {
        ThemeChoice.System -> null
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    FlareAppTheme(dark = dark) {
        val colors = FlareTheme.colors
        Box(Modifier.fillMaxSize().background(colors.background)) {
            if (loggedIn) WorkbenchScreen(store) else LoginScreen(store)
            val busy by store.environment.isBusy.collectAsState()
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.08f)), Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
        }
    }
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
