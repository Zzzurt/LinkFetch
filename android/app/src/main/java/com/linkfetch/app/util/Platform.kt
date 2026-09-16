package com.linkfetch.app.util

enum class Platform(
    val key: String,
    val label: String,
    val badgeColor: Long,
    /** 深色模式下的强调色（降饱和/提亮，保证与文字对比度） */
    val badgeColorDark: Long,
    /** 可识别该平台的域名/短链特征（小写，可被 URL 解析与整段文案探测共用） */
    val domains: List<String>,
) {
    // 徽标与筛选 Chip 的前景色由 onPlatform() 按底色亮度自适应（亮底用深字、暗底用白字），
    // 不再固定白字；这里的 badgeColor 只是「平台品牌色」，深浅模式各自取值。
    // 四色定为：小红书红 / 微博橙 / 抖音青 / X 黑——冷暖错开，避免抖音黑与 X 黑两块近黑色糊在一起。
    XHS(
        "xhs", "小红书", 0xFFE11D3D, 0xFFE11D3D,
        listOf("xhslink.com", "xiaohongshu.com", "xhslink.cn", "hongshu.com"),
    ),
    DOUYIN(
        "douyin", "抖音", 0xFF25F4EE, 0xFF25F4EE,
        listOf("douyin.com", "iesdouyin.com"),
    ),
    WEIBO(
        "weibo", "微博", 0xFFF59E0B, 0xFFF59E0B,
        listOf("weibo.com", "weibo.cn", "m.weibo.cn", "t.cn", "video.weibo.com"),
    ),
    X(
        "x", "X", 0xFF0F1419, 0xFF6B7280,
        listOf("x.com", "twitter.com", "t.co"),
    );

    companion object {
        fun fromKey(key: String?): Platform? = values().firstOrNull { it.key == key }

        fun fromUrl(url: String): Platform? {
            val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return null
            return values().firstOrNull { p ->
                p.domains.any { host == it || host.endsWith(".$it") }
            }
        }

        /**
         * 从整段输入（可能是纯分享文案、也可能只含链接）中探测平台。
         *
         * 首页输入区徽标高亮用：用户在框里粘贴任何一段内容，实时告诉「这是哪个平台」。
         * 用域名特征做包含匹配，比强解析 URL 更宽容 —— 文案里的短链、纯链接都能命中。
         */
        fun detectFromText(text: String): Platform? =
            values().firstOrNull { p -> p.domains.any { text.contains(it, ignoreCase = true) } }
    }
}
