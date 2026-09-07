package cn.huacheng.safebaiyun.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================
//  ColorOS 16 光场设计配色方案
// ============================================================

private val ColorOSLightColorScheme = lightColorScheme(
    // 主色调
    primary = ColorOSPrimary,
    onPrimary = Color.White,
    primaryContainer = ColorOSPrimaryLight,
    onPrimaryContainer = Color.White,

    // 辅助色
    secondary = ColorOSSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFF1565C0),

    // 第三色
    tertiary = ColorOSTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F5E9),
    onTertiaryContainer = Color(0xFF2E7D32),

    // 背景与卡片
    background = ColorOSBackgroundLight,
    onBackground = ColorOSTextPrimaryLight,
    surface = ColorOSSurfaceLight,
    onSurface = ColorOSTextPrimaryLight,
    surfaceVariant = ColorOSSurfaceVariantLight,
    onSurfaceVariant = ColorOSTextSecondaryLight,

    // 轮廓
    outline = Color(0xFFC4C6D0),
    outlineVariant = Color(0xFFE0E1E6),

    // 功能色
    error = ColorOSError,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFC62828),

    // 反色
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = ColorOSPrimaryLight,

    // 表面色调
    surfaceTint = ColorOSPrimary,
    scrim = Color.Black,
)

private val ColorOSDarkColorScheme = darkColorScheme(
    // 主色调
    primary = ColorOSPrimaryLight,
    onPrimary = Color(0xFF1A237E),
    primaryContainer = ColorOSPrimaryDark,
    onPrimaryContainer = Color.White,

    // 辅助色
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = Color.White,

    // 第三色
    tertiary = Color(0xFFA5D6A7),
    onTertiary = Color(0xFF1B5E20),
    tertiaryContainer = Color(0xFF2E7D32),
    onTertiaryContainer = Color.White,

    // 背景与卡片
    background = ColorOSBackgroundDark,
    onBackground = ColorOSTextPrimaryDark,
    surface = ColorOSSurfaceDark,
    onSurface = ColorOSTextPrimaryDark,
    surfaceVariant = ColorOSSurfaceVariantDark,
    onSurfaceVariant = ColorOSTextSecondaryDark,

    // 轮廓
    outline = Color(0xFF5A5C66),
    outlineVariant = Color(0xFF3A3B42),

    // 功能色
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF4A0000),
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = Color.White,

    // 反色
    inverseSurface = Color(0xFFE4E4EC),
    inverseOnSurface = Color(0xFF1A1B20),
    inversePrimary = ColorOSPrimary,

    // 表面色调
    surfaceTint = ColorOSPrimaryLight,
    scrim = Color.Black,
)

@Composable
fun SafeBaiyunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // ColorOS 16 默认启用动态颜色（Android 12+）
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 动态颜色优先（Android 12+）
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ColorOSDarkColorScheme
        else -> ColorOSLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // ColorOS 16 风格：状态栏透明，与背景融合
            window.statusBarColor = Color.Transparent.toArgb()
            // 导航栏也透明
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
