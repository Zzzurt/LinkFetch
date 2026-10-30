package com.linkfetch.app.util

/**
 * 从剪贴板等任意文本中提取第一条平台链接。
 */
object UrlExtractor {

    // 排除空白、引号、括号以及常见中文全角标点；允许中英文、数字与路径符号
    private val URL_REGEX = Regex("https?://[^\\s\"'<>（）()【】「」『』《》〈〉，。；;、,]+")
    private val TRAILING_PUNCT = Regex("[.,;:!?，。；！？、)）]+$")

    fun extractFirstPlatformUrl(text: String): String? {
        val match = URL_REGEX.find(text.trim()) ?: return null
        val url = TRAILING_PUNCT.replace(match.value.trim(), "")
        return if (Platform.fromUrl(url) != null) url else null
    }

    /**
     * 提取文本里的全部平台链接，按出现顺序去重。
     *
     * 批量粘贴时常混着非目标平台的链接（例如正文里带了别的网站），这里一并过滤掉，
     * 剩下的才是可以交给解析器的。
     */
    fun extractAllPlatformUrls(text: String): List<String> =
        URL_REGEX.findAll(text)
            .map { TRAILING_PUNCT.replace(it.value.trim(), "") }
            .filter { Platform.fromUrl(it) != null }
            .distinct()
            .toList()
}

