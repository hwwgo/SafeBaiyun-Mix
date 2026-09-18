package cn.huacheng.safebaiyun.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import cn.huacheng.safebaiyun.theme.ColorOSError
import cn.huacheng.safebaiyun.theme.ColorOSPrimary
import cn.huacheng.safebaiyun.theme.ColorOSSecondary
import cn.huacheng.safebaiyun.theme.ColorOSTertiary

// ============================================================
//  桌面部件 ColorOS 配色
//
//  Glance 的 ColorProvider 需要 day/night 两色，系统自动根据
//  深浅模式挑选。这里定义所有 widget 要用的颜色常量。
// ============================================================

// -------- 品牌色（深浅模式一致） --------
val WidgetPrimary = ColorProvider(day = ColorOSPrimary, night = ColorOSPrimary)
val WidgetOnPrimary = ColorProvider(day = Color.White, night = Color.White)
val WidgetSecondary = ColorProvider(day = ColorOSSecondary, night = ColorOSSecondary)
val WidgetTertiary = ColorProvider(day = ColorOSTertiary, night = ColorOSTertiary)
val WidgetError = ColorProvider(day = ColorOSError, night = ColorOSError)

// -------- 表面与文字（深浅模式区分） --------
// 卡片背景
val WidgetSurface = ColorProvider(
    day = Color(0xFFFFFFFF),
    night = Color(0xFF1E1E1E)
)

// 卡片主文字
val WidgetOnSurface = ColorProvider(
    day = Color(0xFF1A1A1A),
    night = Color(0xFFFFFFFF)
)

// 卡片次要背景（未激活按钮）
val WidgetSurfaceVariant = ColorProvider(
    day = Color(0xFFF2F2F2),
    night = Color(0xFF2A2A2A)
)

// 卡片次要文字
val WidgetOnSurfaceVariant = ColorProvider(
    day = Color(0xFF666666),
    night = Color(0xFFB0B0B0)
)
