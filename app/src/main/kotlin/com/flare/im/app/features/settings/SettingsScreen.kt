package com.flare.im.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flare.im.app.R
import com.flare.im.app.core.FlareAppStore
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.ThemeChoice
import com.flare.im.app.features.shell.SectionTitle

/** 设置屏：主题 + 会话态 + 诊断/登出/释放。 */
@Composable
fun SettingsScreen(store: FlareAppStore) {
    val tk = FlareTheme.tokens
    val colors = FlareTheme.colors
    val vm = store.settingsViewModel
    val theme by vm.theme.collectAsState()
    val user by vm.currentUserId.collectAsState()
    val conn by vm.connectionState.collectAsState()
    Column(Modifier.fillMaxSize().padding(tk.lg).verticalScroll(rememberScrollState())) {
        SectionTitle(stringResource(R.string.nav_settings))
        Text(stringResource(R.string.settings_appearance), style = FlareTheme.type.headline, color = colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(tk.sm), modifier = Modifier.padding(vertical = tk.sm)) {
            ThemeChoice.entries.forEach { t ->
                FilterChip(theme == t, { vm.setTheme(t) }, { Text(t.name) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.brandSoft, selectedLabelColor = colors.brand))
            }
        }
        Spacer(Modifier.height(tk.md))
        Text(stringResource(R.string.settings_session), style = FlareTheme.type.headline, color = colors.textPrimary)
        Text("User: ${user ?: "-"}", style = FlareTheme.type.callout, color = colors.textSecondary)
        Text("Connection: ${conn.name}", style = FlareTheme.type.callout, color = colors.textSecondary)
        Spacer(Modifier.height(tk.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(tk.sm)) {
            com.flare.im.ui.Button(label = stringResource(R.string.settings_refresh_diagnostics), variant = com.flare.im.ui.FlareButtonVariant.Secondary, onClick = { vm.refreshDiagnostics() })
            com.flare.im.ui.Button(label = stringResource(R.string.action_logout), variant = com.flare.im.ui.FlareButtonVariant.Secondary, onClick = { vm.logout() })
            com.flare.im.ui.Button(label = stringResource(R.string.settings_dispose), variant = com.flare.im.ui.FlareButtonVariant.Secondary, onClick = { vm.dispose() })
        }

        Spacer(Modifier.height(tk.md))
        Text(stringResource(R.string.settings_media_cache), style = FlareTheme.type.headline, color = colors.textPrimary)
        val cacheStats by vm.cacheStats.collectAsState()
        LaunchedEffect(Unit) { vm.refreshCacheStats() }
        Text(
            stringResource(R.string.settings_cache_usage, cacheStats ?: "—"),
            style = FlareTheme.type.callout, color = colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(tk.sm), modifier = Modifier.padding(vertical = tk.sm)) {
            listOf(128L, 256L, 512L).forEach { mb ->
                com.flare.im.ui.Button(label = "${mb}MB", variant = com.flare.im.ui.FlareButtonVariant.Secondary, onClick = { vm.setCacheMaxBytes(mb * 1024 * 1024) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(tk.sm)) {
            com.flare.im.ui.Button(label = stringResource(R.string.settings_cache_refresh), variant = com.flare.im.ui.FlareButtonVariant.Secondary, onClick = { vm.refreshCacheStats() })
            com.flare.im.ui.Button(label = stringResource(R.string.settings_cache_clear), variant = com.flare.im.ui.FlareButtonVariant.Secondary, onClick = { vm.clearCache() })
        }
    }
}
