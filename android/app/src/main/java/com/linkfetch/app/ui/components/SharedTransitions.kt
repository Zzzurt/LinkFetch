package com.linkfetch.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 共享元素所需的两个作用域，由导航层统一下发。
 *
 * 页面本身不该知道自己在 Navigation 的哪个 AnimatedContent 里；
 * 页面只声明「这块内容是某个 key 的共享元素」，拿不到作用域时（预览、单测）
 * 自动退化成普通 Modifier。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 历史列表封面 → 结果页首图的共享元素。
 *
 * 1.9.0 线上反馈：几十张图的记录在 LazyVerticalGrid 中会触发 Compose
 * “Placement happened before lookahead” 崩溃。升级 Compose 并验证大图集前先停用，
 * 页面切换仍由 ScreenFadeIn 承担。
 */
@Composable
fun Modifier.sharedCover(@Suppress("UNUSED_PARAMETER") key: Any): Modifier = this
