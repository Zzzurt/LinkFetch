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

/** 圆角体系：缩略图 / 小元素 / 卡片 / 大容器 / 胶囊 */
object Radii {
    /**
     * 缩略图。结果页图片网格定稿为「相册式」：圆角只留一点点，让图片彼此连成一片，
     * 而不是各自成为一张带底的卡片（卡片感会把画面切碎、削弱内容本身）。
     */
    val thumbnail = RoundedCornerShape(4.dp)
    val small = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}

/**
 * 单列内容的最大宽度。
 *
 * 平板 / 折叠屏展开 / 横屏下如果让内容铺满，正文单行会超过 ~600dp（中文 40+ 字），
 * 阅读时眼睛要来回扫，图片网格也会被撑成巨幅。超过该宽度时内容居中、两侧留白。
 * 手机竖屏（360~430dp）完全不受影响。
 */
val PageMaxWidth = 600.dp

val LinkFetchShapes = Shapes(
    small = Radii.small,
    medium = Radii.card,
    large = Radii.large,
    extraLarge = Radii.pill,
)
