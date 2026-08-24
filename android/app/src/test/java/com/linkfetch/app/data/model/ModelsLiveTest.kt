package com.linkfetch.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsLiveTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun oldHistoryJsonWithoutLiveFieldsDecodesWithDefaults() {
        val raw = """
            {
              "platform": "xhs",
              "title": "旧记录",
              "type": "image",
              "medias": [
                {"kind": "image", "url": "https://a.com/a.jpg", "quality": "original"}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString(ParseResponseDto.serializer(), raw)

        assertFalse(response.images.first().live)
        assertNull(response.images.first().liveUrl)
    }

    @Test
    fun liveFieldsRoundTripThroughJson() {
        val response = ParseResponseDto(
            platform = "weibo",
            title = "实况",
            type = "image",
            medias = listOf(
                MediaItemDto(
                    kind = "image",
                    url = "https://a.com/a.jpg",
                    quality = "original",
                    live = true,
                    liveUrl = "https://livephoto.weibo.cn/x.mov?Expires=1&ssig=abc&KID=unistore",
                ),
                MediaItemDto(kind = "image", url = "https://a.com/b.jpg"),
            ),
        )

        val encoded = json.encodeToString(ParseResponseDto.serializer(), response)
        val decoded = json.decodeFromString(ParseResponseDto.serializer(), encoded)

        assertTrue(decoded.images[0].live)
        assertEquals("https://livephoto.weibo.cn/x.mov?Expires=1&ssig=abc&KID=unistore", decoded.images[0].liveUrl)
        assertFalse(decoded.images[1].live)
        assertNull(decoded.images[1].liveUrl)
    }
}
