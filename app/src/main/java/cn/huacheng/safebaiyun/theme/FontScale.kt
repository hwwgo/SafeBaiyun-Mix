package cn.huacheng.safebaiyun.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import cn.huacheng.safebaiyun.util.ConfigManager

/**
 * 大字体模式支持
 * 根据设置调整字体缩放比例
 */
object FontScale {

    /**
     * 获取字体缩放比例
     * 大字体模式：1.3f
     * 正常模式：1.0f
     */
    fun getFontScale(): Float {
        return if (ConfigManager.isLargeFont()) 1.3f else 1.0f
    }

    /**
     * 获取行高缩放比例
     * 大字体模式需要更大的行高
     */
    fun getLineHeightScale(): Float {
        return if (ConfigManager.isLargeFont()) 1.2f else 1.0f
    }
}

/**
 * 应用字体缩放
 * 在 Theme 中使用
 */
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
