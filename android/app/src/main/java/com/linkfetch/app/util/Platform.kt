package com.linkfetch.app.util

enum class Platform(
    val key: String,
    val label: String,
    val badgeColor: Long,
    /** 深色模式下的强调色（降饱和/提亮，保证与文字对比度） */
    val badgeColorDark: Long,
) {
    // 徽标与筛选 Chip 的前景色由 onPlatform() 按底色亮度自适应（亮底用深字、暗底用白字），
    // 不再固定白字；这里的 badgeColor 只是「平台品牌色」，深浅模式各自取值。
    // 四色定为：小红书红 / 微博橙 / 抖音青 / X 黑——冷暖错开，避免抖音黑与 X 黑两块近黑色糊在一起。
    XHS("xhs", "小红书", 0xFFE11D3D, 0xFFE11D3D),
    DOUYIN("douyin", "抖音", 0xFF25F4EE, 0xFF25F4EE),
    WEIBO("weibo", "微博", 0xFFF59E0B, 0xFFF59E0B),
    X("x", "X", 0xFF0F1419, 0xFF6B7280);

    companion object {
        fun fromKey(key: String?): Platform? = values().firstOrNull { it.key == key }

        fun fromUrl(url: String): Platform? {
            val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return null
            val hostMap = listOf(
                XHS to listOf("xhslink.com", "xiaohongshu.com", "xhslink.cn", "hongshu.com"),
                DOUYIN to listOf("douyin.com", "iesdouyin.com"),
                WEIBO to listOf("weibo.com", "weibo.cn", "m.weibo.cn", "t.cn", "video.weibo.com"),
                X to listOf("x.com", "twitter.com", "t.co"),
            )
            for ((platform, suffixes) in hostMap) {
                if (suffixes.any { host == it || host.endsWith(".$it") }) return platform
            }
            return null
        }
    }
}
