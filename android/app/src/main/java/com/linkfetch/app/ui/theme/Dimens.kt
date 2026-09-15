package com.linkfetch.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 间距（4 的倍数体系）。
 *
 * 布局节奏只用三档：xl(24) 区块之间 / md(12) 组内元素 / sm(8) 紧邻元素。
 * lg(16) 是屏幕边距与组件内边距；xs(4) 只用于组件内部微调（徽标内衬等），
 * 不参与页面节奏 —— 页面里出现 4dp 间距，通常说明这一层结构本身就该合并。
 *
 * **不要再引入 6dp / 10dp 这类中间值。** 体系一旦出现中间值，页面节奏就会在各处悄悄分叉：
 * v1.7.6 曾有 11 处 6dp、3 处 10dp，都是"当时手感差一点"就地写死的。
 */
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

/**
 * 圆角体系：三档 + 图片网格 + 胶囊。
 *
 * 数值关系是刻意的：**内层永远不比外层更圆**。此前 `large` 是 20dp 而卡片是 16dp，
 * 于是 16dp 的卡片里嵌一个 20dp 的输入框，圆角方向是反的（越往里越圆），视觉上发"松"。
 * 现在 field(12) < card(16)，嵌套时内层降一档即可。
 */
object Radii {
    /**
     * 图片网格单元。结果页定稿为「相册式」：圆角只留一点点，配合 2dp 间隙让整组图片连成一片，
     * 而不是各自成为一张带底的卡片（卡片感会把画面切碎、削弱内容本身）。
     */
    val grid = RoundedCornerShape(4.dp)
    /** 小元素、缩略图 */
    val small = RoundedCornerShape(8.dp)
    /** 输入框、小容器 */
    val field = RoundedCornerShape(12.dp)
    /** 卡片、对话框 */
    val card = RoundedCornerShape(16.dp)
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

/**
 * M3 组件的形状映射。
 *
 * 全部指回 [Radii]，让「圆角」只有一个入口。此前这里是独立写死的一套值
 * （medium = 卡片 16dp、large = 20dp），与 [Radii] 各说各话，改一处漏一处 ——
 * 同一个圆角概念有两套来源，本身就是设计债。
 */
val LinkFetchShapes = Shapes(
    small = Radii.small,
    medium = Radii.field,
    large = Radii.card,
    extraLarge = Radii.pill,
)
