package com.linkfetch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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

/** 平台色前景：亮底（微博橙/抖音青）用深字，暗底（小红书红/X 黑）用白字，保证 AA 对比 */
fun onPlatform(accent: Color): Color =
    if (accent.luminance() > 0.5f) Color(0xFF0F172A) else Color.White

/**
 * 次级文字语义色。
 *
 * 为什么需要单独一档：`onSurfaceVariant` 在深色下是 Slate400(#94A3B8)，压在自己的
 * `surface` 上够用（约 5.9:1），但压在 `surfaceVariant`(#273549) 这类更亮的容器上
 * 只剩约 4.9:1 —— 12sp 小字没有余量。此前 TypeTag 只能在组件里就地写一个
 * `if (isSystemInDarkTheme()) Slate300 else ...` 打补丁，那是体系缺一档的表现。
 * 补成正式语义色之后，凡是"压在容器上的小字"都有正规出口，不必再就地补丁。
 */
val TextMutedLight = Slate500
val TextMutedDark = Slate300

object TextColors {
    /** 说明文案、元信息等次级文字 */
    val muted: Color
        @Composable get() = if (isSystemInDarkTheme()) TextMutedDark else TextMutedLight
}

/**
 * 卡片描边色。
 *
 * 此前浅色模式下的卡片**完全没有描边** —— 白底卡片只靠 1dp elevation 阴影压在
 * Slate50(#F8FAFC) 页面底上，实测色差约 1.045:1；而深色侧一直靠 outlineVariant 描边分层。
 * 结果是两套主题用了两套不同的分层逻辑：浅色靠阴影、深色靠描边，浅色整屏"糊成一片"。
 *
 * ⚠️ 这不是对比度问题，是**边界感知**问题：大面积的 1.045:1 色差确实几乎不可见，
 * 但一条连续的 1px 线即使只有约 1.24:1，人眼对"边缘"的敏感度也远高于对"填充差"的敏感度。
 *
 * 为什么另开一档，而不是复用 outline / outlineVariant：
 * - `outline`（浅 Slate200 / 深 Slate600）在浅色侧同时是 `OutlinedTextField` 未聚焦态的
 *   默认边框色，不能动；深色侧它比卡片描边更亮，正好形成"输入框比卡片边界更清晰"的层级。
 * - `outlineVariant`（浅 Slate300 / 深 Slate700）是**分隔线**专用色，语义不同。
 * 所以卡片描边单独成档，取值与两者都错开（深色侧与 outlineVariant 同值，因为深色原有行为就是对的）。
 */
val CardStrokeLight = Slate200
val CardStrokeDark = Slate700

object StrokeColors {
    /** 卡片描边：浅色靠它补上缺失的边界，深色维持原有的分层方式 */
    val card: Color
        @Composable get() = if (isSystemInDarkTheme()) CardStrokeDark else CardStrokeLight
}

/**
 * 「淡层」容器色（v1.8 UI 重设计引入）。
 *
 * 用途：首页输入工作台、设置页分组面板的背景 —— 一种比页面底(background)更实、比卡片(surface)
 * 更软的中间层，让「大块输入区 / 设置分组」与普通卡片拉开层级，却不需要描边和阴影。
 *
 * 为什么单独成一档而不复用 surfaceVariant：
 * - surfaceVariant 在浅色下就是 Slate100(#F1F5F9)，被 FilterChip、TypeTag、骨架屏填充等
 *   小元素占用；它承载的是「小容器填充」，语义不是「页面层面的工作台底」。
 * - 与 CardStrokeLight 同理：同一层被两种语义复用，将来改其一必漏其二。
 *
 * 取值刻意落在 Slate50 ↔ Slate100 / DarkBackground ↔ DarkSurface 之间的一步：
 * - 浅色 #F2F5F8：压在 Slate50 页面上比纯白卡片更「落得下来」，又不至于重刷成一块 field。
 * - 深色 #16202F：比背景 #0B1220 亮一档形成面板感，又比卡片表面 #1E293B 暗一档，
 *   让内部白色输入框 / Switch 行能浮起来。
 */
val TintedLight = Color(0xFFF2F5F8)
val TintedDark = Color(0xFF16202F)

object TintedSurfaceColors {
    /** 淡层面板容器色：首页输入区、设置分组 */
    val container: Color
        @Composable get() = if (isSystemInDarkTheme()) TintedDark else TintedLight
}
