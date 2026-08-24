package com.linkfetch.app.data.parser

import com.linkfetch.app.data.model.MediaItemDto
import com.linkfetch.app.data.model.ParseResponseDto
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** 抖音本地直连解析：短链展开 -> 分享页 -> 无水印直链（含实况图），多路径回退。 */
class DouyinParser(
    private val shareBases: List<String> = listOf("https://m.douyin.com", "https://www.iesdouyin.com"),
    private val pcDetailApi: String = "https://www.douyin.com/aweme/v1/web/aweme/detail/",
    private val notePageUrl: String = "https://www.douyin.com/note/%s",
    private val noteShareUrl: String = "https://m.douyin.com/share/note/%s/",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val itemRegex = Regex("/(?:video|note|slides)/(\\d+)")
    private val playUrl = "https://aweme.snssdk.com/aweme/v1/play/?video_id=%s&ratio=1080p&line=0"
    private val spiderUa =
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
    private val ldJsonRegex = Regex(
        """<script[^>]*type=["\']application/ld\+json["\'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    suspend fun parse(url: String, cookie: String? = null): ParseResponseDto = withContext(Dispatchers.IO) {
        val headers = headers(cookie)
        val resp = client.newCall(Request.Builder().url(url).headers(headers).build()).execute().use { it }
        // 重定向链中任意 URL 都可能带作品 ID（移动 UA 可能落在 iesdouyin.com/share/slides/...）
        var contentId: String? = null
        var kind = "note"
        var node: Response? = resp
        while (node != null && contentId == null) {
            val nodeUrl = node.request.url.toString()
            val match = itemRegex.find(nodeUrl)
            if (match != null) {
                contentId = match.groupValues[1]
                if (nodeUrl.contains("/video/")) kind = "video"
            }
            node = node.priorResponse
        }
        if (contentId == null) {
            throw LocalParseException("parse_failed", "无法从抖音链接中提取视频 / 笔记 ID")
        }

        // 依次尝试多个分享页路径（移动站优先，图文笔记另试 slides 路径）
        val attempts = mutableListOf<String>()
        for (base in shareBases) {
            attempts += "$base/share/$kind/$contentId/"
            if (kind == "note") attempts += "$base/share/slides/$contentId/"
        }
        var lastError: LocalParseException? = null
        for (shareUrl in attempts) {
            try {
                val html = client.newCall(Request.Builder().url(shareUrl).headers(headers).build())
                    .execute().use { it ->
                        if (it.code in listOf(403, 429)) {
                            throw LocalParseException("rate_limited", "抖音触发了风控，请稍后重试")
                        }
                        it.body?.string().orEmpty()
                    }
                val blob = HtmlJsonExtractor.extractJsonObject(html, "window._ROUTER_DATA")
                val root = blob?.let { runCatching { json.parseToJsonElement(it) as JsonObject }.getOrNull() }
                val item = root?.let { findItem(it) }
                if (item != null) {
                    return@withContext buildResponse(item)
                }
            } catch (e: LocalParseException) {
                lastError = e
            }
        }

        // 分享页全部失败后的新回退：
        // 1) PC 详情接口（爬虫 UA 免签名）——视频作品可拿到无水印播放直链；
        // 2) SEO 页 JSON-LD——图文笔记可绕过 images_base 过滤；
        // 3) 旧的 iteminfo 接口兜底。
        val spiderHeaders = spiderHeaders(cookie)
        try {
            val detailUrl = "$pcDetailApi?aweme_id=$contentId"
            val detailRoot = client.newCall(Request.Builder().url(detailUrl).headers(spiderHeaders).build())
                .execute().use { it ->
                    if (it.code in listOf(403, 429)) {
                        throw LocalParseException("rate_limited", "抖音触发了风控，请稍后重试")
                    }
                    runCatching { json.parseToJsonElement(it.body?.string().orEmpty()) as JsonObject }.getOrNull()
                }
            val pcDetail = detailRoot?.get("aweme_detail")?.jsonObjectOrNull()
            if (pcDetail != null) {
                return@withContext buildResponse(pcDetail)
            }
        } catch (e: LocalParseException) {
            throw e
        }

        if (kind == "note") {
            try {
                return@withContext parseNoteViaSeo(contentId, spiderHeaders)
            } catch (e: LocalParseException) {
                lastError = e
            }
        }

        val fallbackRoot = shareBases.firstOrNull()?.let { base ->
            val fallbackUrl = "$base/web/api/v2/aweme/iteminfo/?item_ids=$contentId"
            client.newCall(Request.Builder().url(fallbackUrl).headers(headers).build()).execute().use { it ->
                if (it.code in listOf(403, 429)) {
                    throw LocalParseException("rate_limited", "抖音触发了风控，请稍后重试")
                }
                runCatching { json.parseToJsonElement(it.body?.string().orEmpty()) as JsonObject }.getOrNull()
            }
        }
        val itemInfo = (fallbackRoot?.get("item_list") as? JsonArray)?.firstOrNull()?.jsonObjectOrNull()
        if (itemInfo != null) {
            return@withContext buildResponse(itemInfo)
        }
        throw lastError ?: LocalParseException("parse_failed", "抖音解析失败，作品可能已删除")
    }

    private fun findItem(root: JsonObject): JsonObject? {
        val loader = root["loaderData"]?.jsonObjectOrNull() ?: return null
        for (value in loader.values) {
            val itemList = value.jsonObjectOrNull()?.get("videoInfoRes")?.jsonObjectOrNull()?.get("item_list") as? JsonArray
            if (!itemList.isNullOrEmpty()) return itemList.first().jsonObjectOrNull()
            val noteDetail = value.jsonObjectOrNull()?.get("noteInfoRes")?.jsonObjectOrNull()
                ?.get("note")?.jsonObjectOrNull()?.get("note_detail")?.jsonObjectOrNull()
            if (noteDetail != null) return noteDetail
        }
        return null
    }

    private suspend fun parseNoteViaSeo(contentId: String, headers: Headers): ParseResponseDto {
        var lastError: LocalParseException? = null
        for (url in listOf(noteShareUrl.format(contentId), notePageUrl.format(contentId))) {
            try {
                val html = client.newCall(Request.Builder().url(url).headers(headers).build())
                    .execute().use { it ->
                        if (it.code in listOf(403, 429)) {
                            throw LocalParseException("rate_limited", "抖音触发了风控，请稍后重试")
                        }
                        it.body?.string().orEmpty()
                    }
                val item = ldJsonToItem(extractLdJson(html))
                if (item != null) {
                    return buildResponse(item)
                }
            } catch (e: LocalParseException) {
                lastError = e
            }
        }
        throw lastError ?: LocalParseException("parse_failed", "抖音笔记页无数据（可能被风控拦截）")
    }

    private fun extractLdJson(html: String): JsonObject? {
        for (match in ldJsonRegex.findAll(html)) {
            val blob = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (blob.isBlank()) continue
            val root = runCatching { json.parseToJsonElement(blob) as JsonObject }.getOrNull() ?: continue
            val type = root["@type"]?.stringOrNull().orEmpty().lowercase()
            val images = root["image"] as? JsonArray
            if (type.contains("article") && !images.isNullOrEmpty()) return root
        }
        return null
    }

    private fun ldJsonToItem(ld: JsonObject?): JsonObject? {
        if (ld == null) return null
        val imageUrls = (ld["image"] as? JsonArray)
            ?.mapNotNull { it.stringOrNull() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (imageUrls.isEmpty()) return null
        val authorName = ld["author"]?.jsonObjectOrNull()?.get("name")?.stringOrNull()
            ?: ld["creator"]?.jsonObjectOrNull()?.get("name")?.stringOrNull()
        val desc = ld["articleBody"]?.stringOrNull()
            ?: ld["headline"]?.stringOrNull()
            ?: ld["name"]?.stringOrNull()
            ?: "抖音作品"
        return buildJsonObject {
            put("desc", desc)
            if (authorName != null) {
                put("author", buildJsonObject { put("nickname", authorName) })
            }
            put(
                "images",
                buildJsonArray {
                    imageUrls.forEach { url ->
                        add(
                            buildJsonObject {
                                put("url_list", buildJsonArray { add(JsonPrimitive(url)) })
                            },
                        )
                    }
                },
            )
        }
    }

    private fun spiderHeaders(cookie: String?): Headers {
        val builder = Headers.Builder()
            .add("User-Agent", spiderUa)
            .add("Accept", "application/json, text/plain, */*")
            .add("Accept-Language", "zh-CN,zh;q=0.9")
            .add("Referer", "https://www.douyin.com/")
        if (!cookie.isNullOrBlank()) builder.add("Cookie", cookie)
        return builder.build()
    }

    private fun buildResponse(item: JsonObject): ParseResponseDto {
        val desc = item["desc"]?.stringOrNull().orEmpty().ifBlank {
            item["video_text"]?.stringOrNull().orEmpty()
        }.ifBlank { "抖音作品" }
        val author = item["author"]?.jsonObjectOrNull()?.get("nickname")?.stringOrNull()
        val medias = mutableListOf<MediaItemDto>()

        (item["images"] as? JsonArray)?.forEach { element ->
            val image = element.jsonObjectOrNull() ?: return@forEach
            val urlList = image["url_list"] as? JsonArray
            val first = urlList?.firstOrNull()?.stringOrNull()
            if (!first.isNullOrBlank()) {
                val liveUrl = pickLivePhotoUrl(image)
                medias += MediaItemDto(
                    kind = "image",
                    url = first,
                    quality = "original",
                    live = !liveUrl.isNullOrBlank(),
                    liveUrl = liveUrl,
                )
            }
        }

        val video = item["video"]?.jsonObjectOrNull()
        val playUri = video?.get("play_addr")?.jsonObjectOrNull()?.get("uri")?.stringOrNull()
        val videoUrl = when {
            playUri.isNullOrBlank() -> null
            playUri.startsWith("http") -> playUri
            else -> playUrl.format(playUri)
        }
        if (!videoUrl.isNullOrBlank()) {
            val cover = (video?.get("cover")?.jsonObjectOrNull()?.get("url_list") as? JsonArray)
                ?.firstOrNull()?.stringOrNull()
            medias.add(0, MediaItemDto(kind = "video", url = videoUrl, cover = cover, quality = "1080p"))
        }
        if (medias.isEmpty()) {
            throw LocalParseException("parse_failed", "该作品不包含图片或视频")
        }
        val hasVideo = medias.any { it.isVideo }
        val hasImage = medias.any { !it.isVideo }
        return ParseResponseDto(
            platform = "douyin",
            title = desc,
            author = author,
            type = when {
                hasVideo && hasImage -> "mixed"
                hasVideo -> "video"
                else -> "image"
            },
            medias = medias,
        )
    }

    /**
     * 抖音图集中的实况图：每张图片的 video 字段包含一段短视频。
     * 优先取 play_addr.url_list 中不带水印（playwm）的直链；只有 uri 时按无水印播放地址规则拼接。
     */
    private fun pickLivePhotoUrl(image: JsonObject): String? {
        val video = image["video"]?.jsonObjectOrNull() ?: return null
        val playAddr = video["play_addr"]?.jsonObjectOrNull() ?: return null
        val urlList = playAddr["url_list"] as? JsonArray
        if (!urlList.isNullOrEmpty()) {
            val candidates = urlList.mapNotNull { it.stringOrNull() }
            candidates.firstOrNull { it.startsWith("http") && !it.contains("playwm", ignoreCase = true) }
                ?.let { return it }
            candidates.firstOrNull { it.startsWith("http") }?.let { return it }
        }
        val uri = playAddr["uri"]?.stringOrNull()
        if (!uri.isNullOrBlank() && uri.startsWith("http")) return uri
        if (!uri.isNullOrBlank()) return playUrl.format(uri)
        return null
    }

    private fun headers(cookie: String?): Headers {
        val builder = Headers.Builder()
            .add(
                "User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            )
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .add("Accept-Language", "zh-CN,zh;q=0.9")
            .add("Referer", "https://www.douyin.com/")
        if (!cookie.isNullOrBlank()) builder.add("Cookie", cookie)
        return builder.build()
    }
}
