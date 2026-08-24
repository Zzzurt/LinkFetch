package com.linkfetch.app.util

enum class Platform(
    val key: String,
    val label: String,
    val badgeColor: Long,
) {
    XHS("xhs", "小红书", 0xFFFF2442),
    DOUYIN("douyin", "抖音", 0xFF161823),
    WEIBO("weibo", "微博", 0xFFE6162D),
    X("x", "X", 0xFF0F1419);

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
