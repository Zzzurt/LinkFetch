package com.linkfetch.app.data.api

import com.linkfetch.app.data.model.ApiErrorDto
import com.linkfetch.app.data.model.AppSettings
import com.linkfetch.app.data.model.HealthDto
import com.linkfetch.app.data.model.ParseRequestDto
import com.linkfetch.app.data.model.ParseResponseDto
import com.linkfetch.app.util.isPrivateOrLoopbackHost
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(val code: String, message: String) : Exception(message)

class ApiClient(
    private val settingsProvider: () -> AppSettings,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun parse(url: String): ParseResponseDto = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        ensureTransportAllowed(settings)
        val builder = Request.Builder()
            .url(settings.baseUrl.trimEnd('/') + "/api/parse")
            .post(json.encodeToString(ParseRequestDto(url)).toRequestBody("application/json".toMediaType()))
        attachHeaders(builder, settings)
        execute(builder.build()) { body ->
            json.decodeFromString(ParseResponseDto.serializer(), body)
        }
    }

    suspend fun health(): String = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        ensureTransportAllowed(settings)
        val builder = Request.Builder().url(settings.baseUrl.trimEnd('/') + "/api/health")
        attachHeaders(builder, settings)
        execute(builder.build()) { body ->
            json.decodeFromString(HealthDto.serializer(), body).status
        }
    }

    /**
     * 拒绝把凭证发往公网的明文地址。
     *
     * 平台 Cookie 与 API Token 是账号级凭证，一旦以明文发出即可被同网段嗅探；
     * 局域网 / 回环地址视为可信（自建服务的常见部署方式），公网地址必须走 HTTPS。
     * 该判断与 network_security_config.xml 双重生效：前者给出可读提示，后者做硬性拦截。
     */
    private fun ensureTransportAllowed(settings: AppSettings) {
        val url = settings.baseUrl.trim().toHttpUrlOrNull() ?: return
        if (url.isHttps) return
        if (isPrivateOrLoopbackHost(url.host)) return
        throw ApiException(
            "insecure_transport",
            "出于安全考虑，仅允许对局域网 / 本机地址使用 http://（当前为 ${url.host}）。" +
                "请改用 https://，或填入内网 IP。",
        )
    }

    private fun attachHeaders(builder: Request.Builder, settings: AppSettings) {
        if (settings.apiToken.isNotBlank()) builder.header("X-API-Token", settings.apiToken)
        if (settings.xhsCookie.isNotBlank()) builder.header("X-Cookie-XHS", settings.xhsCookie)
        if (settings.douyinCookie.isNotBlank()) builder.header("X-Cookie-DOUYIN", settings.douyinCookie)
        if (settings.weiboCookie.isNotBlank()) builder.header("X-Cookie-WEIBO", settings.weiboCookie)
    }

    private inline fun <T> execute(request: Request, decode: (String) -> T): T {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching { json.decodeFromString(ApiErrorDto.serializer(), text) }.getOrNull()
                    throw ApiException(
                        code = err?.code?.takeIf { it.isNotBlank() } ?: "http_${response.code}",
                        message = err?.message ?: "服务器返回错误（HTTP ${response.code}）",
                    )
                }
                return decode(text)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("network_error", describeNetworkFailure(e))
        }
    }

    /** 被网络安全策略拦截的明文请求会抛 UnknownServiceException，单独给出可操作的提示。 */
    private fun describeNetworkFailure(e: IOException): String {
        if (e.message.orEmpty().contains("CLEARTEXT", ignoreCase = true)) {
            return "该地址不允许明文访问（http）。请改用 https://，" +
                "或在 res/xml/network_security_config.xml 中把该内网地址加入白名单。"
        }
        return "无法连接解析服务：请确认后端已启动、设置页地址正确（真机用局域网 IP 或公网地址，" +
            "10.0.2.2 仅模拟器可用），且手机与服务器网络互通"
    }
}
