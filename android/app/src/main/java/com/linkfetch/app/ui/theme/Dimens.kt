package com.linkfetch.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 间距（4 的倍数体系） */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** 屏幕统一边距 */
    val screen = 16.dp
}

/** 圆角体系：小元素 / 卡片 / 大容器 / 胶囊 */
object Radii {
    val small = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}

val LinkFetchShapes = Shapes(
    small = Radii.small,
    medium = Radii.card,
    large = Radii.large,
    extraLarge = Radii.pill,
)
