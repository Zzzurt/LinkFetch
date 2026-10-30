package com.linkfetch.app.util

/**
 * 合并多个域名取回的 Cookie 头，按名字去重（先出现的优先）。
 *
 * WebView 的 `CookieManager.getCookie(url)` 只能按 URL 取，而一次登录的凭证可能分别落在
 * 主域与子域（如 xiaohongshu.com / edith.xiaohongshu.com）；直接拼接会产生重复的键。
 */
fun mergeCookieHeaders(headers: Iterable<String?>): String {
    val merged = LinkedHashMap<String, String>()
    headers.forEach { header ->
        header?.split(';')?.forEach { part ->
            val pair = part.trim()
            if (pair.isEmpty()) return@forEach
            val name = pair.substringBefore('=', "").trim()
            // $Path / $Domain 之类的属性不是 Cookie 键值对，跳过
            if (name.isEmpty() || name.startsWith("$")) return@forEach
            if (name !in merged) merged[name] = pair
        }
    }
    return merged.values.joinToString("; ")
}
