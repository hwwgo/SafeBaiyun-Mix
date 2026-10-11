package cn.huacheng.safebaiyun.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import cn.huacheng.safebaiyun.util.ConfigManager

// ============================================================
//  ColorOS 16 光场设计配色
//  灵感来源：自然光影、微光轮廓、渐变模糊
// ============================================================

// ---------- 主色调（光场蓝紫渐变） ----------
val ColorOSPrimary = Color(0xFF6B7CFF)        // 光场主色 - 蓝紫
val ColorOSPrimaryLight = Color(0xFF9D8CFF)   // 主色亮部
val ColorOSPrimaryDark = Color(0xFF4A5CE4)    // 主色暗部

// ---------- 辅助色 ----------
val ColorOSSecondary = Color(0xFF64B5F6)      // 天蓝辅助
val ColorOSTertiary = Color(0xFF81C784)       // 清新绿

// ---------- 光场特效色 ----------
val ColorOSGlow = Color(0x40FFFFFF)           // 微光光晕 (25% 白)
val ColorOSGlowStrong = Color(0x66FFFFFF)     // 强光晕 (40% 白)
val ColorOSShadow = Color(0x1A000000)         // 柔和阴影 (10% 黑)

// ---------- 解锁浮层遮罩（半透明，统一桌面快捷方式与小部件） ----------
// 约 25% 黑，覆盖在透明窗口之上，桌面/壁纸透出约 75%，既能看清「正在解锁」，
// 又不会因为全黑/全白而遮挡后方内容。快捷方式与小部件共用此值，保证两者背景一致。
val ColorOSScrim = Color(0x40000000)

// ---------- 背景色（毛玻璃质感） ----------
val ColorOSBackgroundLight = Color(0xFFF8F9FE)      // 浅色背景
val ColorOSSurfaceLight = Color(0xFFFFFFFF)         // 浅色卡片
val ColorOSSurfaceVariantLight = Color(0xFFF0F1F9)  // 浅色变体

val ColorOSBackgroundDark = Color(0xFF121318)       // 深色背景
val ColorOSSurfaceDark = Color(0xFF1C1D24)          // 深色卡片
val ColorOSSurfaceVariantDark = Color(0xFF25262E)   // 深色变体

// ---------- 文字色 ----------
val ColorOSTextPrimaryLight = Color(0xFF1A1B20)
val ColorOSTextSecondaryLight = Color(0xFF5A5C66)
val ColorOSTextTertiaryLight = Color(0xFF8A8C96)

val ColorOSTextPrimaryDark = Color(0xFFE4E4EC)
val ColorOSTextSecondaryDark = Color(0xFFB0B2BC)
val ColorOSTextTertiaryDark = Color(0xFF7E8089)

// ---------- 功能色 ----------
val ColorOSSuccess = Color(0xFF4CAF50)
val ColorOSWarning = Color(0xFFFFB74D)
val ColorOSError = Color(0xFFE57373)
val ColorOSInfo = Color(0xFF64B5F6)

// ---------- 渐变色定义 ----------
val ColorOSGradientStart = Color(0xFF6B7CFF)
val ColorOSGradientEnd = Color(0xFF9D8CFF)

// ---------- 兼容旧代码 ----------
val Purple80 = ColorOSPrimaryLight
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = ColorOSPrimary
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ============================================================
//  字体排版
// ============================================================
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

// ============================================================
//  大字体模式支持（根据设置调整字体缩放比例）
// ============================================================
object FontScale {

    fun getFontScale(): Float {
        return if (ConfigManager.isLargeFont()) 1.3f else 1.0f
    }

    fun getLineHeightScale(): Float {
        return if (ConfigManager.isLargeFont()) 1.2f else 1.0f
    }
}

@Composable
fun ApplyFontScale(content: @Composable () -> Unit) {
    val fontScale = FontScale.getFontScale()
    val currentDensity = LocalDensity.current

    val newDensity = remember(fontScale, currentDensity) {
        Density(currentDensity.density, fontScale)
    }

    CompositionLocalProvider(LocalDensity provides newDensity) {
        content()
    }
}

// ============================================================
//  Material 3 ColorScheme + 主题入口
// ============================================================
private val ColorOSLightColorScheme = lightColorScheme(
    primary = ColorOSPrimary,
    onPrimary = Color.White,
    primaryContainer = ColorOSPrimaryLight,
    onPrimaryContainer = Color.White,
    secondary = ColorOSSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFF1565C0),
    tertiary = ColorOSTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F5E9),
    onTertiaryContainer = Color(0xFF2E7D32),
    background = ColorOSBackgroundLight,
    onBackground = ColorOSTextPrimaryLight,
    surface = ColorOSSurfaceLight,
    onSurface = ColorOSTextPrimaryLight,
    surfaceVariant = ColorOSSurfaceVariantLight,
    onSurfaceVariant = ColorOSTextSecondaryLight,
    outline = Color(0xFFC4C6D0),
    outlineVariant = Color(0xFFE0E1E6),
    error = ColorOSError,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFC62828),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = ColorOSPrimaryLight,
    surfaceTint = ColorOSPrimary,
    scrim = Color.Black,
)

private val ColorOSDarkColorScheme = darkColorScheme(
    primary = ColorOSPrimaryLight,
    onPrimary = Color(0xFF1A237E),
    primaryContainer = ColorOSPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFA5D6A7),
    onTertiary = Color(0xFF1B5E20),
    tertiaryContainer = Color(0xFF2E7D32),
    onTertiaryContainer = Color.White,
    background = ColorOSBackgroundDark,
    onBackground = ColorOSTextPrimaryDark,
    surface = ColorOSSurfaceDark,
    onSurface = ColorOSTextPrimaryDark,
    surfaceVariant = ColorOSSurfaceVariantDark,
    onSurfaceVariant = ColorOSTextSecondaryDark,
    outline = Color(0xFF5A5C66),
    outlineVariant = Color(0xFF3A3B42),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF4A0000),
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = Color.White,
    inverseSurface = Color(0xFFE4E4EC),
    inverseOnSurface = Color(0xFF1A1B20),
    inversePrimary = ColorOSPrimary,
    surfaceTint = ColorOSPrimaryLight,
    scrim = Color.Black,
)

@Composable
fun SafeBaiyunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
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
            if (darkTheme) {
                window.statusBarColor = ColorOSBackgroundDark.toArgb()
                window.navigationBarColor = ColorOSBackgroundDark.toArgb()
            } else {
                window.statusBarColor = ColorOSBackgroundLight.toArgb()
                window.navigationBarColor = ColorOSBackgroundLight.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    ApplyFontScale {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
