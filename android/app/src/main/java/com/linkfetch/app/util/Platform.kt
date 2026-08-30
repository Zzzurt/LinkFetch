package com.linkfetch.app.util

enum class Platform(
    val key: String,
    val label: String,
    val badgeColor: Long,
    /** 深色模式下的强调色（降饱和/提亮，保证与文字对比度） */
    val badgeColorDark: Long,
) {
    XHS("xhs", "小红书", 0xFFFF2442, 0xFFE11D3D),
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
