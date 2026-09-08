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

    // 应用字体缩放
    ApplyFontScale {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
