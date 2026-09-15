package com.linkfetch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue700,
    secondary = Slate600,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate800,
    tertiary = Blue500,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate200,
    // 分隔线专用色。原先这里是 Slate100(#F1F5F9)，压在页面底 Slate50(#F8FAFC) 上对比只有
    // 1.05:1 —— 浅色模式下的 Divider 等于没画（分隔线要"轻"，但不能不可见）。
    // 提高到 Slate300 后约 1.42:1，与深色下 Slate700 压 DarkBackground 的 1.81:1 观感接近。
    // outline(Slate200) 保持不动：它同时是 OutlinedTextField 未聚焦态的默认边框色。
    // 注意 outlineVariant 在浅色下只被 Divider 使用（浅色卡片不带描边），所以这次改动面很窄。
    outlineVariant = Slate300,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    surfaceTint = Blue600,
)

private val DarkColors = darkColorScheme(
    primary = Blue300,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Blue300,
    secondary = Slate400,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Slate700,
    onSecondaryContainer = Slate200,
    tertiary = Blue500,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    // 深色下的分层靠描边，因此这两个值必须与 surface(#1E293B) 有可见差异：
    // 原先 outlineVariant = Slate800(#1E293B) 与卡片底色完全相同，画上去等于没画。
    // 现在 卡片细描边(Slate700) < 输入框边框(Slate600)，形成可辨的两级。
    outline = Slate600,
    outlineVariant = Slate700,
    error = ErrorRedDark,
    onError = Color(0xFF0F172A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    surfaceTint = Blue300,
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
