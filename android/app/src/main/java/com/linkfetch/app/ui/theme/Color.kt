package com.linkfetch.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ---------------------------------------------------------------------------
// 黑白灰（Mono）体系
//
// 界面只保留三级灰阶：纸（页面底）、卡（内容面）、墨（文字与动作）。
// 品牌色从界面上完全退场 —— 取回的照片与视频是屏幕里唯一的颜色。
// 深色不是浅色的反相，而是另一张「夜纸」：底色压到近黑，靠 1px 描边与字重
// 建立层级，不用投影。
// ---------------------------------------------------------------------------

// 浅色：纸 / 卡 / 墨
val Paper = Color(0xFFF3F3F4)
val PaperDim = Color(0xFFE9E9EC)
val CardWhite = Color(0xFFFFFFFF)
val Ink = Color(0xFF0A0A0A)
val Ink2 = Color(0xFF6A6A73)
val Line = Color(0xFFE2E2E6)
val LineStrong = Color(0xFFD3D3D9)

// 深色：夜纸 / 夜卡 / 夜墨
val NightPaper = Color(0xFF0A0A0B)
val NightCard = Color(0xFF141417)
val NightRaised = Color(0xFF1B1B1F)
val NightInk = Color(0xFFF4F4F6)
val NightInk2 = Color(0xFFA2A2AA)
val NightLine = Color(0xFF26262B)
val NightLineStrong = Color(0xFF34343A)

// 语义色：黑白灰界面里唯一允许出现的彩色是「错误」。
// 取低饱和砖红，既能被识别为异常，又不会把整页拉回彩色。
val MonoError = Color(0xFF9B2C24)
val MonoErrorDark = Color(0xFFE59A93)

/** 平台色（仅在无障碍需要区分、且不使用文字码的少数场景保留真实品牌色） */
fun platformAccent(platform: com.linkfetch.app.util.Platform, isDark: Boolean): Color =
    Color(if (isDark) platform.badgeColorDark else platform.badgeColor)

/** 平台色前景 */
fun onPlatform(accent: Color): Color =
    if (accent.luminance() > 0.5f) Ink else Color.White

/**
 * 次级文字语义色。
 *
 * 黑白灰体系里只有两档文字：墨（主）与墨 60%（次）。不再用透明度叠色 ——
 * 灰值直接取自色板，叠在任何一层纸上都稳定。
 */
object TextColors {
    val muted: Color
        @Composable get() = if (LocalDarkTheme.current) NightInk2 else Ink2
}

/** 卡片 / 输入框描边：黑白灰界面靠它建立边界，不用阴影。 */
object StrokeColors {
    val card: Color
        @Composable get() = if (LocalDarkTheme.current) NightLine else Line
}

/** 低一档的容器（设置页的提示块）：比纸更实、比卡更暗。 */
object TintedSurfaceColors {
    val container: Color
        @Composable get() = if (LocalDarkTheme.current) NightRaised else PaperDim
}

/** 主文字 / 反相文字 */
object InkColors {
    val main: Color
        @Composable get() = if (LocalDarkTheme.current) NightInk else Ink

    /** 反相块（黑色 CTA）上的文字 */
    val inverse: Color
        @Composable get() = if (LocalDarkTheme.current) NightPaper else CardWhite

    /** 反相块底色 */
    val inverseSurface: Color
        @Composable get() = if (LocalDarkTheme.current) NightInk else Ink
}
