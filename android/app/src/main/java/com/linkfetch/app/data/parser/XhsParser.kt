package com.linkfetch.app.data.parser

import com.linkfetch.app.data.model.MediaItemDto
import com.linkfetch.app.data.model.ParseResponseDto
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/** 小红书本地直连解析：短链展开 -> explore 页 -> 提取原图（fileId）、无水印视频与 Live 图。 */
class XhsParser(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val noteIdRegex = Regex("/(?:explore|discovery/item|item)/([0-9a-fA-F]{8,})")

    suspend fun parse(url: String, cookie: String? = null): ParseResponseDto = withContext(Dispatchers.IO) {
        val headers = mobileHeaders(cookie)
        val shortResp = client.newCall(Request.Builder().url(url).headers(headers).build()).execute().use { it }
        val finalUrl = shortResp.request.url
        val idMatch = noteIdRegex.find(finalUrl.toString())
            ?: throw LocalParseException("parse_failed", "无法从小红书链接中提取笔记 ID")
        val noteId = idMatch.groupValues[1]
        val token = finalUrl.queryParameter("xsec_token").orEmpty()

        val exploreUrl = finalUrl.newBuilder()
            .encodedPath("/explore/$noteId")
            .query(null)
            .addQueryParameter("xsec_token", token)
            .addQueryParameter("xsec_source", "pc_feed")
            .build()
        val html = client.newCall(Request.Builder().url(exploreUrl).headers(headers).build()).execute().use { it ->
            if (it.code in listOf(403, 429, 461)) {
                throw LocalParseException("rate_limited", "小红书触发了风控，请稍后重试（可在设置中配置 Cookie）")
            }
            it.body?.string().orEmpty()
        }
        val blob = HtmlJsonExtractor.extractJsonObject(html, "window.__INITIAL_STATE__")
            ?: throw LocalParseException("parse_failed", "小红书页面结构变化，无法解析（请更新 App）")
        val root = runCatching { json.parseToJsonElement(blob) as JsonObject }
            .getOrElse { throw LocalParseException("parse_failed", "小红书页面数据解析失败") }

        val note = findNote(root, noteId)
            ?: throw LocalParseException("parse_failed", "未能在页面中找到笔记数据（笔记可能已删除或需要登录）")
        buildResponse(note)
    }

    private fun findNote(root: JsonObject, noteId: String): JsonObject? {
        // 新版结构：noteData.data.noteData
        root["noteData"]?.jsonObjectOrNull()?.get("data")?.jsonObjectOrNull()?.get("noteData")?.jsonObjectOrNull()
            ?.let { return it }
        // 旧版结构：note.noteDetailMap[<id>].note
        return root["note"]?.jsonObjectOrNull()?.get("noteDetailMap")?.jsonObjectOrNull()?.get(noteId)
            ?.jsonObjectOrNull()?.get("note")?.jsonObjectOrNull()
    }

    private fun buildResponse(note: JsonObject): ParseResponseDto {
        val title = note["title"]?.stringOrNull()
            ?: note["desc"]?.stringOrNull()
            ?: "小红书笔记"
        val user = note["user"]?.jsonObjectOrNull()
        val author = user?.get("nickName")?.stringOrNull() ?: user?.get("nickname")?.stringOrNull()

        val medias = mutableListOf<MediaItemDto>()
        (note["imageList"] as? JsonArray)?.forEach { element ->
            val image = element.jsonObjectOrNull() ?: return@forEach
            val infoUrl = (image["infoList"] as? JsonArray)?.firstOrNull()?.jsonObjectOrNull()?.get("url")?.stringOrNull()
            val raw = image["url"]?.stringOrNull() ?: infoUrl ?: image["urlDefault"]?.stringOrNull() ?: ""
            val url = buildImageUrl(image, raw)
            if (url.isNotBlank()) {
                val liveUrl = pickLivePhotoUrl(image)
                medias += MediaItemDto(
                    kind = "image",
                    url = url,
                    quality = "original",
                    live = !liveUrl.isNullOrBlank(),
                    liveUrl = liveUrl,
                )
            }
        }

        val video = note["video"]?.jsonObjectOrNull()
        val videoUrl = pickVideoUrl(video)
        val cover = video?.get("cover")?.jsonObjectOrNull()?.get("urlDefault")?.stringOrNull()
        if (!videoUrl.isNullOrBlank()) {
            medias.add(0, MediaItemDto(kind = "video", url = videoUrl, cover = cover, quality = "original"))
        }
        if (medias.isEmpty()) {
            throw LocalParseException("parse_failed", "该笔记不包含图片或视频")
        }
        return ParseResponseDto(
            platform = "xhs",
            title = title,
            author = author,
            type = mediaType(medias),
            medias = medias,
        )
    }

    /**
     * 优先使用 fileId 指向的原图对象并转成 JPEG（原分辨率、无水印）：
     * https://sns-img-{region}.xhscdn.com/{fileId}?imageView2/0/format/jpg
     */
    private fun buildImageUrl(image: JsonObject, raw: String): String {
        val fileId = image["fileId"]?.stringOrNull()
        if (!fileId.isNullOrBlank()) {
            val host = raw.substringAfter("//", "").substringBefore("/")
                .replace("sns-webpic", "sns-img")
                .ifBlank { "sns-img-qc.xhscdn.com" }
            return "https://$host/$fileId?imageView2/0/format/jpg"
        }
        return cleanImageUrl(raw)
    }

    /**
     * 提取 Live 图短视频直链。不同时期的小红书数据结构不一致，按优先级依次尝试：
     * 1. imageList[i].livePhoto.media.stream.{h264|h265|av1}[0].masterUrl
     * 2. imageList[i].livePhoto.stream.{h264|h265|av1}[0].masterUrl
     * 3. imageList[i].stream.{h264|h265|av1}[0].masterUrl（旧版本）
     * 4. imageList[i].livePhotoVideo / imageList[i].liveUrl / livePhoto.video（直接给 URL 的兜底字段）
     */
    private fun pickLivePhotoUrl(image: JsonObject): String? {
        val livePhoto = image["livePhoto"]?.jsonObjectOrNull()
        livePhoto?.let { lp ->
            streamMasterUrl(lp)?.let { return it }
            lp["media"]?.jsonObjectOrNull()?.let { streamMasterUrl(it) }?.let { return it }
            lp["livePhotoVideo"]?.stringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            lp["liveUrl"]?.stringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            lp["video"]?.stringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        streamMasterUrl(image)?.let { return it }
        image["livePhotoVideo"]?.stringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        image["liveUrl"]?.stringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun streamMasterUrl(node: JsonObject): String? {
        val stream = node["stream"]?.jsonObjectOrNull() ?: return null
        for (key in listOf("h264", "h265", "av1")) {
            val item = (stream[key] as? JsonArray)?.firstOrNull()?.jsonObjectOrNull() ?: continue
            item["masterUrl"]?.stringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            item["url"]?.stringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun pickVideoUrl(video: JsonObject?): String? {
        if (video == null) return null
        val consumer = video["consumer"]?.jsonObjectOrNull()
        val originKey = consumer?.get("originVideoKey")?.stringOrNull()
            ?: consumer?.get("origin_video_key")?.stringOrNull()
        if (!originKey.isNullOrBlank()) return "https://sns-video-bd.xhscdn.com/$originKey"
        val h264 = video["media"]?.jsonObjectOrNull()?.get("stream")?.jsonObjectOrNull()?.get("h264") as? JsonArray
        h264?.firstOrNull()?.jsonObjectOrNull()?.get("masterUrl")?.stringOrNull()?.let { return it }
        return video["video"]?.jsonObjectOrNull()?.get("url")?.stringOrNull() ?: video["url"]?.stringOrNull()
    }

    private fun mediaType(medias: List<MediaItemDto>): String {
        val hasVideo = medias.any { it.isVideo }
        val hasImage = medias.any { !it.isVideo }
        return when {
            hasVideo && hasImage -> "mixed"
            hasVideo -> "video"
            else -> "image"
        }
    }

    private fun mobileHeaders(cookie: String?): Headers {
        val builder = Headers.Builder()
            .add(
                "User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            )
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .add("Accept-Language", "zh-CN,zh;q=0.9")
            .add("Referer", "https://www.xiaohongshu.com/")
        if (!cookie.isNullOrBlank()) builder.add("Cookie", cookie)
        return builder.build()
    }
}

internal fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** 字段可能是字符串，也可能是 {"url": "..."} 对象（如微博 large）。 */
internal fun JsonElement.mediaUrlOrNull(): String? =
    stringOrNull() ?: jsonObjectOrNull()?.get("url")?.stringOrNull()

/**
 * 旧版小红书图片 URL 清洗：带 watermark 的后缀才需要去掉，并切回原图域名。
 * 新版 URL 由 buildImageUrl 处理（fileId 原图），不经过这里。
 */
internal fun cleanImageUrl(url: String): String {
    if (url.isBlank()) return url
    return if (url.contains("watermark", ignoreCase = true)) {
        var result = url.substringBefore("!")
        result = result.substringBefore("?")
        result.replace("sns-webpic", "sns-img")
    } else {
        url
    }
}
