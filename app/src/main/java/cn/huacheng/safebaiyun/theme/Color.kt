package cn.huacheng.safebaiyun.theme

import androidx.compose.ui.graphics.Color

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
