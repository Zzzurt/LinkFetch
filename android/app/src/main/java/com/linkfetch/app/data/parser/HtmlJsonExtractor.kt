package com.linkfetch.app.data.parser

/**
 * 从页面 HTML 中按标记提取内嵌的 JS 对象字面量，并清洗为非 JSON 的 JS 值。
 *
 * 页面内嵌数据形如：window.__X__ = {...}
 * - 逐字符括号配平，正确处理字符串中的 `</script>`、花括号等干扰内容；
 * - JS 字面量中的 undefined / NaN 替换为 null，使其可被标准 JSON 解析。
 */
object HtmlJsonExtractor {

    private val UNDEFINED = Regex("\\bundefined\\b")
    private val NAN = Regex("\\bNaN\\b")

    fun extractJsonObject(html: String, marker: String): String? {
        val start = html.indexOf(marker)
        if (start < 0) return null
        val brace = html.indexOf('{', start)
        if (brace < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        var end = -1
        var i = brace
        while (i < html.length) {
            val ch = html[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else {
                    when (ch) {
                        '\\' -> escaped = true
                        '"' -> inString = false
                    }
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }
            i++
        }
        if (end < 0) return null

        var blob = html.substring(brace, end + 1)
        blob = UNDEFINED.replace(blob, "null")
        blob = NAN.replace(blob, "null")
        return blob
    }
}

