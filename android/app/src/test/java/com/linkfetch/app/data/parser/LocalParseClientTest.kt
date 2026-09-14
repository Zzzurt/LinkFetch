package com.linkfetch.app.data.parser

import com.linkfetch.app.util.Platform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalParseClientTest {

    @Test
    fun xGetsOverseasNetworkHint() {
        val message = platformNetworkErrorMessage(Platform.X)

        assertTrue("应明确提示需要海外网络：$message", message.contains("海外网络"))
        assertTrue("应给出可执行动作：$message", message.contains("代理") || message.contains("VPN"))
    }

    @Test
    fun otherPlatformsKeepGenericMessage() {
        listOf(Platform.XHS, Platform.DOUYIN, Platform.WEIBO).forEach { platform ->
            val message = platformNetworkErrorMessage(platform)
            assertTrue("$platform 不应混入海外网络提示：$message", !message.contains("海外网络"))
            assertTrue(message.contains("检查网络"))
        }
    }

    @Test
    fun unsupportedLinkFailsFastWithoutNetwork() = runBlocking {
        // 未识别平台应在发起任何请求之前就报错，避免用户等待后才看到「不支持的链接」
        val client = LocalParseClient(cookieProvider = { null })

        val exception = runCatching { client.parse("https://example.com/video") }.exceptionOrNull()

        assertTrue(exception is LocalParseException)
        assertEquals("unsupported_link", (exception as LocalParseException).code)
    }
}
