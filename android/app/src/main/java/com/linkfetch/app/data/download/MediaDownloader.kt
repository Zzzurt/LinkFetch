package com.linkfetch.app.data.download

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.linkfetch.app.data.model.MediaItemDto
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class DownloadResult(
    val uri: String,
    val name: String,
    val bytes: Long,
)

class DownloadException(message: String) : Exception(message)

class MediaDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val hlsResolver = HlsPlaylistResolver(client)

    suspend fun download(
        item: MediaItemDto,
        prefix: String,
        onProgress: (Float) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            if (isHlsUrl(item.url)) {
                return@withContext downloadHls(item, prefix, onProgress)
            }
            val request = newRequest(item.url)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(httpFailureMessage(response.code))
                val body = response.body ?: throw IOException("响应为空")
                val total = body.contentLength()
                val mime = body.contentType()?.toString()?.lowercase()
                    ?: fallbackMime(item.isVideo, item.url)
                val ext = extensionFromMime(mime, item)
                val name = uniqueName(prefix, ext)
                val (uri, written) = writeToMediaStore(item.isVideo, name, mime, total) { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            if (total > 0) onProgress((output.bytes().toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                DownloadResult(uri = uri.toString(), name = name, bytes = written)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            throw DownloadException("下载失败：${e.message ?: "网络异常"}")
        } catch (e: Throwable) {
            // 兜底收敛：非法 URL（IllegalArgumentException）、权限不足（SecurityException）、
            // 大图解码 OOM 等都会落到这里。若不收敛，异常会穿透 ViewModel 直达协程顶层并崩溃。
            throw DownloadException("下载失败：${e.message ?: e::class.simpleName ?: "未知错误"}")
        }
    }

    /**
     * 下载 Live 图并合成为单个 Motion Photo 文件保存到相册。
     * 封面图与短视频分开下载，任一步失败都会明确报错，不自动降级为静态图。
     */
    suspend fun downloadLive(
        item: MediaItemDto,
        prefix: String,
        onProgress: (Float) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        val liveUrl = item.liveUrl
        if (liveUrl.isNullOrBlank()) {
            throw DownloadException("该图片没有可用的 Live 视频链接")
        }
        val dir = File(context.cacheDir, "linkfetch_live").apply { mkdirs() }
        val imageTmp = File.createTempFile("cover_", ".img", dir)
        val videoTmp = File.createTempFile("live_", ".mp4", dir)
        var converted: File? = null
        try {
            // 阶段 1：封面图（0 ~ 0.4）
            downloadToTemp(item.url, imageTmp) { p ->
                onProgress((p * 0.4f).coerceIn(0f, 0.4f))
            }
            // 阶段 2：Live 短视频（0.4 ~ 0.8）
            try {
                downloadToTemp(liveUrl, videoTmp) { p ->
                    onProgress((0.4f + p * 0.4f).coerceIn(0.4f, 0.8f))
                }
            } catch (e: IOException) {
                throw DownloadException("Live 视频下载失败：${e.message ?: "网络异常"}")
            }
            // 阶段 3：合成为 Motion Photo 并写入相册（0.8 ~ 1）
            val jpeg = ensureJpeg(imageTmp)
            if (jpeg !== imageTmp) converted = jpeg
            val name = uniqueName(prefix, "jpg")
            val size = imageTmp.length() + videoTmp.length()
            val (uri, written) = writeToMediaStore(isVideo = false, name, "image/jpeg", size) { output ->
                MotionPhotoWriter.compose(jpeg, videoTmp, output) { p ->
                    onProgress((0.8f + p * 0.2f).coerceIn(0.8f, 1f))
                }
            }
            DownloadResult(uri = uri.toString(), name = name, bytes = written)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            throw DownloadException("Live 图下载失败：${e.message ?: "网络异常"}")
        } catch (e: Throwable) {
            throw DownloadException("Live 图处理失败：${e.message ?: e::class.simpleName ?: "未知错误"}")
        } finally {
            runCatching { imageTmp.delete() }
            runCatching { videoTmp.delete() }
            converted?.let { runCatching { it.delete() } }
        }
    }

    /** 把单个 URL（普通直链或 HLS）下载到本地临时文件。 */
    private suspend fun downloadToTemp(
        url: String,
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        if (isHlsUrl(url)) {
            downloadHlsToTemp(url, target, onProgress)
            return
        }
        val request = newRequest(url)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(httpFailureMessage(response.code))
            val body = response.body ?: throw IOException("响应为空")
            val total = body.contentLength()
            var written = 0L
            FileOutputStream(target).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }

    private suspend fun downloadHlsToTemp(
        url: String,
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        val playlist = hlsResolver.resolve(url)
        val totalParts = playlist.segments.size + if (playlist.initSegment != null) 1 else 0
        FileOutputStream(target).use { output ->
            var done = 0
            playlist.initSegment?.let { initUrl ->
                writeHlsPart(initUrl, output)
                done++
            }
            playlist.segments.forEach { segmentUrl ->
                writeHlsPart(segmentUrl, output)
                done++
                if (totalParts > 0) onProgress((done.toFloat() / totalParts).coerceIn(0f, 1f))
            }
        }
    }

    /**
     * 确保封面是 JPEG：已是 JPEG 则原样使用；WebP/PNG 等通过 Bitmap 转码为 JPEG，
     * 并处理 EXIF 旋转方向，保证 Motion Photo 封面方向正确。
     */
    private fun ensureJpeg(file: File): File {
        if (isJpeg(file)) return file
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IOException("无法解码封面图片")
        val normalized = normalizeOrientation(file, bitmap)
        val out = File(file.parentFile, "${file.name}_converted.jpg")
        var ok = false
        try {
            FileOutputStream(out).use { fos ->
                if (!normalized.compress(Bitmap.CompressFormat.JPEG, 95, fos)) {
                    throw IOException("封面图片转码失败")
                }
            }
            ok = true
            return out
        } finally {
            if (!ok) runCatching { out.delete() }
            if (normalized !== bitmap && !normalized.isRecycled) normalized.recycle()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun isJpeg(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(2)
                val read = input.read(magic)
                read == 2 && magic[0] == 0xFF.toByte() && magic[1] == 0xD8.toByte()
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun normalizeOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        if (orientation == ExifInterface.ORIENTATION_UNDEFINED ||
            orientation == ExifInterface.ORIENTATION_NORMAL
        ) {
            return bitmap
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
    }

    /** 长视频 HLS：解析播放列表 -> 按分段下载并拼接，直接写入系统相册。 */
    private suspend fun downloadHls(
        item: MediaItemDto,
        prefix: String,
        onProgress: (Float) -> Unit,
    ): DownloadResult {
        val playlist = hlsResolver.resolve(item.url)
        val name = uniqueName(prefix, playlist.ext)
        val totalParts = playlist.segments.size + if (playlist.initSegment != null) 1 else 0
        val (uri, written) = writeToMediaStore(isVideo = true, name, playlist.mime, null) { output ->
            var done = 0
            playlist.initSegment?.let { initUrl ->
                writeHlsPart(initUrl, output)
                done++
            }
            playlist.segments.forEach { segmentUrl ->
                writeHlsPart(segmentUrl, output)
                done++
                if (totalParts > 0) onProgress((done.toFloat() / totalParts).coerceIn(0f, 1f))
            }
        }
        return DownloadResult(uri = uri.toString(), name = name, bytes = written)
    }

    private fun writeHlsPart(url: String, output: OutputStream) {
        val request = newRequest(url)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HLS 分段下载失败：${httpFailureMessage(response.code)}")
            }
            val body = response.body ?: throw IOException("HLS 分段响应为空")
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    /** 只认路径部分以 .m3u8 结尾的地址，避免普通链接的查询参数里出现 ".m3u8" 被误判。 */
    private fun isHlsUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return path.endsWith(".m3u8", ignoreCase = true)
    }

    /** 带平台请求头的下载请求。 */
    private fun newRequest(url: String): Request =
        Request.Builder().url(url).apply {
            headersFor(url).forEach { (name, value) -> addHeader(name, value) }
        }.build()

    /**
     * 按 URL 域名补上平台 Referer 与移动端 UA。
     *
     * 为什么需要：解析阶段各 Parser 都带了 UA + Referer，而**下载阶段此前是裸请求**。
     * 图片 CDN 大多不校验来源，所以"图片能存下来"长期掩盖了这个缺口；视频 CDN 普遍做防盗链，
     * 缺 Referer 会被直接拒绝（403）—— 这是"图文存得下、视频存不下"最可能的原因。
     *
     * 域名匹配不上时返回空 map、保持原来的裸请求行为：不给陌生 CDN 硬套一个不合适的
     * Referer，免得把本来能下的反而弄坏。
     */
    private fun headersFor(url: String): Map<String, String> {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
            ?: return emptyMap()
        val referer = when {
            host == "douyin.com" || host.endsWith(".douyin.com") ||
                host.endsWith(".iesdouyin.com") || host.contains("douyinvod") ||
                host.contains("douyinpic") || host.contains("douyinstatic") ->
                "https://www.douyin.com/"

            host == "weibo.com" || host.endsWith(".weibo.com") ||
                host.endsWith(".weibo.cn") || host.contains("sinaimg") ||
                host.contains("weibocdn") ->
                // 与 WeiboParser.headers 保持一致：微博侧的请求走的是 m.weibo.cn，不是 weibo.com
                "https://m.weibo.cn/"

            host == "xiaohongshu.com" || host.endsWith(".xiaohongshu.com") ||
                host.contains("xhscdn") || host.contains("xhsimg") ->
                "https://www.xiaohongshu.com/"

            // X（pbs.twimg.com / video.twimg.com）不校验 Referer，所以不补；
            // 其余未知域名同理，继续走裸请求。
            else -> null
        } ?: return emptyMap()
        return mapOf("Referer" to referer, "User-Agent" to MOBILE_UA)
    }

    /**
     * 把 HTTP 失败码翻译成「用户能据此行动」的提示。
     *
     * 403 尤其需要解释清楚：平台的媒体链接大多带**时效签名**，从历史记录打开的作品
     * （几天前解析的）链接早已过期，表现就是 403。只回一句「HTTP 403」的话，用户完全不知道
     * 下一步该做什么 —— 而正确答案往往很简单：回首页把这个链接重新解析一次。
     */
    private fun httpFailureMessage(code: Int): String = when (code) {
        403 -> "平台拒绝了下载（403）。媒体链接带时效签名，从历史记录打开的旧作品通常已过期，" +
            "请回到首页重新解析该链接后再保存。"
        404 -> "媒体文件不存在（404）。该作品可能已被删除，或链接已失效，建议重新解析。"
        429 -> "平台限流（429），请等几分钟再试。"
        else -> "HTTP $code"
    }

    private fun writeToMediaStore(
        isVideo: Boolean,
        name: String,
        mime: String,
        size: Long?,
        writer: (CountingOutputStream) -> Unit,
    ): Pair<Uri, Long> {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        // 视频归入 Movies、图片归入 Pictures，避免视频出现在图库的「图片」分类下
        val relativePath = if (isVideo) "Movies/LinkFetch" else "Pictures/LinkFetch"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (size != null) put(MediaStore.MediaColumns.SIZE, size)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("无法在相册中创建文件")
        // 任何异常都在 catch 中删除半成品并重抛，因此走到后面时 writtenBytes 必然已赋值
        var writtenBytes: Long
        try {
            // 拿不到输出流必须视为失败，避免静默生成 0 字节文件
            val output = resolver.openOutputStream(uri)
                ?: throw IOException("无法打开文件写入流")
            CountingOutputStream(output).use { counting ->
                writer(counting)
                counting.flush()
                writtenBytes = counting.bytes()
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        val update = ContentValues().apply {
            put(MediaStore.MediaColumns.SIZE, writtenBytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
        }
        if (update.size() > 0) resolver.update(uri, update, null, null)
        return uri to writtenBytes
    }

    private fun extensionFromMime(mime: String, item: MediaItemDto): String = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "image/avif" -> "avif"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/mp2t" -> "ts"
        "video/quicktime" -> "mov"
        "video/webm" -> "webm"
        else -> extensionOfUrl(item.url, item.isVideo)
    }

    private fun fallbackMime(isVideo: Boolean, url: String): String {
        val ext = extensionOfUrl(url, isVideo)
        return if (isVideo) "video/mp4" else if (ext == "png") "image/png" else "image/jpeg"
    }

    private fun extensionOfUrl(url: String, isVideo: Boolean): String {
        val path = url.substringBefore("?").substringAfterLast('/')
        val dot = path.lastIndexOf('.')
        if (dot >= 0) {
            val ext = path.substring(dot + 1).filter { it.isLetterOrDigit() }.take(5).lowercase()
            if (ext.isNotEmpty()) return ext
        }
        return if (isVideo) "mp4" else "jpg"
    }

    private fun uniqueName(prefix: String, ext: String): String {
        val safePrefix = prefix.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(40).ifBlank { "LinkFetch" }
        val seq = counter.getAndIncrement()
        return "${safePrefix}_${System.currentTimeMillis()}_$seq.$ext"
    }

    private companion object {
        /** 同一毫秒内并发下载时的序号，用原子类型避免重名。 */
        val counter = java.util.concurrent.atomic.AtomicInteger(0)

        /** 与各 Parser 抓取页面时一致的移动端 UA：部分 CDN 会按 UA 分流 */
        const val MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    }
}

/** 统计实际写入字节数，用于 HLS（总大小未知）下载后回填 MediaStore SIZE。 */
private class CountingOutputStream(
    private val delegate: OutputStream,
) : OutputStream() {
    private var bytesWritten: Long = 0

    fun bytes(): Long = bytesWritten

    override fun write(b: Int) {
        delegate.write(b)
        bytesWritten++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        bytesWritten += len
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}
