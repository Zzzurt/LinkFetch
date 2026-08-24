package com.linkfetch.app.data.download

import java.io.IOException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 解析 HLS（m3u8）播放列表：
 * 主列表（#EXT-X-STREAM-INF）中按 BANDWIDTH 选择最高码率 -> 媒体列表（#EXTINF）中的分段地址。
 * Twitter 长视频（amplify 卡片）只提供 HLS，App 通过分段下载拼接成可播放文件。
 */
data class HlsPlaylist(
    val mime: String,
    val ext: String,
    /** EXT-X-MAP 初始化分段（fMP4 时需要先写入），没有则为 null（TS 流）。 */
    val initSegment: String?,
    val segments: List<String>,
)

class HlsException(message: String) : IOException(message)

class HlsPlaylistResolver(
    private val client: OkHttpClient,
    private val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
) {

    suspend fun resolve(masterUrl: String): HlsPlaylist = withContext(Dispatchers.IO) {
        val master = fetch(masterUrl)
        val mediaUrl: String
        val mediaBody: String
        if (master.contains("#EXT-X-STREAM-INF", ignoreCase = true)) {
            mediaUrl = pickVariant(master, masterUrl)
                ?: throw HlsException("HLS 主播放列表中没有可用清晰度")
            mediaBody = fetch(mediaUrl)
        } else {
            mediaUrl = masterUrl
            mediaBody = master
        }
        if (!mediaBody.contains("#EXTINF", ignoreCase = true)) {
            throw HlsException("HLS 播放列表格式异常")
        }
        if (mediaBody.contains("#EXT-X-KEY:METHOD=AES-128", ignoreCase = true)) {
            throw HlsException("暂不支持下载加密的 HLS 视频")
        }
        val init = Regex("#EXT-X-MAP:URI=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(mediaBody)?.groupValues?.get(1)
        val isFmp4 = init != null || mediaBody.contains("#EXT-X-MAP", ignoreCase = true)
        HlsPlaylist(
            mime = if (isFmp4) "video/mp4" else "video/mp2t",
            ext = if (isFmp4) "mp4" else "ts",
            initSegment = init?.let { resolveUrl(mediaUrl, it) },
            segments = parseSegments(mediaBody).map { resolveUrl(mediaUrl, it) },
        )
    }

    private fun parseSegments(body: String): List<String> {
        val lines = body.lineSequence().toList()
        val segments = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                var j = i + 1
                while (j < lines.size) {
                    val candidate = lines[j].trim()
                    if (candidate.isEmpty() || candidate.startsWith("#")) {
                        j++
                        continue
                    }
                    segments += candidate
                    break
                }
                i = j + 1
            } else {
                i++
            }
        }
        if (segments.isEmpty()) {
            // 兜底：取所有非注释、非空行作为分段地址
            segments += lines.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        }
        return segments.distinct()
    }

    private fun pickVariant(master: String, baseUrl: String): String? {
        val lines = master.lineSequence().toList()
        var bestBandwidth = -1L
        var bestUri: String? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                val bandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                var j = i + 1
                while (j < lines.size) {
                    val uri = lines[j].trim()
                    if (uri.isNotEmpty() && !uri.startsWith("#")) {
                        if (bandwidth > bestBandwidth) {
                            bestBandwidth = bandwidth
                            bestUri = uri
                        }
                        break
                    }
                    j++
                }
                i = j + 1
            } else {
                i++
            }
        }
        return bestUri?.let { resolveUrl(baseUrl, it) }
    }

    private fun resolveUrl(base: String, uri: String): String {
        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
        return URL(URL(base), uri).toString()
    }

    private suspend fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HlsException("HLS 请求失败：HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }
}
