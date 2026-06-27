package com.flare.im.app.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flare.im.app.R
import com.flare.im.app.core.FlareAppStore
import com.flare.im.app.core.designsystem.FlareTheme

/** 登录屏：用户 ID + 服务地址 → AuthViewModel.submit。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(store: FlareAppStore) {
    val colors = FlareTheme.colors
    val tk = FlareTheme.tokens
    val auth = store.authViewModel
    val draft by auth.loginDraft.collectAsState()
    val validation by auth.validationMessage.collectAsState()
    val error by auth.lastError.collectAsState()
    val busy by auth.isBusy.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(tk.xxl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.brand_eyebrow), style = FlareTheme.type.eyebrow, color = colors.textSecondary)
        Spacer(Modifier.height(tk.xs))
        Text(stringResource(R.string.brand_name), style = FlareTheme.type.largeTitle, color = colors.brand)
        Text(stringResource(R.string.brand_tagline), style = FlareTheme.type.callout, color = colors.textSecondary)
        Spacer(Modifier.height(tk.xxl))
        Text(stringResource(R.string.auth_welcome), style = FlareTheme.type.title, color = colors.textPrimary)
        Text(stringResource(R.string.auth_enter_id), style = FlareTheme.type.body, color = colors.textSecondary)
        Spacer(Modifier.height(tk.lg))
        OutlinedTextField(
            value = draft.userId,
            onValueChange = { v -> auth.updateDraft { it.copy(userId = v) }; auth.clearValidation() },
            label = { Text(stringResource(R.string.auth_user_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(tk.sm))
        OutlinedTextField(
            value = draft.visibleServerAddress,
            onValueChange = { v -> auth.updateDraft { it.withVisibleServerAddress(v) } },
            label = { Text(stringResource(R.string.auth_server_optional)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        validation?.let { Text(it, color = colors.danger, style = FlareTheme.type.caption, modifier = Modifier.padding(top = tk.sm)) }
        error?.let { Text(it, color = colors.danger, style = FlareTheme.type.caption, modifier = Modifier.padding(top = tk.sm)) }
        Spacer(Modifier.height(tk.lg))
        Button(
            onClick = { auth.submit() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brand),
            shape = tk.radiusMedium,
        ) {
            Text(stringResource(if (busy) R.string.auth_signing_in else R.string.auth_sign_in), color = colors.outgoingText)
        }
    }
}
