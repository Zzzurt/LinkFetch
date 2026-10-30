package com.linkfetch.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 黑白灰（Mono）配色。
 *
 * 与上一版最大的不同：primary 不再是品牌色，而是墨色本身。
 * 这样所有 M3 组件（按钮、选中态、进度条）默认就落在黑白灰里，
 * 不会出现「某个组件偷偷带了一点蓝」的情况。
 */
private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = CardWhite,
    primaryContainer = PaperDim,
    onPrimaryContainer = Ink,
    secondary = Ink2,
    onSecondary = CardWhite,
    secondaryContainer = PaperDim,
    onSecondaryContainer = Ink,
    tertiary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Ink2,
    outline = LineStrong,
    outlineVariant = Line,
    error = MonoError,
    onError = CardWhite,
    errorContainer = Color(0xFFF5E4E2),
    onErrorContainer = MonoError,
    // 关闭 M3 的 elevation 染色：卡片抬升时不应该泛出一层颜色
    surfaceTint = Color.Transparent,
)

private val DarkColors = darkColorScheme(
    primary = NightInk,
    onPrimary = NightPaper,
    primaryContainer = NightRaised,
    onPrimaryContainer = NightInk,
    secondary = NightInk2,
    onSecondary = NightPaper,
    secondaryContainer = NightRaised,
    onSecondaryContainer = NightInk,
    tertiary = NightInk,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = NightRaised,
    onSurfaceVariant = NightInk2,
    outline = NightLineStrong,
    outlineVariant = NightLine,
    error = MonoErrorDark,
    onError = NightPaper,
    errorContainer = Color(0xFF3A211E),
    onErrorContainer = MonoErrorDark,
    surfaceTint = Color.Transparent,
)

/**
 * 当前生效的深浅色。
 *
 * 所有语义色（[TextColors] 等）都从这里取值，而不是各自调用 [isSystemInDarkTheme]：
 * 用户可以在设置里强制浅色 / 深色，若色板仍跟着系统走，就会出现「深色背景 + 浅色模式的灰字」。
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** 设置里的主题模式 → 实际是否深色。 */
@Composable
fun resolveDarkTheme(themeMode: String): Boolean = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun LinkFetchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 状态栏 / 导航栏图标随主题反色。放在主题里而不是各 Activity 里，
    // 设置页切换主题时能立即生效，不必等页面重建。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = LinkFetchTypography,
            shapes = LinkFetchShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
