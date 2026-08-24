package com.linkfetch.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodeResponseWithUnknownFields() {
        val raw = """
            {
              "platform": "douyin",
              "title": "视频",
              "extraField": "ignored",
              "type": "video",
              "medias": [
                {"kind": "video", "url": "https://a.com/v.mp4", "cover": "https://a.com/c.jpg", "quality": "1080p"}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString(ParseResponseDto.serializer(), raw)

        assertEquals("douyin", response.platform)
        assertEquals(1, response.videos.size)
        assertEquals("https://a.com/c.jpg", response.videos.first().cover)
        assertEquals(0, response.images.size)
    }

    @Test
    fun mediaTypeHelpers() {
        val media = ParseResponseDto(
            platform = "weibo",
            title = "t",
            type = "mixed",
            medias = listOf(
                MediaItemDto(kind = "video", url = "v.mp4"),
                MediaItemDto(kind = "image", url = "a.jpg"),
            ),
        )
        assertEquals(1, media.videos.size)
        assertEquals(1, media.images.size)
    }
}

