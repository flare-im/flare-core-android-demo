package com.flare.im.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// FlareTheme — Compose 设计系统（品牌锁定，关闭 dynamic-color）。
// 颜色经 FlareColors（双主题），间距/圆角/字体经 FlareTokens（与主题无关）。
// 视图通过 `FlareTheme.colors` / `FlareTheme.tokens` 读取，替代散落的魔法数字。

/** 语义色板（light + dark 各一份）。 */
@Immutable
data class FlareColors(
    val brand: Color,
    val brandSoft: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val incomingBubble: Color,
    val outgoing: Color,
    val outgoingText: Color,
    val hairline: Color,
    val isDark: Boolean,
)

private val LightFlareColors = FlareColors(
    brand = Color(0xFF7D3BED),
    brandSoft = Color(0xFFF2EBFF),
    accent = Color(0xFF1A75D1),
    background = Color(0xFFF5F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF2F2F7),
    textPrimary = Color(0xFF121217),
    textSecondary = Color(0xFF6B7380),
    textTertiary = Color(0xFFA3A6B0),
    success = Color(0xFF21C45E),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFF04444),
    incomingBubble = Color(0xFFEDE6FF),
    outgoing = Color(0xFF7D3BED),
    outgoingText = Color(0xFFFFFFFF),
    hairline = Color(0x14000000),
    isDark = false,
)

private val DarkFlareColors = FlareColors(
    brand = Color(0xFF9D6BFF),
    brandSoft = Color(0xFF2A2140),
    accent = Color(0xFF3D90E0),
    background = Color(0xFF0E0E12),
    surface = Color(0xFF17171C),
    surfaceAlt = Color(0xFF202028),
    textPrimary = Color(0xFFF2F2F5),
    textSecondary = Color(0xFFA0A4AE),
    textTertiary = Color(0xFF6B6F7A),
    success = Color(0xFF34D777),
    warning = Color(0xFFF6B23C),
    danger = Color(0xFFF26666),
    incomingBubble = Color(0xFF262033),
    outgoing = Color(0xFF8A4BF0),
    outgoingText = Color(0xFFFFFFFF),
    hairline = Color(0x1FFFFFFF),
    isDark = true,
)

/** 运行时状态色调。 */
enum class FlareTone { Neutral, Info, Success, Warning, Danger }

fun FlareColors.color(tone: FlareTone): Color = when (tone) {
    FlareTone.Neutral -> textTertiary
    FlareTone.Info -> accent
    FlareTone.Success -> success
    FlareTone.Warning -> warning
    FlareTone.Danger -> danger
}

/** 间距 / 圆角 / 字体标尺 —— 与主题无关。 */
@Immutable
object FlareTokens {
    // Spacing：4pt 基准网格。
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp

    // Radius：4 档 + pill。
    val radiusSmall = RoundedCornerShape(6.dp)
    val radiusMedium = RoundedCornerShape(8.dp)
    val radiusLarge = RoundedCornerShape(12.dp)
    val radiusXl = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(999.dp)
}

/** 字体阶梯：系统 Roboto，精确字号/字重。 */
@Immutable
object FlareType {
    val largeTitle = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    val headline = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
    val callout = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
    val captionStrong = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
    // 签名样式：FLARE CORE 眉标（大写 + 字距）。
    val eyebrow = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
}

private val LocalFlareColors = staticCompositionLocalOf { LightFlareColors }

/** 在 Compose 树中读取 Flare 设计 token。 */
object FlareTheme {
    val colors: FlareColors
        @Composable @ReadOnlyComposable get() = LocalFlareColors.current
    val tokens: FlareTokens get() = FlareTokens
    val type: FlareType get() = FlareType
}

private fun FlareColors.toMaterialScheme() = if (isDark) {
    darkColorScheme(
        primary = brand, onPrimary = outgoingText, secondary = accent,
        background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary,
        surfaceVariant = surfaceAlt, error = danger,
    )
} else {
    lightColorScheme(
        primary = brand, onPrimary = outgoingText, secondary = accent,
        background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary,
        surfaceVariant = surfaceAlt, error = danger,
    )
}

private fun materialTypography() = Typography(
    titleLarge = FlareType.title,
    titleMedium = FlareType.headline,
    bodyLarge = FlareType.body,
    bodyMedium = FlareType.callout,
    labelSmall = FlareType.caption,
)

/**
 * App 主题。`dark` 为 null 时跟随系统。
 * dynamic-color 关闭以保留 Flare 品牌紫。
 */
@Composable
fun FlareAppTheme(
    dark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val isDark = dark ?: isSystemInDarkTheme()
    val colors = if (isDark) DarkFlareColors else LightFlareColors
    CompositionLocalProvider(LocalFlareColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = materialTypography(),
            content = content,
        )
    }
}
