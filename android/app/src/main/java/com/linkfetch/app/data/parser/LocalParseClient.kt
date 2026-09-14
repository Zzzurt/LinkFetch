package com.linkfetch.app.data.parser

import com.linkfetch.app.data.model.ParseResponseDto
import com.linkfetch.app.util.Platform
import java.io.IOException
import okhttp3.OkHttpClient

/**
 * 本地直连解析入口：无需服务器，由 App 直接请求平台页面/接口完成解析。
 *
 * @param client 生产环境必须注入全局共享的 HTTP 客户端（见 AppContainer.okHttp），
 *               避免每个平台解析器各自持有独立的连接池与线程池；默认值仅供单测使用。
 */
class LocalParseClient(
    private val cookieProvider: (Platform) -> String?,
    private val client: OkHttpClient = OkHttpClient(),
    /** 抖音 WebView 兜底提取（真机可用；JVM 单测不传则跳过该路径）。 */
    private val douyinWebViewFallback: (suspend (pageUrl: String, cookie: String?) -> String?)? = null,
) {
    suspend fun parse(url: String): ParseResponseDto {
        val platform = Platform.fromUrl(url)
            ?: throw LocalParseException("unsupported_link", "仅支持小红书、抖音、微博、X 平台的链接")
        return try {
            val cookie = cookieProvider(platform)
            when (platform) {
                Platform.XHS -> XhsParser(client = client).parse(url, cookie)
                Platform.DOUYIN -> DouyinParser(
                    webViewPageFetcher = douyinWebViewFallback?.let { fetch -> { pageUrl -> fetch(pageUrl, cookie) } },
                    client = client,
                ).parse(url, cookie)
                Platform.WEIBO -> WeiboParser(client = client).parse(url, cookie)
                Platform.X -> XParser(client = client).parse(url, cookie)
            }
        } catch (e: LocalParseException) {
            throw e
        } catch (e: IOException) {
            throw LocalParseException("network_error", platformNetworkErrorMessage(platform))
        }
    }
}

/**
 * 平台感知的网络错误提示。
 *
 * X 的 syndication 接口在国内网络下完全不可达（实测 connect 直接失败），
 * 若统一回「请检查网络」，用户会反复排查本地 Wi-Fi 而找不到原因；
 * README 也承诺过该平台会给出明确提示，此前并未兑现。
 */
internal fun platformNetworkErrorMessage(platform: Platform): String = when (platform) {
    Platform.X -> "无法连接 X：该平台需要能访问海外网络，请开启代理 / VPN 后重试"
    else -> "无法连接平台服务器，请检查网络后重试"
}
