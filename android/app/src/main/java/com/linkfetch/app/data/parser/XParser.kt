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
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * X (Twitter) 本地直连解析：提取推文 ID -> syndication 半公开接口 -> 原图与最高画质 mp4。
 * 说明：该接口不校验 token；需要能访问海外网络（x.com / twimg.com）。
 * 长视频（amplify / unified_card 卡片）会通过 card.binding_values 返回 HLS/VMAP，这里一并处理；
 * 若普通 UA 拿不到媒体，会用 Googlebot UA 重试一次（yt-dlp 的实践经验）；
 * 若 syndication 返回 TweetTombstone（受限内容，长视频常见）或两次都拿不到媒体，
 * 自动回退 vxtwitter / fxtwitter 第三方接口。
 */
class XParser(
    private val syndicationBase: String = "https://cdn.syndication.twimg.com",
    private val vxtwitterBase: String = "https://api.vxtwitter.com",
    private val fxtwitterBase: String = "https://api.fxtwitter.com",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val statusRegex = Regex("/(?:i/)?status(?:es)?/(\\d+)")
    private val sizeSuffixRegex = Regex("_(large|thumb|small|medium|orig)(?=\\.(?:jpg|jpeg|png|webp|gif))")

    suspend fun parse(url: String, cookie: String? = null): ParseResponseDto = withContext(Dispatchers.IO) {
        val headers = headers()
        var tweetId = statusRegex.find(url)?.groupValues?.get(1)
        if (tweetId == null) {
            val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: ""
            if (host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")) {
                throw LocalParseException("parse_failed", "无法从 X 链接中提取推文 ID")
            }
            // t.co 短链：沿重定向链查找推文 ID
            val resp = client.newCall(Request.Builder().url(url).headers(headers).build()).execute().use { it }
            var node: Response? = resp
            while (node != null && tweetId == null) {
                tweetId = statusRegex.find(node.request.url.toString())?.groupValues?.get(1)
                node = node.priorResponse
            }
        }
        if (tweetId == null) {
            throw LocalParseException("parse_failed", "无法从 X 链接中提取推文 ID")
        }

        var lastBody: String? = null
        // 第一遍普通 UA；若媒体为空（长视频常见），第二遍用 Googlebot UA 重试
        for (attempt in 0..1) {
            val body = fetchSyndication(tweetId, googlebot = attempt == 1)
            lastBody = body
            val root = runCatching { json.parseToJsonElement(body) as JsonObject }
                .getOrElse {
                    throw LocalParseException("parse_failed", "X 接口返回异常", rawBody = body.take(20000))
                }
            root["error"]?.stringOrNull()?.let { error ->
                // 墓碑等受限响应也可能带 error；此时交给第三方回退处理
                if (!isTombstone(root)) {
                    throw LocalParseException(
                        "parse_failed",
                        if (error.contains("no status", ignoreCase = true) || error.contains("invalid", ignoreCase = true)) {
                            "推文不存在或已删除"
                        } else {
                            "X 解析失败：$error"
                        },
                        rawBody = body.take(20000),
                    )
                }
            }
            // TweetTombstone：syndication 接口拒绝展示该推文（长视频常见），直接走第三方回退
            if (isTombstone(root)) break
            try {
                return@withContext buildResponse(root)
            } catch (e: NoMediaException) {
                // 继续下一次尝试（Googlebot UA）
            }
        }

        // 第三方回退：vxtwitter / fxtwitter 能拿到 syndication 拿不到的长视频等受限内容
        tryFallback(tweetId)?.let { return@withContext it }

        throw LocalParseException(
            "parse_failed",
            "该推文不包含图片或视频",
            rawBody = lastBody?.take(20000),
        )
    }

    private suspend fun fetchSyndication(tweetId: String, googlebot: Boolean): String {
        val apiUrl = "$syndicationBase/tweet-result?id=$tweetId&lang=zh&token=7"
        val requestHeaders = if (googlebot) googlebotHeaders() else headers()
        return client.newCall(Request.Builder().url(apiUrl).headers(requestHeaders).build()).execute().use { resp ->
            when (resp.code) {
                404 -> throw LocalParseException("parse_failed", "推文不存在或已删除")
                403, 429 -> throw LocalParseException("rate_limited", "X 触发了风控，请稍后重试")
            }
            resp.body?.string().orEmpty()
        }
    }

    private suspend fun buildResponse(root: JsonObject): ParseResponseDto {
        val rawText = root["full_text"]?.stringOrNull() ?: root["text"]?.stringOrNull().orEmpty()
        val title = rawText.replace(Regex("\\s+"), " ").trim().take(100).ifBlank { "推文" }
        val user = root["user"]?.jsonObjectOrNull()
        val author = user?.get("name")?.stringOrNull() ?: user?.get("screen_name")?.stringOrNull()

        val medias = mutableListOf<MediaItemDto>()
        // 主推文无媒体时回退到被引用/转发的推文
        val candidates = listOfNotNull(
            root,
            root["quoted_tweet"]?.jsonObjectOrNull(),
            root["retweeted_tweet"]?.jsonObjectOrNull(),
        )
        for (candidate in candidates) {
            val list = candidate["mediaDetails"] as? JsonArray
            if (!list.isNullOrEmpty()) {
                collectMedia(list, medias)
                break
            }
            if (collectCardMedia(candidate, medias)) break
        }
        if (medias.isEmpty()) {
            // 兜底：部分响应把媒体放在顶层 photos / video 字段
            collectTopLevelMedia(root, medias)
        }
        if (medias.isEmpty()) {
            throw NoMediaException()
        }

        val hasVideo = medias.any { it.isVideo }
        val hasImage = medias.any { !it.isVideo }
        return ParseResponseDto(
            platform = "x",
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

    private fun collectMedia(list: JsonArray, medias: MutableList<MediaItemDto>) {
        collectMedia(list.mapNotNull { it.jsonObjectOrNull() }, medias)
    }

    private fun collectMedia(items: List<JsonObject>, medias: MutableList<MediaItemDto>) {
        for (media in items) {
            val baseUrl = media["media_url_https"]?.stringOrNull()
            when (media["type"]?.stringOrNull()) {
                "photo" -> {
                    if (baseUrl != null) {
                        medias += MediaItemDto(
                            kind = "image",
                            url = originalImageUrl(baseUrl),
                            quality = "original",
                        )
                    }
                }
                "video", "animated_gif" -> {
                    if (baseUrl == null) continue
                    val videoUrl = pickVideoUrl(media["video_info"]?.jsonObjectOrNull()) ?: continue
                    medias += MediaItemDto(
                        kind = "video",
                        url = videoUrl,
                        cover = baseUrl,
                        quality = if (media["type"]?.stringOrNull() == "animated_gif") "gif" else "hd",
                    )
                }
            }
        }
    }

    /**
     * 从推文 card 中提取媒体：
     * - unified_card：binding_values.unified_card 是 JSON 字符串，内含 media_entities（与 mediaDetails 同构）
     * - amplify / promo_video_website 等视频卡片：player_hls_url / player_stream_url / amplify_url_vmap（VMAP）
     */
    private suspend fun collectCardMedia(candidate: JsonObject, medias: MutableList<MediaItemDto>): Boolean {
        val card = candidate["card"]?.jsonObjectOrNull() ?: return false
        val cardName = card["name"]?.stringOrNull()?.substringAfterLast(':') ?: return false
        val binding = card["binding_values"]?.jsonObjectOrNull() ?: return false

        if (cardName.contains("unified_card")) {
            val unifiedJson = bindingString(binding, "unified_card") ?: return false
            val unified = runCatching { json.parseToJsonElement(unifiedJson) as JsonObject }.getOrNull()
                ?: return false
            val entities = unified["media_entities"]?.jsonObjectOrNull() ?: return false
            val items = entities.values.mapNotNull { it.jsonObjectOrNull() }
            if (items.isEmpty()) return false
            collectMedia(items, medias)
            return medias.isNotEmpty()
        }

        val hls = bindingString(binding, "player_hls_url")
        val stream = bindingString(binding, "player_stream_url")
        val vmap = bindingString(binding, "amplify_url_vmap")
        val cover = bindingImage(binding, "player_image")
        val url = hls
            ?: stream?.takeIf { it.contains(".m3u8", ignoreCase = true) }
            ?: vmap?.let { resolveVmap(it) }
            ?: stream?.let { resolveVmap(it) }
        if (url != null) {
            medias += MediaItemDto(kind = "video", url = url, cover = cover, quality = "hd")
            return true
        }
        return false
    }

    /** VMAP XML 中提取媒体直链：优先 m3u8，其次 mp4。 */
    private suspend fun resolveVmap(url: String): String? {
        val body = try {
            client.newCall(Request.Builder().url(url).headers(headers()).build())
                .execute().use { it.body?.string().orEmpty() }
        } catch (e: Exception) {
            return null
        }
        val candidates = mutableListOf<String>()
        Regex("<MediaFile[^>]*>\\s*(.*?)\\s*</MediaFile>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(body).forEach { m ->
                var value = m.groupValues[1].trim()
                Regex("<!\\[CDATA\\[\\s*(.*?)\\s*\\]\\]>", RegexOption.DOT_MATCHES_ALL)
                    .find(value)?.let { value = it.groupValues[1].trim() }
                if (value.isNotEmpty()) candidates += value
            }
        return candidates.firstOrNull { it.contains(".m3u8", ignoreCase = true) }
            ?: candidates.firstOrNull { it.contains(".mp4", ignoreCase = true) }
            ?: candidates.firstOrNull()
    }

    /** 部分 syndication 响应把媒体放在顶层 photos / video 字段（you-get 等也按此解析）。 */
    private fun collectTopLevelMedia(root: JsonObject, medias: MutableList<MediaItemDto>) {
        val photos = root["photos"] as? JsonArray
        if (!photos.isNullOrEmpty()) {
            for (element in photos) {
                val photo = element.jsonObjectOrNull() ?: continue
                val url = photo["url"]?.stringOrNull()
                    ?: photo["media_url_https"]?.stringOrNull()
                    ?: continue
                medias += MediaItemDto(kind = "image", url = originalImageUrl(url), quality = "original")
            }
        }
        val video = root["video"]?.jsonObjectOrNull()
        if (video != null) {
            val variants = video["variants"] as? JsonArray
            val url = if (!variants.isNullOrEmpty()) pickVideoUrl(variants) else video["url"]?.stringOrNull()
            if (url != null) {
                val cover = video["poster"]?.stringOrNull()
                    ?: video["media_url_https"]?.stringOrNull()
                    ?: video["thumbnail"]?.stringOrNull()
                medias += MediaItemDto(kind = "video", url = url, cover = cover, quality = "hd")
            }
        }
    }

    /** pbs.twimg.com 原图：去掉尺寸后缀并追加 name=orig 参数。 */
    private fun originalImageUrl(base: String): String {
        val cleaned = sizeSuffixRegex.replace(base, "")
        return if (cleaned.contains("?")) "$cleaned&name=orig" else "$cleaned?name=orig"
    }

    /** 优先选 bitrate 最高的 mp4；没有 mp4 时回退 HLS。 */
    private fun pickVideoUrl(videoInfo: JsonObject?): String? {
        val variants = videoInfo?.get("variants") as? JsonArray ?: return null
        return pickVideoUrl(variants)
    }

    private fun pickVideoUrl(variants: JsonArray): String? {
        var best: String? = null
        var bestBitrate = -1
        var hls: String? = null
        for (variant in variants) {
            val item = variant.jsonObjectOrNull() ?: continue
            val contentType = item["content_type"]?.stringOrNull() ?: continue
            val url = item["url"]?.stringOrNull() ?: continue
            when (contentType) {
                "video/mp4" -> {
                    val bitrate = item.intOrNull("bitrate") ?: 0
                    if (bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        best = url
                    }
                }
                "application/x-mpegURL" -> if (hls == null) hls = url
            }
        }
        return best ?: hls
    }

    // ---- 第三方回退（vxtwitter / fxtwitter）----

    /** TweetTombstone：X 拒绝通过 syndication 嵌入展示该推文（长视频常见）。 */
    private fun isTombstone(root: JsonObject): Boolean =
        root["__typename"]?.stringOrNull() == "TweetTombstone" || root["tombstone"] != null

    /** 依次尝试 vxtwitter、fxtwitter；成功返回解析结果，全部失败返回 null。 */
    private suspend fun tryFallback(tweetId: String): ParseResponseDto? {
        fetchJson("$vxtwitterBase/i/status/$tweetId")?.let { root ->
            buildVxResponse(root)?.let { return it }
        }
        fetchJson("$fxtwitterBase/status/$tweetId")?.let { root ->
            buildFxResponse(root)?.let { return it }
        }
        return null
    }

    private suspend fun fetchJson(url: String): JsonObject? {
        return try {
            val body = client.newCall(Request.Builder().url(url).headers(headers()).build())
            .execute().use { resp ->
                if (resp.code !in 200..299) return@use ""
                resp.body?.string().orEmpty()
            }
            if (body.isBlank()) return null
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        }
    }

    /** vxtwitter 响应：media_extended（带类型）优先，缺失时按 mediaURLs 后缀判断。 */
    private fun buildVxResponse(root: JsonObject): ParseResponseDto? {
        val medias = mutableListOf<MediaItemDto>()
        val extended = root["media_extended"] as? JsonArray
        if (!extended.isNullOrEmpty()) {
            for (element in extended) {
                val item = element.jsonObjectOrNull() ?: continue
                val type = item["type"]?.stringOrNull()
                val url = item["url"]?.stringOrNull() ?: continue
                when (type) {
                    "image" -> medias += MediaItemDto(kind = "image", url = url, quality = "original")
                    "video", "gif" -> medias += MediaItemDto(
                        kind = "video",
                        url = url,
                        cover = item["thumbnail_url"]?.stringOrNull(),
                        quality = if (type == "gif") "gif" else "hd",
                    )
                }
            }
        } else {
            (root["mediaURLs"] as? JsonArray).orEmpty().forEach { element ->
                val url = element.stringOrNull() ?: return@forEach
                if (isLikelyVideo(url)) {
                    medias += MediaItemDto(kind = "video", url = url, quality = "hd")
                } else {
                    medias += MediaItemDto(kind = "image", url = url, quality = "original")
                }
            }
        }
        if (medias.isEmpty()) return null
        val rawText = root["text"]?.stringOrNull().orEmpty()
        val title = rawText.replace(Regex("\\s+"), " ").trim().take(100).ifBlank { "推文" }
        val author = root["user_name"]?.stringOrNull() ?: root["user_screen_name"]?.stringOrNull()
        return ParseResponseDto(
            platform = "x",
            title = title,
            author = author,
            type = mediaType(medias),
            medias = medias,
        )
    }

    /** fxtwitter 响应：tweet.media.photos / videos。 */
    private fun buildFxResponse(root: JsonObject): ParseResponseDto? {
        val code = (root["code"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 200
        if (code != 200) return null
        val tweet = root["tweet"]?.jsonObjectOrNull() ?: return null
        val media = tweet["media"]?.jsonObjectOrNull() ?: return null
        val medias = mutableListOf<MediaItemDto>()
        (media["photos"] as? JsonArray).orEmpty().forEach { element ->
            element.jsonObjectOrNull()?.get("url")?.stringOrNull()?.let {
                medias += MediaItemDto(kind = "image", url = it, quality = "original")
            }
        }
        (media["videos"] as? JsonArray).orEmpty().forEach { element ->
            val item = element.jsonObjectOrNull() ?: return@forEach
            item["url"]?.stringOrNull()?.let {
                medias += MediaItemDto(
                    kind = "video",
                    url = it,
                    cover = item["thumbnailUrl"]?.stringOrNull(),
                    quality = "hd",
                )
            }
        }
        if (medias.isEmpty()) return null
        val rawText = tweet["text"]?.stringOrNull().orEmpty()
        val title = rawText.replace(Regex("\\s+"), " ").trim().take(100).ifBlank { "推文" }
        val authorObject = tweet["author"]?.jsonObjectOrNull()
        val author = authorObject?.get("name")?.stringOrNull() ?: authorObject?.get("screen_name")?.stringOrNull()
        return ParseResponseDto(
            platform = "x",
            title = title,
            author = author,
            type = mediaType(medias),
            medias = medias,
        )
    }

    private fun isLikelyVideo(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains("/video/")
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

    private fun bindingString(binding: JsonObject, key: String): String? =
        binding[key]?.jsonObjectOrNull()?.get("string_value")?.stringOrNull()

    private fun bindingImage(binding: JsonObject, key: String): String? {
        val value = binding[key]?.jsonObjectOrNull() ?: return null
        value["image_value"]?.jsonObjectOrNull()?.get("url")?.stringOrNull()?.let { return it }
        return value["string_value"]?.stringOrNull()
    }

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun headers(): Headers = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )
        .add("Accept", "application/json, text/plain, */*")
        .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .build()

    private fun googlebotHeaders(): Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
        .add("Accept", "application/json, text/plain, */*")
        .build()

    /** 内部信号：响应中没有任何可识别的媒体。 */
    private class NoMediaException : Exception()
}
