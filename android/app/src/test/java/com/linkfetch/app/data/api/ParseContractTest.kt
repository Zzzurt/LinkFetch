package com.linkfetch.app.data.api

import com.linkfetch.app.data.model.ParseResponseDto
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 与服务端共享的响应契约。
 *
 * fixture 来自 backend/tests/fixtures（见 app/build.gradle.kts 的 test resources），
 * 后端 test_contract.py 用 Pydantic 校验同一批文件。这里用 ApiClient 实际使用的
 * Json 配置解码，任何改名、类型变化都会让测试失败，而不是等线上解析报错。
 */
class ParseContractTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "缺少契约 fixture：$name（应位于 backend/tests/fixtures）"
        }.bufferedReader().use { it.readText() }

    private fun decode(name: String): ParseResponseDto =
        ApiClient.json.decodeFromString(ParseResponseDto.serializer(), fixture(name))

    @Test
    fun xhsFixtureDecodesMixedMedia() {
        val result = decode("parse_xhs.json")

        assertEquals("xhs", result.platform)
        assertEquals("周末去海边", result.title)
        assertEquals("小海", result.author)
        assertEquals("mixed", result.type)
        assertEquals(2, result.medias.size)
        assertEquals(1, result.videos.size)
        assertEquals(1, result.images.size)

        val video = result.videos.single()
        assertEquals("hd", video.quality)
        assertNotNull(video.cover)
        val image = result.images.single()
        assertEquals("original", image.quality)
        assertNull(image.cover)
    }

    @Test
    fun douyinFixtureAllowsMissingAuthorAndOptionalFields() {
        val result = decode("parse_douyin.json")

        assertEquals("douyin", result.platform)
        assertNull(result.author)
        assertEquals("video", result.type)
        val video = result.medias.single()
        assertTrue(video.isVideo)
        assertEquals("1080p", video.quality)
        assertNull(video.width)
        assertNull(video.liveUrl)
        assertFalse(video.live)
    }

    @Test
    fun weiboFixtureDecodesImages() {
        val result = decode("parse_weibo.json")

        assertEquals("weibo", result.platform)
        assertEquals("image", result.type)
        assertEquals(2, result.images.size)
        assertTrue(result.videos.isEmpty())
        assertTrue(result.images.all { it.quality == "original" })
    }

    @Test
    fun xFixtureDecodesMixedMediaInOrder() {
        val result = decode("parse_x.json")

        assertEquals("x", result.platform)
        assertEquals("mixed", result.type)
        assertEquals(listOf("image", "video"), result.medias.map { it.kind })
        assertNotNull(result.medias[1].cover)
    }

    @Test
    fun unknownServerFieldDoesNotBreakDecoding() {
        val body = """
            {
              "platform": "weibo",
              "title": "t",
              "type": "image",
              "medias": [{"kind": "image", "url": "https://example.com/a.jpg"}],
              "futureField": {"nested": true}
            }
        """.trimIndent()

        val result = ApiClient.json.decodeFromString(ParseResponseDto.serializer(), body)

        assertEquals("weibo", result.platform)
        assertEquals(1, result.medias.size)
    }

    @Test
    fun directModeLiveFieldsDecode() {
        // 直连解析（LocalParseClient）比服务端多 live/liveUrl；服务端旧响应缺这两个字段也必须可用
        val body = """
            {
              "platform": "xhs",
              "title": "实况",
              "type": "image",
              "medias": [{
                "kind": "image",
                "url": "https://example.com/a.jpg",
                "live": true,
                "liveUrl": "https://example.com/a.mp4"
              }]
            }
        """.trimIndent()

        val media = ApiClient.json
            .decodeFromString(ParseResponseDto.serializer(), body)
            .medias
            .single()

        assertTrue(media.live)
        assertEquals("https://example.com/a.mp4", media.liveUrl)
        assertFalse(media.isVideo)
    }

    @Test(expected = SerializationException::class)
    fun missingRequiredTitleFailsLoudly() {
        ApiClient.json.decodeFromString(
            ParseResponseDto.serializer(),
            """{"platform":"weibo","type":"image","medias":[]}""",
        )
    }
}
