package com.linkfetch.app.data.download

import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * 将静态 JPEG 与短视频合成为单个 Motion Photo（动态照片）文件：
 * - 在 JPEG 的 SOI 之后依次插入 APP1 XMP 段与 OPPO 专属 APP1 EXIF 段；
 * - 在 JPEG 数据之后直接追加短视频字节。
 *
 * 文件结构（与 OPPO 相册可识别方案一致）：
 * SOI → XMP APP1 → EXIF APP1（含 oplus_ 标识）→ JPEG 数据 → EOI → MP4。
 *
 * OPPO 相册识别动态照片依赖两点：
 * 1. XMP 中 OpCamera:MotionPhotoOwner="oplus"（固定值，用于判断是否自家拍摄）；
 * 2. EXIF UserComment 以 "oplus_" 开头，数字为最大视频字节数（不能小于实际视频大小）。
 *
 * 不支持的图库会把该文件当作普通静态图片显示，不会损坏。
 */
object MotionPhotoWriter {

    private const val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000"

    private const val DEFAULT_WIDTH = 1440
    private const val DEFAULT_HEIGHT = 1084

    /** OPPO 相册最低可识别的视频大小上限（旧版 ColorOS 常见值：8 MB）。 */
    private const val MIN_OPPO_VIDEO_LIMIT = 8_388_608L

    private fun b(v: Int): Byte = v.toByte()

    /**
     * OPPO 相册识别动态照片的关键 EXIF 段模板（APP1 + TIFF + UserComment=oplus_...），
     * 共 102 字节；宽高占位位于偏移 28..31 与 40..43，UserComment 长度位于偏移 78..81，
     * 标记字符串从偏移 102 开始（长度可随视频大小动态变化）。
     */
    private val OPPO_EXIF_BASE: ByteArray = byteArrayOf(
        // APP1 段头（长度字段在 2..3，稍后按实际大小回填）
        b(0xFF), b(0xE1), 0x00, 0x00,
        // "Exif\0\0"
        0x45, 0x78, 0x69, 0x66, 0x00, 0x00,
        // TIFF 头：大端（MM）、42、IFD0 偏移 8
        0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
        // IFD0：4 个条目
        0x00, 0x04,
        // 0x0100 ImageWidth（LONG），值占位 28..31
        0x01, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x05, b(0xA0),
        // 0x0101 ImageLength（LONG），值占位 40..43
        0x01, 0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x04, 0x3C,
        // 0x8769 ExifIFD 指针 -> 0x3E
        b(0x87), 0x69, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x3E,
        // 0x0112 Orientation（SHORT）= 0
        0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
        // 下一 IFD 偏移
        0x00, 0x00, 0x00, 0x00,
        // ExifIFD：2 个条目
        0x00, 0x02,
        // 0x9286 UserComment（ASCII，长度占位 78..81），值位于 TIFF 偏移 0x5C
        b(0x92), b(0x86), 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x5C,
        // 0x9208（LONG）= 0，与参考实现保持一致
        b(0x92), 0x08, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
        // 下一 IFD 偏移
        0x00, 0x00, 0x00, 0x00,
    )

    /**
     * 组合并写出 Motion Photo。
     *
     * @param imageFile 封面 JPEG（必须是 JPEG，若原始图片是 WebP/PNG 需先转码）
     * @param videoFile 待内嵌的短视频
     * @param output 目标输出流（通常是 MediaStore 的输出流）
     * @param onProgress 组合进度回调（0..1），用于 UI 展示
     */
    fun compose(
        imageFile: File,
        videoFile: File,
        output: OutputStream,
        onProgress: (Float) -> Unit = {},
    ) {
        val videoSize = videoFile.length()
        val (width, height) = try {
            readJpegDimensions(imageFile)
        } catch (e: IOException) {
            DEFAULT_WIDTH to DEFAULT_HEIGHT
        }
        val xmpSegment = buildXmpApp1Segment(videoSize)
        val exifSegment = buildOppoExifApp1Segment(width, height, videoSize)
        val imageSize = imageFile.length()
        val total = imageSize + xmpSegment.size + exifSegment.size + videoSize
        if (total <= 0) throw IOException("图片或视频文件为空")

        var written = 0L
        imageFile.inputStream().buffered().use { imageIn ->
            // 读取并校验 JPEG SOI
            val soi = ByteArray(2)
            var read = imageIn.read(soi)
            if (read < 2 || soi[0] != 0xFF.toByte() || soi[1] != 0xD8.toByte()) {
                throw IOException("封面图片不是有效的 JPEG")
            }
            output.write(soi)
            written += 2

            // 紧跟 SOI 写入 XMP 段
            output.write(xmpSegment)
            written += xmpSegment.size
            onProgress((written.toFloat() / total).coerceIn(0f, 1f))

            // 再写入 OPPO 相册识别的 EXIF 段
            output.write(exifSegment)
            written += exifSegment.size
            onProgress((written.toFloat() / total).coerceIn(0f, 1f))

            // 复制剩余 JPEG 数据
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = imageIn.read(buffer)
                if (n == -1) break
                output.write(buffer, 0, n)
                written += n
                onProgress((written.toFloat() / total).coerceIn(0f, 1f))
            }
        }

        videoFile.inputStream().buffered().use { videoIn ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = videoIn.read(buffer)
                if (n == -1) break
                output.write(buffer, 0, n)
                written += n
                onProgress((written.toFloat() / total).coerceIn(0f, 1f))
            }
        }
        output.flush()
    }

    /** 构造 APP1 XMP 段（含 FF E1 标记与长度），兼容 Google / 小米 / OPPO 动态照片。 */
    internal fun buildXmpApp1Segment(videoSize: Long): ByteArray {
        val xmpData = xmpMetadata(videoSize).toByteArray(Charsets.UTF_8)
        val header = XMP_HEADER.toByteArray(Charsets.UTF_8)
        val payloadLength = header.size + xmpData.size
        val segmentLength = payloadLength + 2 // 长度字段本身占 2 字节
        require(segmentLength <= 0xFFFF) { "XMP 段过长" }

        return ByteArray(2 + segmentLength).also { seg ->
            seg[0] = 0xFF.toByte()
            seg[1] = 0xE1.toByte()
            seg[2] = ((segmentLength shr 8) and 0xFF).toByte()
            seg[3] = (segmentLength and 0xFF).toByte()
            header.copyInto(seg, 4)
            xmpData.copyInto(seg, 4 + header.size)
        }
    }

    /**
     * 构造 OPPO 相册识别的 APP1 EXIF 段（含 oplus_ 标识），并写入真实图像宽高。
     *
     * UserComment 的数值为“最大视频字节数”，必须不小于实际视频大小，
     * 否则较大的 Live 视频会被 OPPO 相册判定为无效。
     */
    internal fun buildOppoExifApp1Segment(width: Int, height: Int, videoSize: Long): ByteArray {
        val limit = maxOf(videoSize, MIN_OPPO_VIDEO_LIMIT)
        val marker = "oplus_$limit".toByteArray(Charsets.US_ASCII)
        val markerWithNul = marker + byteArrayOf(0)
        val dataStart = OPPO_EXIF_BASE.size // 102
        val total = dataStart + markerWithNul.size
        val result = OPPO_EXIF_BASE.copyOf(total)

        // APP1 段长度（不含标记与长度字段自身）
        result[2] = ((total - 2 shr 8) and 0xFF).toByte()
        result[3] = ((total - 2) and 0xFF).toByte()

        val w = width.coerceIn(1, 0xFFFFFF)
        val h = height.coerceIn(1, 0xFFFFFF)
        result[28] = ((w shr 24) and 0xFF).toByte()
        result[29] = ((w shr 16) and 0xFF).toByte()
        result[30] = ((w shr 8) and 0xFF).toByte()
        result[31] = (w and 0xFF).toByte()
        result[40] = ((h shr 24) and 0xFF).toByte()
        result[41] = ((h shr 16) and 0xFF).toByte()
        result[42] = ((h shr 8) and 0xFF).toByte()
        result[43] = (h and 0xFF).toByte()

        // UserComment 数据长度（含结尾 NUL）
        val len = markerWithNul.size
        result[78] = ((len shr 24) and 0xFF).toByte()
        result[79] = ((len shr 16) and 0xFF).toByte()
        result[80] = ((len shr 8) and 0xFF).toByte()
        result[81] = (len and 0xFF).toByte()

        markerWithNul.copyInto(result, dataStart)
        return result
    }

    /** 从 JPEG 头部解析图像宽高（SOF 标记）；解析失败时返回模板默认值。 */
    private fun readJpegDimensions(imageFile: File): Pair<Int, Int> {
        val header = ByteArray(64 * 1024)
        val n = imageFile.inputStream().buffered().use { it.read(header) }
        if (n < 4 || header[0] != 0xFF.toByte() || header[1] != 0xD8.toByte()) {
            throw IOException("封面图片不是有效的 JPEG")
        }
        var p = 2
        while (p + 4 <= n) {
            if ((header[p].toInt() and 0xFF) != 0xFF) throw IOException("JPEG 标记格式错误")
            val marker = header[p + 1].toInt() and 0xFF
            if (marker == 0xFF) {
                // 填充字节
                p++
                continue
            }
            if (marker == 0xD8 || marker == 0x01 || (marker in 0xD0..0xD7)) {
                // 无长度字段的独立标记
                p += 2
                continue
            }
            if (marker == 0xD9 || marker == 0xDA) break
            val len = ((header[p + 2].toInt() and 0xFF) shl 8) or (header[p + 3].toInt() and 0xFF)
            if (len < 2) throw IOException("JPEG 段长度错误")
            val isSof = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
            if (isSof) {
                if (p + 9 > n) break // 头部不完整，回退默认值
                val height = ((header[p + 5].toInt() and 0xFF) shl 8) or (header[p + 6].toInt() and 0xFF)
                val width = ((header[p + 7].toInt() and 0xFF) shl 8) or (header[p + 8].toInt() and 0xFF)
                if (width > 0 && height > 0) return width to height
            }
            p += 2 + len
        }
        return DEFAULT_WIDTH to DEFAULT_HEIGHT
    }

    /**
     * OPPO O-Live Photo 的 XMP 模板：
     * Google MotionPhoto V2（GCamera + Container）基础上增加 OpCamera 命名空间，
     * MotionPhotoOwner 固定为 "oplus"，不包含已废弃的 MicroVideo / MiCamera 字段。
     */
    private fun xmpMetadata(videoSize: Long): String {
        val size = videoSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return String.format(
            "<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" +
                "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n" +
                "  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                "    <rdf:Description rdf:about=\"\"" +
                " xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"" +
                " xmlns:Container=\"http://ns.google.com/photos/1.0/container/\"" +
                " xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"" +
                " xmlns:OpCamera=\"http://ns.oplus.com/photos/1.0/camera/\"" +
                " GCamera:MotionPhoto=\"1\"" +
                " GCamera:MotionPhotoVersion=\"1\"" +
                " GCamera:MotionPhotoPresentationTimestampUs=\"0\"" +
                " OpCamera:MotionPhotoPrimaryPresentationTimestampUs=\"0\"" +
                " OpCamera:MotionPhotoOwner=\"oplus\"" +
                " OpCamera:OLivePhotoVersion=\"2\"" +
                " OpCamera:VideoLength=\"%1\$d\">" +
                "<Container:Directory>" +
                "<rdf:Seq>" +
                "<rdf:li rdf:parseType=\"Resource\">" +
                "<Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"0\" Item:Padding=\"0\"/>" +
                "</rdf:li>" +
                "<rdf:li rdf:parseType=\"Resource\">" +
                "<Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\"%1\$d\" Item:Padding=\"0\"/>" +
                "</rdf:li>" +
                "</rdf:Seq>" +
                "</Container:Directory>" +
                "</rdf:Description>\n" +
                "  </rdf:RDF>\n" +
                "</x:xmpmeta>\n" +
                "<?xpacket end=\"w\"?>",
            size,
        )
    }

    private const val DEFAULT_BUFFER_SIZE = 8192
}
