package com.linkfetch.app.data.api

import com.linkfetch.app.data.model.AppSettings
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
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(settings: AppSettings = AppSettings(baseUrl = server.url("/").toString().trimEnd('/'))) =
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
            baseUrl = server.url("/").toString().trimEnd('/'),
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
}

