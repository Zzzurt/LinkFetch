package com.linkfetch.app.data.parser

import com.linkfetch.app.data.model.AppSettings
import com.linkfetch.app.data.model.ParseResponseDto
import kotlinx.coroutines.CancellationException

data class ParseOutcome(
    val response: ParseResponseDto,
    val viaServerFallback: Boolean = false,
)

/**
 * 解析入口：默认在设备上直连，直连失败且用户配置过自建服务器时自动改走服务器。
 *
 * 只在「一次性失败」上回退：unsupported_link 是输入问题，换通道也没用；
 * 备用通道同样失败时抛回直连的原始错误 —— 用户选的是直连模式，不该看到服务器的报错。
 */
class ParseService(
    private val localParse: suspend (String) -> ParseResponseDto,
    private val serverParse: suspend (String) -> ParseResponseDto,
    private val settingsProvider: () -> AppSettings,
) {

    suspend fun parse(url: String): ParseOutcome {
        val settings = settingsProvider()
        if (settings.parseMode == "server") {
            return ParseOutcome(serverParse(url))
        }
        return try {
            ParseOutcome(localParse(url))
        } catch (e: LocalParseException) {
            if (!settings.serverConfigured || e.code !in FALLBACK_CODES) throw e
            val fallback = try {
                serverParse(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw e
            }
            ParseOutcome(fallback, viaServerFallback = true)
        }
    }

    private companion object {
        val FALLBACK_CODES = setOf("network_error", "parse_failed", "rate_limited")
    }
}
