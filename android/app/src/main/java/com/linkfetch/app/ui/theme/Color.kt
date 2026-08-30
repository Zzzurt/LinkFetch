package com.linkfetch.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------- 品牌蓝 ----------
val Blue300 = Color(0xFF93C5FD)
val Blue500 = Color(0xFF3B82F6)
val Blue600 = Color(0xFF2563EB)
val Blue700 = Color(0xFF1D4ED8)
val Blue800 = Color(0xFF1E40AF)
val Blue50 = Color(0xFFEFF6FF)

// ---------- Slate 中性灰（浅色模式文字 / 辅助） ----------
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)

// ---------- 深色模式（背景压深、三层对比） ----------
val DarkBackground = Color(0xFF0B1220)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceHigh = Color(0xFF273549)
val DarkOnSurface = Color(0xFFF1F5F9)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)

// ---------- 语义色 ----------
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFDC2626)
val ErrorRedDark = Color(0xFFF87171)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF7F1D1D)
val ErrorContainerDark = Color(0xFF7F1D1D)
val OnErrorContainerDark = Color(0xFFFECACA)

/** 平台色（深色模式取降饱和版本），用于筛选 Chip、强调等场景 */
fun platformAccent(platform: com.linkfetch.app.util.Platform, isDark: Boolean): Color =
    Color(if (isDark) platform.badgeColorDark else platform.badgeColor)
