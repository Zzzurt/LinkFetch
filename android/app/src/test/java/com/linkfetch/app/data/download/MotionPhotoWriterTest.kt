package com.linkfetch.app.data.download

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPhotoWriterTest {

    @Test
    fun composeInsertsXmpExifAndAppendsVideo() {
        val imageBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            // APP0（JFIF 占位）
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x06, 0x4A, 0x46, 0x49, 0x46,
            // SOF0：宽 640、高 480
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            0x01, 0xE0.toByte(), 0x02, 0x80.toByte(),
            0x03,
            0x11, 0x00, 0x02, 0x11, 0x00, 0x03, 0x11, 0x00, 0x00,
            // 图像数据与 EOI
            0x12, 0x34, 0x56, 0x78,
            0xFF.toByte(), 0xD9.toByte(),
        )
        val videoBytes = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70)
        val imageFile = Files.createTempFile("motion_cover_", ".jpg").toFile()
        val videoFile = Files.createTempFile("motion_video_", ".mp4").toFile()
        try {
            imageFile.writeBytes(imageBytes)
            videoFile.writeBytes(videoBytes)

            val output = ByteArrayOutputStream()
            val progress = mutableListOf<Float>()
            MotionPhotoWriter.compose(imageFile, videoFile, output) { progress.add(it) }

            val result = output.toByteArray()
            // 以 JPEG SOI 开头
            assertTrue(result[0] == 0xFF.toByte() && result[1] == 0xD8.toByte())
            // 包含 XMP 元数据
            val head = String(result.copyOfRange(0, result.size - videoBytes.size), Charsets.UTF_8)
            assertTrue(head.contains("MotionPhoto"))
            assertTrue(head.contains("MotionPhotoOwner=\"oplus\""))
            assertTrue(head.contains("OpCamera:VideoLength=\"${videoBytes.size}\""))
            assertTrue(head.contains("Item:Mime=\"video/mp4\""))
            assertTrue(head.contains("Item:Length=\"${videoBytes.size}\""))
            assertTrue(head.contains("<?xpacket"))
            // 包含 OPPO 相册识别标识与正确宽高
            assertTrue(head.contains("oplus_8388608"))
            val exifSegment = MotionPhotoWriter.buildOppoExifApp1Segment(
                640,
                480,
                videoBytes.size.toLong(),
            )
            assertTrue(indexOfBytes(result, exifSegment) >= 0)
            // XMP 段必须位于 EXIF 段之前
            val xmpSegment = MotionPhotoWriter.buildXmpApp1Segment(videoBytes.size.toLong())
            val xmpIndex = indexOfBytes(result, xmpSegment)
            val exifIndex = indexOfBytes(result, exifSegment)
            assertTrue(xmpIndex >= 0)
            assertTrue(exifIndex > xmpIndex)
            // 视频字节追加在文件末尾
            val tail = result.copyOfRange(result.size - videoBytes.size, result.size)
            assertTrue(tail.contentEquals(videoBytes))
            // 总长度 = 图片 + XMP 段 + EXIF 段 + 视频
            assertEquals(
                imageBytes.size + xmpSegment.size + exifSegment.size + videoBytes.size,
                result.size,
            )
            // 进度回调已触发且单调递增到 1
            assertTrue(progress.isNotEmpty())
            for (i in 1 until progress.size) {
                assertTrue(progress[i] >= progress[i - 1])
            }
            assertEquals(1f, progress.last(), 0.001f)
        } finally {
            imageFile.delete()
            videoFile.delete()
        }
    }

    @Test
    fun composeUsesFallbackDimensionsWhenNoSof() {
        val imageBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            // SOS 段（无组件），无 SOF 标记
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02,
            0x12, 0x34,
            0xFF.toByte(), 0xD9.toByte(),
        )
        val videoBytes = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70)
        val imageFile = Files.createTempFile("motion_cover2_", ".jpg").toFile()
        val videoFile = Files.createTempFile("motion_video2_", ".mp4").toFile()
        try {
            imageFile.writeBytes(imageBytes)
            videoFile.writeBytes(videoBytes)

            val output = ByteArrayOutputStream()
            MotionPhotoWriter.compose(imageFile, videoFile, output)

            val result = output.toByteArray()
            val exifSegment = MotionPhotoWriter.buildOppoExifApp1Segment(
                1440,
                1084,
                videoBytes.size.toLong(),
            )
            assertTrue(indexOfBytes(result, exifSegment) >= 0)
        } finally {
            imageFile.delete()
            videoFile.delete()
        }
    }

    @Test
    fun oppoExifMarkerGrowsWithVideoSize() {
        // 小视频使用 8 MB 旧版常量
        val small = MotionPhotoWriter.buildOppoExifApp1Segment(640, 480, 100L)
        assertTrue(String(small, Charsets.US_ASCII).contains("oplus_8388608"))

        // 视频超过 8 MB 时，标记数值必须不小于实际视频大小
        val bigVideoSize = 12_582_912L // 12 MB
        val big = MotionPhotoWriter.buildOppoExifApp1Segment(640, 480, bigVideoSize)
        assertTrue(String(big, Charsets.US_ASCII).contains("oplus_$bigVideoSize"))
        // 段长度字段 = 实际长度 - 2
        val total = ((big[2].toInt() and 0xFF) shl 8) or (big[3].toInt() and 0xFF)
        assertEquals(big.size - 2, total)
    }

    @Test
    fun composeRejectsNonJpegCover() {
        val imageBytes = "not a jpeg".toByteArray()
        val videoBytes = byteArrayOf(0x01, 0x02, 0x03)
        val imageFile = Files.createTempFile("motion_bad_", ".img").toFile()
        val videoFile = Files.createTempFile("motion_video3_", ".mp4").toFile()
        try {
            imageFile.writeBytes(imageBytes)
            videoFile.writeBytes(videoBytes)

            val output = ByteArrayOutputStream()
            var thrown: IOException? = null
            try {
                MotionPhotoWriter.compose(imageFile, videoFile, output)
            } catch (e: IOException) {
                thrown = e
            }

            assertTrue(thrown != null)
            assertFalse(output.size() > 0)
        } finally {
            imageFile.delete()
            videoFile.delete()
        }
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > haystack.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
