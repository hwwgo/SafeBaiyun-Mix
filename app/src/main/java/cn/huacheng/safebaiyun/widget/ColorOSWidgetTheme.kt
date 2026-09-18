package cn.huacheng.safebaiyun.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.isSystemInDarkTheme
import androidx.glance.color.ColorProvider
import androidx.glance.color.ColorProviders
import cn.huacheng.safebaiyun.theme.ColorOSError
import cn.huacheng.safebaiyun.theme.ColorOSPrimary
import cn.huacheng.safebaiyun.theme.ColorOSSecondary
import cn.huacheng.safebaiyun.theme.ColorOSTertiary

/**
 * 桌面小部件的 ColorOS 风格主题包装器
 *
 * 使用主界面的 ColorOS 色系，浅色/深色自动适配，与 App 内视觉统一。
 */
@Composable
fun ColorOSGlanceTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val colors = if (isDark) darkColorProviders() else lightColorProviders()
    GlanceTheme(colors = colors, content = content)
}

/** 浅色模式 */
@Composable
private fun lightColorProviders() = ColorProviders(
    primary = ColorProvider(ColorOSPrimary),
    onPrimary = ColorProvider(Color.White),
    primaryContainer = ColorProvider(ColorOSPrimary.copy(alpha = 0.12f)),
    onPrimaryContainer = ColorProvider(ColorOSPrimary),
    secondary = ColorProvider(ColorOSSecondary),
    onSecondary = ColorProvider(Color.White),
    tertiary = ColorProvider(ColorOSTertiary),
    onTertiary = ColorProvider(Color.White),
    error = ColorProvider(ColorOSError),
    onError = ColorProvider(Color.White),
    background = ColorProvider(Color(0xFFFFFFFF)),
    onBackground = ColorProvider(Color(0xFF1A1A1A)),
    surface = ColorProvider(Color(0xFFFFFFFF)),
    onSurface = ColorProvider(Color(0xFF1A1A1A)),
    surfaceVariant = ColorProvider(Color(0xFFF2F2F2)),
    onSurfaceVariant = ColorProvider(Color(0xFF666666)),
    outline = ColorProvider(Color(0xFFD0D0D0)),
)

/** 深色模式 */
@Composable
private fun darkColorProviders() = ColorProviders(
    primary = ColorProvider(ColorOSPrimary),
    onPrimary = ColorProvider(Color.White),
    primaryContainer = ColorProvider(ColorOSPrimary.copy(alpha = 0.25f)),
    onPrimaryContainer = ColorProvider(ColorOSPrimary),
    secondary = ColorProvider(ColorOSSecondary),
    onSecondary = ColorProvider(Color.White),
    tertiary = ColorProvider(ColorOSTertiary),
    onTertiary = ColorProvider(Color.White),
    error = ColorProvider(ColorOSError),
    onError = ColorProvider(Color.White),
    background = ColorProvider(Color(0xFF121212)),
    onBackground = ColorProvider(Color.White),
    surface = ColorProvider(Color(0xFF1E1E1E)),
    onSurface = ColorProvider(Color.White),
    surfaceVariant = ColorProvider(Color(0xFF2A2A2A)),
    onSurfaceVariant = ColorProvider(Color(0xFFB0B0B0)),
    outline = ColorProvider(Color(0xFF444444)),
)
