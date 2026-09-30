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
        val title = decodeHtmlEntities(
            htmlTagRegex.replace(data["text"]?.stringOrNull().orEmpty(), ""),
        ).trim().take(100).ifBlank { "微博正文" }
        val author = data["user"]?.jsonObjectOrNull()?.get("screen_name")?.stringOrNull()

        val pics = (data["pics"] as? JsonArray) ?: JsonArray(emptyList())
        val picIds = (data["pic_ids"] as? JsonArray)?.mapNotNull { it.stringOrNull() } ?: emptyList()
        val mixIds = (data["mix_media_ids"] as? JsonArray)?.mapNotNull { it.stringOrNull() } ?: emptyList()

        val medias = if (mixIds.isNotEmpty()) {
            buildMixedMedias(mixIds, pics, picIds)
        } else {
            buildPlainMedias(data, pics, picIds)
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

    /** 普通微博：pics 就是完整图集，page_info 的视频（如有）排在最前。 */
    private fun buildPlainMedias(
        data: JsonObject,
        pics: JsonArray,
        picIds: List<String>,
    ): List<MediaItemDto> {
        val medias = mutableListOf<MediaItemDto>()
        val pageInfo = data["page_info"]?.jsonObjectOrNull()
        val mediaInfo = pageInfo?.get("media_info")?.jsonObjectOrNull()
        val videoUrl = mediaInfo?.get("mp4_hd_url")?.stringOrNull()
            ?: mediaInfo?.get("mp4_sd_url")?.stringOrNull()
            ?: mediaInfo?.get("stream_url_hd")?.stringOrNull()
            ?: mediaInfo?.get("stream_url")?.stringOrNull()
        if (!videoUrl.isNullOrBlank()) {
            medias += MediaItemDto(
                kind = "video",
                url = videoUrl,
                cover = pageInfo?.get("page_pic")?.stringOrNull(),
                quality = "hd",
            )
        }

        val seen = mutableSetOf<String>()
        pics.forEach { element ->
            val pic = element.jsonObjectOrNull() ?: return@forEach
            pic["pid"]?.stringOrNull()?.let { seen += it }
            imageFromPic(pic)?.let { medias += it }
        }
        // 仅当 pics 确实不完整时才补全，避免个别响应缺 pid 时把图片重复列一遍
        if (pics.size < picIds.size) {
            picIds.filterNot { it in seen }.forEach { medias += imageFromPid(it) }
        }
        return medias
    }

    /**
     * 混合发布（图文 + 视频混排）：接口的 pics / mix_media_ids 只覆盖前 9 个媒体，
     * 图片完整清单在 pic_ids 里——不补全的话 18 图微博只能解析出 9 个。
     * mix 中的 t.cn 短链即一个视频，与 pics 里 type=video 的条目按出现顺序一一对应。
     */
    private fun buildMixedMedias(
        mixIds: List<String>,
        pics: JsonArray,
        picIds: List<String>,
    ): List<MediaItemDto> {
        val byPid = mutableMapOf<String, JsonObject>()
        val videoPics = mutableListOf<JsonObject>()
        pics.forEach { element ->
            val pic = element.jsonObjectOrNull() ?: return@forEach
            pic["pid"]?.stringOrNull()?.let { byPid[it] = pic }
            if (pic["type"]?.stringOrNull() == "video") videoPics += pic
        }

        val medias = mutableListOf<MediaItemDto>()
        val seen = mutableSetOf<String>()
        var videoIndex = 0
        mixIds.forEach { entry ->
            if (entry.startsWith("http")) {
                videoPics.getOrNull(videoIndex)?.let { pic ->
                    videoFromPic(pic)?.let { medias += it }
                }
                videoIndex++
                return@forEach
            }
            val image = byPid[entry]?.let { imageFromPic(it) } ?: imageFromPid(entry)
            medias += image
            seen += entry
        }
        picIds.filterNot { it in seen }.forEach { medias += imageFromPid(it) }
        return medias
    }

    /** pics 条目 -> 图片；Live 图的 .mov 短视频一起带上。 */
    private fun imageFromPic(pic: JsonObject): MediaItemDto? {
        // original 可能是字符串；large 可能是字符串或 {size, url} 对象（mw2000 大图）
        val url = pic["original"]?.mediaUrlOrNull()
            ?: pic["large"]?.mediaUrlOrNull()
            ?: pic["url"]?.stringOrNull()
        if (url.isNullOrBlank()) return null
        // Live 图：type=livephoto 时 videoSrc 为 .mov 短视频直链
        val liveUrl = if (pic["type"]?.stringOrNull() == "livephoto") {
            pic["videoSrc"]?.stringOrNull()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return MediaItemDto(
            kind = "image",
            url = url,
            quality = "original",
            live = !liveUrl.isNullOrBlank(),
            liveUrl = liveUrl,
        )
    }

    /** pics 里的视频条目 -> 视频（videoSrc 即 mp4 直链，large 为封面）。 */
    private fun videoFromPic(pic: JsonObject): MediaItemDto? {
        val url = pic["videoSrc"]?.stringOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return MediaItemDto(
            kind = "video",
            url = url,
            cover = pic["large"]?.mediaUrlOrNull() ?: pic["url"]?.stringOrNull(),
            quality = "hd",
        )
    }

    /** 接口未返回明细的图片（混合发布第 10 个媒体起）按 pic_id 拼原图直链。 */
    private fun imageFromPid(pid: String): MediaItemDto =
        MediaItemDto(
            kind = "image",
            url = "https://wx1.sinaimg.cn/large/$pid.jpg",
            quality = "original",
        )

    /**
     * 微博正文是 HTML 片段，剥掉标签后仍需解码实体，
     * 否则标题里会残留 `&amp;` / `&quot;` 之类的原始转义。
     * 注意 &amp; 必须最后替换，避免把 &amp;lt; 二次解码成 "<"。
     */
    private fun decodeHtmlEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

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
