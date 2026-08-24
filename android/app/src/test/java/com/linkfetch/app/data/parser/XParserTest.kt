package com.linkfetch.app.data.parser

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class XParserTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        base = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun parser() = XParser(syndicationBase = base)

    @Test
    fun parsesPhotoTweetWithOriginalImage() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "一张照片",
                  "user": {"name": "作者", "screen_name": "author"},
                  "mediaDetails": [
                    {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/AbC.jpg"},
                    {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/DeF_large.jpg"}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/2083053411524850111")

        assertEquals("x", result.platform)
        assertEquals("一张照片", result.title)
        assertEquals("作者", result.author)
        assertEquals("image", result.type)
        assertEquals(2, result.images.size)
        // 原图：追加 name=orig，并去掉 _large 尺寸后缀
        assertEquals("https://pbs.twimg.com/media/AbC.jpg?name=orig", result.images[0].url)
        assertEquals("https://pbs.twimg.com/media/DeF.jpg?name=orig", result.images[1].url)
        val recorded = server.takeRequest()
        assertTrue(recorded.path?.contains("id=2083053411524850111") == true)
    }

    @Test
    fun parsesUsernameStatusUrl() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"text":"hi","mediaDetails":[{"type":"photo","media_url_https":"https://pbs.twimg.com/media/a.jpg"}]}""",
            ),
        )

        val result = parser().parse("https://twitter.com/someone/status/2083053411524850111")

        assertEquals("image", result.type)
        assertTrue(server.takeRequest().path?.contains("id=2083053411524850111") == true)
    }

    @Test
    fun picksHighestBitrateMp4() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "视频",
                  "mediaDetails": [
                    {
                      "type": "video",
                      "media_url_https": "https://pbs.twimg.com/ext_tw_video_thumb/cover.jpg",
                      "video_info": {
                        "variants": [
                          {"bitrate": 832000, "content_type": "video/mp4", "url": "https://video.twimg.com/low.mp4"},
                          {"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/master.m3u8"},
                          {"bitrate": 2176000, "content_type": "video/mp4", "url": "https://video.twimg.com/high.mp4"}
                        ]
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/2082841167251845607")

        assertEquals("video", result.type)
        val video = result.videos.single()
        assertEquals("https://video.twimg.com/high.mp4", video.url)
        assertEquals("https://pbs.twimg.com/ext_tw_video_thumb/cover.jpg", video.cover)
        assertEquals("hd", video.quality)
    }

    @Test
    fun fallsBackToHlsWhenNoMp4() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "只有 HLS",
                  "mediaDetails": [
                    {
                      "type": "video",
                      "media_url_https": "https://pbs.twimg.com/cover.jpg",
                      "video_info": {
                        "variants": [
                          {"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/master.m3u8"}
                        ]
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/1")

        assertEquals("https://video.twimg.com/master.m3u8", result.videos.single().url)
    }

    @Test
    fun parsesAnimatedGifAsVideo() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "动图",
                  "mediaDetails": [
                    {
                      "type": "animated_gif",
                      "media_url_https": "https://pbs.twimg.com/tweet_video_thumb/gif.jpg",
                      "video_info": {
                        "variants": [
                          {"bitrate": 0, "content_type": "video/mp4", "url": "https://video.twimg.com/gif.mp4"}
                        ]
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/2")

        assertEquals("video", result.type)
        assertEquals("https://video.twimg.com/gif.mp4", result.videos.single().url)
        assertEquals("gif", result.videos.single().quality)
    }

    @Test
    fun fallsBackToQuotedTweetMedia() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "引用推文",
                  "quoted_tweet": {
                    "mediaDetails": [
                      {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/quoted.jpg"}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/3")

        assertEquals("image", result.type)
        assertEquals("https://pbs.twimg.com/media/quoted.jpg?name=orig", result.images.single().url)
    }

    @Test
    fun followsTcoRedirectToExtractId() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .addHeader("Location", server.url("/status/2083053411524850111").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        server.enqueue(
            MockResponse().setBody(
                """{"text":"短链","mediaDetails":[{"type":"photo","media_url_https":"https://pbs.twimg.com/media/s.jpg"}]}""",
            ),
        )

        val result = parser().parse(server.url("/s/xyz123").toString())

        assertEquals("短链", result.title)
        server.takeRequest()
        server.takeRequest()
        assertTrue(server.takeRequest().path?.contains("id=2083053411524850111") == true)
    }

    @Test
    fun throwsWhenNoTweetId() = runBlocking {
        try {
            parser().parse("https://x.com/home")
            fail("应当抛出 parse_failed")
        } catch (e: LocalParseException) {
            assertEquals("parse_failed", e.code)
        }
    }

    @Test
    fun throwsWhenTweetDeleted() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        try {
            parser().parse("https://x.com/i/status/9")
            fail("应当抛出 parse_failed")
        } catch (e: LocalParseException) {
            assertEquals("parse_failed", e.code)
            assertTrue(e.message.orEmpty().contains("删除"))
        }
    }

    @Test
    fun throwsOnErrorField() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"error":"No status found with that id."}"""))

        try {
            parser().parse("https://x.com/i/status/10")
            fail("应当抛出 parse_failed")
        } catch (e: LocalParseException) {
            assertEquals("parse_failed", e.code)
        }
    }
}
