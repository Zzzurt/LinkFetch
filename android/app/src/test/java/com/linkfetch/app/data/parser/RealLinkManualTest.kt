package com.linkfetch.app.data.parser

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真实网络手动验证（需要外网 + 环境变量 LINKFETCH_REAL_TEST=1）：
 *   LINKFETCH_REAL_TEST=1 .\gradlew.bat :app:testDebugUnitTest --tests "*RealLinkManualTest"
 *
 * 可选：抖音用例需要登录态才有意义，可用 LINKFETCH_DOUYIN_COOKIE 提供：
 *   set LINKFETCH_DOUYIN_COOKIE=<浏览器复制的 Cookie>
 *
 * 2026-09-14 实测结论（避免把环境限制误判成回归）：
 * - X：`cdn.syndication.twimg.com` 与 `x.com` 在国内网络下 connect 直接失败（curl http_code=000），
 *   因此本机必然失败；这类用例改为「不可达则跳过」。
 * - 抖音：分享页 `window._ROUTER_DATA` 已不再内嵌作品数据（实测只含 ua/isSpider/itemId 等页面元信息），
 *   `share/slides/...` 会直接命中风控页（带不带 share_sign 参数结果一致）；
 *   PC 详情 / SEO / iteminfo 在无 Cookie 时同样被拦。App 真正依赖的是设备侧 WebView 兜底，
 *   而 WebView 无法在 JVM 单测中运行，故无 Cookie 时该用例跳过。
 */
class RealLinkManualTest {

    private fun assumeRealNetwork() {
        assumeTrue(
            "跳过：未设置 LINKFETCH_REAL_TEST=1",
            System.getenv("LINKFETCH_REAL_TEST") == "1",
        )
    }

    private companion object {
        /** 只探测一次，两个 X 用例共用结果。 */
        val overseasReachable: Boolean by lazy {
            runCatching {
                OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                    .newCall(
                        Request.Builder()
                            .url("https://cdn.syndication.twimg.com/tweet-result?id=1&lang=zh&token=7")
                            .header("User-Agent", "Mozilla/5.0")
                            .build(),
                    )
                    .execute()
                    .use { response -> response.code > 0 }
            }.getOrDefault(false)
        }
    }

    /**
     * X 需要能访问海外网络；不可达时跳过，否则失败会被误读成解析器回归。
     *
     * 注意必须发真实 HTTPS 请求来判定：实测本机到该域名的 TCP 握手能成功，
     * 但随后的 TLS/HTTP 会被重置（curl 得到 http_code=000），
     * 仅凭 Socket.connect 探测会误判为「可达」。
     */
    private fun assumeOverseasReachable() {
        assumeTrue("跳过：当前网络无法访问 X（需要海外网络，如代理 / VPN）", overseasReachable)
    }

    @Test
    fun realXhsLinkParses() {
        assumeRealNetwork()
        runBlocking {
            val client = LocalParseClient(cookieProvider = { null })
            val result = client.parse("http://xhslink.cn/o/1OhM4NLR50k")
            assertEquals("xhs", result.platform)
            assertTrue(result.medias.isNotEmpty())
            // 原图：fileId + JPEG 转换参数（无水印、原分辨率）
            assertTrue(result.images.first().url.contains("imageView2/0/format/jpg"))
        }
    }

    @Test
    fun realWeiboLinkParses() {
        assumeRealNetwork()
        runBlocking {
            val client = LocalParseClient(cookieProvider = { null })
            val result = client.parse("https://weibo.com/6238113311/5326855628391978")
            assertEquals("weibo", result.platform)
            assertTrue(result.medias.isNotEmpty())
            // 使用 mw2000 大图而非缩略图
            assertTrue(result.images.first().url.contains("mw2000"))
        }
    }

    @Test
    fun realDouyinLinkParsesWithCookie() {
        assumeRealNetwork()
        val cookie = System.getenv("LINKFETCH_DOUYIN_COOKIE")
        assumeTrue(
            "跳过：抖音免 Cookie 的分享页已不再内嵌作品数据，请用 LINKFETCH_DOUYIN_COOKIE 提供登录态 " +
                "（设备侧则依赖 WebView 兜底，无法在 JVM 单测中验证）",
            !cookie.isNullOrBlank(),
        )
        runBlocking {
            val client = LocalParseClient(cookieProvider = { cookie }, douyinWebViewFallback = null)
            val result = client.parse("https://v.douyin.com/5LgdumGF1iw/")
            assertEquals("douyin", result.platform)
            assertTrue(result.medias.isNotEmpty())
        }
    }

    @Test
    fun realXImageLinkParses() {
        assumeRealNetwork()
        assumeOverseasReachable()
        runBlocking {
            val client = LocalParseClient(cookieProvider = { null })
            val result = client.parse("https://x.com/i/status/2083053411524850111")
            assertEquals("x", result.platform)
            assertTrue(result.medias.isNotEmpty())
            assertTrue(result.images.first().url.contains("name=orig"))
        }
    }

    @Test
    fun realXVideoLinkParses() {
        assumeRealNetwork()
        assumeOverseasReachable()
        runBlocking {
            val client = LocalParseClient(cookieProvider = { null })
            val result = client.parse("https://x.com/i/status/2082841167251845607")
            assertEquals("x", result.platform)
            assertTrue(result.videos.isNotEmpty())
        }
    }
}
