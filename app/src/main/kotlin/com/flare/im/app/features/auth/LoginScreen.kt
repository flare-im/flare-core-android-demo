package com.flare.im.app.features.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flare.im.app.R
import com.flare.im.app.core.FlareAppStore
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.core.domain.LoginTransportMode

// 登录屏：版式复刻 iOS LoginView —— 渐变品牌头(网格背景 + logo) + 白底表单
// (胶囊输入 + 传输模式菜单 + 服务地址 + 错误横幅 + 渐变登录键)。
// 状态/绑定沿用 AuthViewModel；仅重做视觉。

private val HeaderTop = Color(0xFF6419C2)
private val HeaderMid = Color(0xFF7D3BED)
private val HeaderBottom = Color(0xFF6466F0)
private val ButtonEnd = Color(0xFF8C29EB)
private val FieldBg = Color(0xFFF2F2F5)

@Composable
fun LoginScreen(store: FlareAppStore) {
    val auth = store.authViewModel
    val draft by auth.loginDraft.collectAsState()
    val validation by auth.validationMessage.collectAsState()
    val error by auth.lastError.collectAsState()
    val busy by auth.isBusy.collectAsState()

    // 紫色品牌头铺到状态栏下：状态栏图标改浅色(白)，离开登录页恢复。
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BrandHeader()
            Column(
                Modifier.widthIn(max = 430.dp).align(Alignment.CenterHorizontally)
                    .fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 20.dp).padding(top = 30.dp, bottom = 42.dp),
            ) {
                LoginForm(store, draft, validation, error, busy)
            }
        }
        if (busy) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.06f)), Alignment.Center) {
                CircularProgressIndicator(color = HeaderMid)
            }
        }
    }
}

/** 渐变品牌头：对角渐变 + 细网格 + 白色圆角 logo + 品牌名/标语。 */
@Composable
private fun BrandHeader() {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 280.dp)
            .background(Brush.linearGradient(listOf(HeaderTop, HeaderMid, HeaderBottom), start = Offset.Zero, end = Offset.Infinite)),
        Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val step = 40.dp.toPx()
            val line = Color.White.copy(alpha = 0.11f)
            var x = 0f
            while (x <= size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y <= size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        }
        Column(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(64.dp).shadow(16.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)).background(Color.White),
                Alignment.Center,
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = HeaderMid, modifier = Modifier.size(33.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.brand_name), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.brand_tagline), color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun LoginForm(
    store: FlareAppStore,
    draft: com.flare.im.app.core.domain.LoginDraft,
    validation: String?,
    error: String?,
    busy: Boolean,
) {
    val colors = FlareTheme.colors
    val auth = store.authViewModel

    Text(stringResource(R.string.auth_welcome), style = FlareTheme.type.title, color = colors.textPrimary)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.auth_enter_id), fontSize = 14.sp, color = colors.textSecondary)
    Spacer(Modifier.height(28.dp))

    LoginInputField(
        label = stringResource(R.string.auth_user_id),
        placeholder = stringResource(R.string.auth_user_id_placeholder),
        icon = Icons.Outlined.Person,
        value = draft.userId,
        onValueChange = { auth.updateDraft { d -> d.copy(userId = it) }; auth.clearValidation() },
    )
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.Info, null, tint = colors.brand, modifier = Modifier.size(14.dp).padding(top = 1.dp))
        Text(stringResource(R.string.auth_id_hint), fontSize = 12.sp, color = colors.textTertiary)
    }

    validation?.let {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(14.dp))
            Text(it, fontSize = 12.sp, color = colors.danger)
        }
    }

    Spacer(Modifier.height(22.dp))
    ServerConfigSection(store, draft)

    error?.let {
        Spacer(Modifier.height(16.dp))
        LoginErrorBanner(it)
    }

    Spacer(Modifier.height(24.dp))
    GradientSignInButton(busy = busy, enabled = !busy) { auth.submit() }

    Spacer(Modifier.height(20.dp))
    Text(
        stringResource(R.string.auth_footer),
        fontSize = 12.sp,
        color = colors.textTertiary,
        modifier = Modifier.fillMaxWidth(),
        style = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
    )
}

/** 服务地址区：标题 + 传输模式下拉 + 可见地址(+ Race 次地址)。 */
@Composable
private fun ServerConfigSection(store: FlareAppStore, draft: com.flare.im.app.core.domain.LoginDraft) {
    val colors = FlareTheme.colors
    val auth = store.authViewModel
    var menu by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.auth_server_optional), fontSize = 14.sp, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { menu = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(draft.transportMode.title, style = FlareTheme.type.callout, color = colors.textPrimary)
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                LoginTransportMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(mode.title) }, onClick = {
                        menu = false
                        auth.updateDraft { it.copy(transportMode = mode) }
                    })
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    LoginInputField(
        label = stringResource(R.string.auth_server_optional),
        placeholder = draft.visibleServerAddress,
        icon = Icons.Outlined.KeyboardArrowDown,
        value = draft.visibleServerAddress,
        onValueChange = { auth.updateDraft { d -> d.withVisibleServerAddress(it) } },
    )
    draft.secondaryServerAddress?.let { secondary ->
        Spacer(Modifier.height(8.dp))
        LoginInputField(
            label = "Fallback (WebSocket)",
            placeholder = secondary,
            icon = Icons.Outlined.KeyboardArrowDown,
            value = secondary,
            onValueChange = { auth.updateDraft { d -> d.withSecondaryServerAddress(it) } },
        )
    }
}

/** 胶囊输入：标签 + 行内图标 + 文本框（灰底圆角，无描边）。 */
@Composable
private fun LoginInputField(
    label: String,
    placeholder: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val colors = FlareTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 14.sp, color = colors.textPrimary)
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(FieldBg).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, style = FlareTheme.type.body, color = colors.textTertiary)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = FlareTheme.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.brand),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 渐变登录按钮（48 高，圆角；禁用降透明度）。 */
@Composable
private fun GradientSignInButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = FlareTheme.colors
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(colors.brand, ButtonEnd)))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.55f),
        Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Outlined.Login, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (busy) R.string.auth_signing_in else R.string.auth_sign_in),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 登录失败横幅：警告图标 + 标题 + 详情，danger 淡底圆角。 */
@Composable
private fun LoginErrorBanner(message: String) {
    val colors = FlareTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.danger.copy(alpha = 0.11f)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.auth_error_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text(message, fontSize = 12.sp, color = colors.textSecondary, maxLines = 3)
        }
    }
}
