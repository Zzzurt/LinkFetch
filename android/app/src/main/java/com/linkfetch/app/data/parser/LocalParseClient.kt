package com.linkfetch.app.data.parser

import com.linkfetch.app.data.model.ParseResponseDto
import com.linkfetch.app.util.Platform
import java.io.IOException

/**
 * 本地直连解析入口：无需服务器，由 App 直接请求平台页面/接口完成解析。
 */
class LocalParseClient(
    private val cookieProvider: (Platform) -> String?,
    /** 抖音 WebView 兜底提取（真机可用；JVM 单测不传则跳过该路径）。 */
    private val douyinWebViewFallback: (suspend (pageUrl: String, cookie: String?) -> String?)? = null,
) {
    suspend fun parse(url: String): ParseResponseDto {
        val platform = Platform.fromUrl(url)
            ?: throw LocalParseException("unsupported_link", "仅支持小红书、抖音、微博、X 平台的链接")
        return try {
            val cookie = cookieProvider(platform)
            when (platform) {
                Platform.XHS -> XhsParser().parse(url, cookie)
                Platform.DOUYIN -> DouyinParser(
                    webViewPageFetcher = douyinWebViewFallback?.let { fetch -> { pageUrl -> fetch(pageUrl, cookie) } },
                ).parse(url, cookie)
                Platform.WEIBO -> WeiboParser().parse(url, cookie)
                Platform.X -> XParser().parse(url, cookie)
            }
        } catch (e: LocalParseException) {
            throw e
        } catch (e: IOException) {
            throw LocalParseException("network_error", "无法连接平台服务器，请检查网络后重试")
        }
    }
}
