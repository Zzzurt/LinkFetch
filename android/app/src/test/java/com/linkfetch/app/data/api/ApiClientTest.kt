package com.linkfetch.app.data.api

import com.linkfetch.app.data.model.AppSettings
import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        // 绑定到 127.0.0.1：MockWebServer 默认用「本机主机名」作为 host，
        // 那个名字既非 loopback 也非私网 IP，会被 ApiClient 的明文守卫拦下。
        server.start(InetAddress.getByName("127.0.0.1"), 0)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * MockWebServer 的 `url()` 用「本机主机名」作为 host（反查 127.0.0.1 得到），
     * 那个名字既不是 loopback 也不是私网 IP，会被 ApiClient 的明文守卫拦下，
     * 因此这里显式用 127.0.0.1 + 端口拼地址。
     */
    private fun baseUrl(): String = "http://127.0.0.1:${server.port}"

    private fun client(settings: AppSettings = AppSettings(baseUrl = baseUrl())) =
        ApiClient(settingsProvider = { settings })

    @Test
    fun parseSuccessDecodesResponse() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "platform": "xhs",
                      "title": "测试笔记",
                      "author": "作者",
                      "type": "mixed",
                      "medias": [
                        {"kind": "video", "url": "https://cdn.example.com/v.mp4", "quality": "original"},
                        {"kind": "image", "url": "https://cdn.example.com/a.jpg"}
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val result = client().parse("https://xhslink.com/a/x")

        assertEquals("xhs", result.platform)
        assertEquals("mixed", result.type)
        assertEquals(2, result.medias.size)
        assertTrue(result.medias[0].isVideo)
        assertEquals("https://cdn.example.com/a.jpg", result.images.first().url)

        val recorded = server.takeRequest()
        assertEquals("/api/parse", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("xhslink.com"))
    }

    @Test
    fun parseMapsBackendErrorCode() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":"unsupported_link","message":"仅支持小红书、抖音、微博平台的链接"}"""),
        )

        val exception = runCatching { client().parse("https://example.com/x") }.exceptionOrNull()

        assertNotNull(exception)
        assertTrue(exception is ApiException)
        assertEquals("unsupported_link", (exception as ApiException).code)
        assertEquals("仅支持小红书、抖音、微博平台的链接", exception.message)
    }

    @Test
    fun sendsTokenAndCookies() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"platform":"weibo","title":"t","type":"image","medias":[]}"""),
        )
        val settings = AppSettings(
            baseUrl = baseUrl(),
            apiToken = "secret-token",
            xhsCookie = "cookie-xhs",
            douyinCookie = "cookie-douyin",
            weiboCookie = "cookie-weibo",
        )

        client(settings).parse("https://t.cn/A6xYz")

        val recorded = server.takeRequest()
        assertEquals("secret-token", recorded.getHeader("X-API-Token"))
        assertEquals("cookie-xhs", recorded.getHeader("X-Cookie-XHS"))
        assertEquals("cookie-douyin", recorded.getHeader("X-Cookie-DOUYIN"))
        assertEquals("cookie-weibo", recorded.getHeader("X-Cookie-WEIBO"))
    }

    @Test
    fun healthReturnsStatus() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","service":"linkfetch"}"""),
        )

        val status = client().health()

        assertEquals("ok", status)
        assertEquals("/api/health", server.takeRequest().path)
    }

    @Test
    fun networkErrorMapsToFriendlyCode() = runTest {
        server.shutdown()

        val exception = runCatching { client().parse("https://xhslink.com/a/x") }.exceptionOrNull()

        assertTrue(exception is ApiException)
        assertEquals("network_error", (exception as ApiException).code)
    }

    @Test
    fun rejectsCleartextToPublicHost() = runTest {
        // 凭证不得以明文发往公网地址：必须在发请求之前就拒绝
        val settings = AppSettings(baseUrl = "http://example.com:8000")

        val exception = runCatching { client(settings).parse("https://xhslink.com/a/x") }.exceptionOrNull()

        assertTrue(exception is ApiException)
        assertEquals("insecure_transport", (exception as ApiException).code)
        assertEquals(0, server.requestCount) // 未发出任何请求
    }

    @Test
    fun allowsCleartextToPrivateHost() = runTest {
        // 局域网 / 回环地址允许明文（自建服务的常见部署方式）
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","service":"linkfetch"}"""),
        )

        val status = client(AppSettings(baseUrl = baseUrl())).health()

        assertEquals("ok", status)
    }

    @Test
    fun allowsHttpsTransport() = runTest {
        // 守卫只针对明文：https 地址不会被策略拒绝（此处只验证不是 insecure_transport）
        val exception = runCatching {
            client(AppSettings(baseUrl = "https://127.0.0.1:1")).health()
        }.exceptionOrNull()

        assertTrue(exception is ApiException)
        assertEquals("network_error", (exception as ApiException).code)
    }
}

