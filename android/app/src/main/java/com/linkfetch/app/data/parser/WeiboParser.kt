package com.linkfetch.app.data.parser

import com.linkfetch.app.data.model.MediaItemDto
import com.linkfetch.app.data.model.ParseResponseDto
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** 微博本地直连解析：短链展开 -> statuses/show 接口 -> 视频直链、多图原图与 Live 图。 */
class WeiboParser(
    private val apiBase: String = "https://m.weibo.cn",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val statusRegex = Regex("/(?:status|detail)/([A-Za-z0-9_]+)")
    // 桌面端 /weibo.com/<uid>/<id>，以及任意域名下 /<数字>/<id> 的形态
    private val uidStatusRegex = Regex("/(\\d+)/([A-Za-z0-9_]+)")
    private val fidRegex = Regex("[?&]fid=1034:(\\d+)")
    private val htmlTagRegex = Regex("<[^>]+>")

    suspend fun parse(url: String, cookie: String? = null): ParseResponseDto = withContext(Dispatchers.IO) {
        val headers = headers(cookie)
        // 优先从原始链接直接提取（桌面端 weibo.com/<uid>/<id>）
        var statusId = extractStatusId(url)
        if (statusId == null) {
            // 短链需要展开；检查整条重定向链中的 URL
            val resp = client.newCall(Request.Builder().url(url).headers(headers).build()).execute().use { it }
            var node: Response? = resp
            while (node != null && statusId == null) {
                statusId = extractStatusId(node.request.url.toString())
                node = node.priorResponse
            }
        }
        if (statusId == null) {
            throw LocalParseException("parse_failed", "无法从微博链接中提取微博 ID")
        }

        val apiUrl = "$apiBase/statuses/show?id=$statusId"
        val data = client.newCall(Request.Builder().url(apiUrl).headers(headers).build()).execute().use { it ->
            if (it.code in listOf(403, 429)) {
                throw LocalParseException("rate_limited", "微博触发了风控，请稍后重试（可在设置中配置 Cookie）")
            }
            val root = runCatching { json.parseToJsonElement(it.body?.string().orEmpty()) as JsonObject }.getOrNull()
                ?: throw LocalParseException("parse_failed", "微博接口返回异常")
            root["data"]?.jsonObjectOrNull()
                ?: throw LocalParseException("parse_failed", "微博不存在或已删除")
        }
        buildResponse(data)
    }

    private fun extractStatusId(url: String): String? =
        statusRegex.find(url)?.groupValues?.get(1)
            ?: uidStatusRegex.find(url)?.groupValues?.get(2)
            ?: fidRegex.find(url)?.groupValues?.get(1)

    private fun buildResponse(data: JsonObject): ParseResponseDto {
        val title = htmlTagRegex.replace(data["text"]?.stringOrNull().orEmpty(), "").trim().take(100)
            .ifBlank { "微博正文" }
        val author = data["user"]?.jsonObjectOrNull()?.get("screen_name")?.stringOrNull()
        val medias = mutableListOf<MediaItemDto>()

        val pageInfo = data["page_info"]?.jsonObjectOrNull()
        val mediaInfo = pageInfo?.get("media_info")?.jsonObjectOrNull()
        val videoUrl = mediaInfo?.get("mp4_hd_url")?.stringOrNull()
            ?: mediaInfo?.get("mp4_sd_url")?.stringOrNull()
            ?: mediaInfo?.get("stream_url_hd")?.stringOrNull()
            ?: mediaInfo?.get("stream_url")?.stringOrNull()
        if (!videoUrl.isNullOrBlank()) {
            val cover = pageInfo?.get("page_pic")?.stringOrNull()
            medias += MediaItemDto(kind = "video", url = videoUrl, cover = cover, quality = "hd")
        }

        (data["pics"] as? JsonArray)?.forEach { element ->
            val pic = element.jsonObjectOrNull() ?: return@forEach
            // original 可能是字符串；large 可能是字符串或 {size, url} 对象（mw2000 大图）
            val url = pic["original"]?.mediaUrlOrNull()
                ?: pic["large"]?.mediaUrlOrNull()
                ?: pic["url"]?.stringOrNull()
            if (!url.isNullOrBlank()) {
                // Live 图：type=livephoto 时 videoSrc 为 .mov 短视频直链
                val liveUrl = if (pic["type"]?.stringOrNull() == "livephoto") {
                    pic["videoSrc"]?.stringOrNull()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                medias += MediaItemDto(
                    kind = "image",
                    url = url,
                    quality = "original",
                    live = !liveUrl.isNullOrBlank(),
                    liveUrl = liveUrl,
                )
            }
        }

        if (medias.isEmpty()) {
            throw LocalParseException("parse_failed", "该微博不包含图片或视频")
        }
        val hasVideo = medias.any { it.isVideo }
        val hasImage = medias.any { !it.isVideo }
        return ParseResponseDto(
            platform = "weibo",
            title = title,
            author = author,
            type = when {
                hasVideo && hasImage -> "mixed"
                hasVideo -> "video"
                else -> "image"
            },
            medias = medias,
        )
    }

    private fun headers(cookie: String?): Headers {
        val builder = Headers.Builder()
            .add(
                "User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            )
            .add("Accept", "application/json, text/plain, */*")
            .add("Accept-Language", "zh-CN,zh;q=0.9")
            .add("Referer", "https://m.weibo.cn/")
            // m.weibo.cn 接口要求该头，否则返回访客系统页面
            .add("X-Requested-With", "XMLHttpRequest")
        if (!cookie.isNullOrBlank()) builder.add("Cookie", cookie)
        return builder.build()
    }
}
