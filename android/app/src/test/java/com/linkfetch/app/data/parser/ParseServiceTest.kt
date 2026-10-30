package com.linkfetch.app.data.parser

import com.linkfetch.app.data.model.AppSettings
import com.linkfetch.app.data.model.ParseResponseDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseServiceTest {

    private val response = ParseResponseDto(platform = "xhs", title = "标题", type = "image")

    @Test
    fun directSuccessDoesNotTouchServer() = runBlocking {
        var serverCalls = 0
        val service = service(
            local = { response },
            server = { serverCalls++; response },
            settings = AppSettings(baseUrl = "https://api.example.com"),
        )

        val outcome = service.parse("https://xhslink.com/a/abc")

        assertEquals(response, outcome.response)
        assertEquals(false, outcome.viaServerFallback)
        assertEquals(0, serverCalls)
    }

    @Test
    fun fallsBackToServerWhenDirectFailsAndServerIsConfigured() = runBlocking {
        val service = service(
            local = { throw LocalParseException("network_error", "直连失败") },
            server = { response },
            settings = AppSettings(baseUrl = "https://api.example.com"),
        )

        val outcome = service.parse("https://xhslink.com/a/abc")

        assertEquals(response, outcome.response)
        assertTrue(outcome.viaServerFallback)
    }

    @Test
    fun doesNotFallBackWithoutConfiguredServer() = runBlocking {
        var serverCalls = 0
        val service = service(
            local = { throw LocalParseException("parse_failed", "直连失败") },
            server = { serverCalls++; response },
            settings = AppSettings(baseUrl = AppSettings.DEFAULT_BASE_URL),
        )

        try {
            service.parse("https://xhslink.com/a/abc")
            assertTrue("应当抛出原始错误", false)
        } catch (e: LocalParseException) {
            assertEquals("parse_failed", e.code)
        }
        assertEquals(0, serverCalls)
    }

    @Test
    fun unsupportedLinkNeverFallsBack() = runBlocking {
        var serverCalls = 0
        val service = service(
            local = { throw LocalParseException("unsupported_link", "不支持") },
            server = { serverCalls++; response },
            settings = AppSettings(baseUrl = "https://api.example.com"),
        )

        try {
            service.parse("https://example.com/x")
            assertTrue("应当抛出原始错误", false)
        } catch (e: LocalParseException) {
            assertEquals("unsupported_link", e.code)
        }
        assertEquals(0, serverCalls)
    }

    @Test
    fun keepsOriginalErrorWhenFallbackAlsoFails() = runBlocking {
        val service = service(
            local = { throw LocalParseException("network_error", "直连失败") },
            server = { throw LocalParseException("parse_failed", "服务器也失败") },
            settings = AppSettings(baseUrl = "https://api.example.com"),
        )

        try {
            service.parse("https://xhslink.com/a/abc")
            assertTrue("应当抛出直连错误", false)
        } catch (e: LocalParseException) {
            assertEquals("network_error", e.code)
            assertEquals("直连失败", e.message)
        }
    }

    @Test
    fun serverModeUsesServerDirectly() = runBlocking {
        var localCalls = 0
        val service = service(
            local = { localCalls++; response },
            server = { response },
            settings = AppSettings(parseMode = "server", baseUrl = "https://api.example.com"),
        )

        val outcome = service.parse("https://xhslink.com/a/abc")

        assertEquals(response, outcome.response)
        assertEquals(0, localCalls)
        assertEquals(false, outcome.viaServerFallback)
    }

    private fun service(
        local: suspend (String) -> ParseResponseDto,
        server: suspend (String) -> ParseResponseDto,
        settings: AppSettings,
    ) = ParseService(
        localParse = local,
        serverParse = server,
        settingsProvider = { settings },
    )
}
