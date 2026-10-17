package com.linkfetch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun LinkFetchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LinkFetchTypography,
        shapes = LinkFetchShapes,
        content = content,
    )
}
