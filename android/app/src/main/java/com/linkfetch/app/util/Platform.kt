package com.linkfetch.app.util

enum class Platform(
    val key: String,
    val label: String,
    val badgeColor: Long,
    /** 深色模式下的强调色（降饱和/提亮，保证与文字对比度） */
    val badgeColorDark: Long,
) {
    // 徽标与筛选 Chip 都用「底色 + 白字」，白字需 ≥4.5:1（14sp 标签、12.6sp 徽标字）。
    // 小红书原先的 #FF2442 白字只有 3.77:1，不达 AA；改用同色系更深的 #E11D3D → 4.72:1。
    // 微博 #E6162D 为 4.65:1，刚好达标，维持原品牌色不动。
    XHS("xhs", "小红书", 0xFFE11D3D, 0xFFE11D3D),
    DOUYIN("douyin", "抖音", 0xFF161823, 0xFF3B4250),
    WEIBO("weibo", "微博", 0xFFE6162D, 0xFFC4111F),
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
