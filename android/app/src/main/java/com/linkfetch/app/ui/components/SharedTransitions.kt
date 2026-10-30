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
 * 只有两端都在组合树里、且 key 相同才会产生过渡；从首页直接解析进入结果页时
 * 没有源端，自然退化为普通页面切换。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(key: Any): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedCover.sharedElement(
            rememberSharedContentState(key),
            animatedScope,
        )
    }
}
