package com.linkfetch.app.data.parser

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XParserRetryTest {

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

    private fun parser() = XParser(syndicationBase = base, vxtwitterBase = base, fxtwitterBase = base)

    @Test
    fun retriesWithGooglebotWhenFirstResponseHasNoMedia() = runBlocking {
        // 第一次：普通 UA，只有文字（长视频常见场景）
        server.enqueue(MockResponse().setBody("""{"text":"纯文字推文"}"""))
        // 第二次：Googlebot UA，返回完整媒体
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "长视频",
                  "mediaDetails": [
                    {
                      "type": "video",
                      "media_url_https": "https://pbs.twimg.com/cover.jpg",
                      "video_info": {
                        "variants": [
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

        val result = parser().parse("https://x.com/i/status/1")

        assertEquals("video", result.type)
        assertEquals("https://video.twimg.com/high.mp4", result.videos.single().url)

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertTrue(first.getHeader("User-Agent").orEmpty().contains("Chrome"))
        assertTrue(second.getHeader("User-Agent").orEmpty().contains("Googlebot"))
    }

    @Test
    fun attachesRawBodyWhenNoMediaAfterBothAttempts() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"没有媒体 A","foo":1}"""))
        server.enqueue(MockResponse().setBody("""{"text":"没有媒体 B","bar":2}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))

        try {
            parser().parse("https://x.com/i/status/2")
            assertTrue("应当抛出 parse_failed", false)
        } catch (e: LocalParseException) {
            assertEquals("parse_failed", e.code)
            assertEquals("该推文不包含图片或视频", e.message)
            assertTrue(e.rawBody.orEmpty().contains("没有媒体 B"))
        }
    }
}
