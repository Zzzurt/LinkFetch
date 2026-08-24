package com.linkfetch.app.data.parser

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * X 长视频墓碑（TweetTombstone）场景：syndication 拒绝展示该推文时，
 * 自动回退 vxtwitter / fxtwitter 第三方接口。
 */
class XParserFallbackTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String
    private lateinit var vxBase: String
    private lateinit var fxBase: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        base = server.url("/").toString().trimEnd('/')
        vxBase = server.url("/vx").toString().trimEnd('/')
        fxBase = server.url("/fx").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun parser() =
        XParser(syndicationBase = base, vxtwitterBase = vxBase, fxtwitterBase = fxBase)

    @Test
    fun fallsBackToVxTwitterOnTombstone() = runBlocking {
        // syndication 返回墓碑（用户实测长视频就是这个响应）
        server.enqueue(MockResponse().setBody("""{"__typename":"TweetTombstone","tombstone":{}}"""))
        // vxtwitter 返回完整媒体
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "长视频 via vxtwitter",
                  "user_name": "作者",
                  "user_screen_name": "author",
                  "media_extended": [
                    {"type": "video", "url": "https://video.twimg.com/vx_full.mp4", "thumbnail_url": "https://pbs.twimg.com/vx_thumb.jpg"},
                    {"type": "image", "url": "https://pbs.twimg.com/vx_img.jpg"}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/2082841167251845607")

        assertEquals("mixed", result.type)
        assertEquals("长视频 via vxtwitter", result.title)
        assertEquals("作者", result.author)
        val video = result.videos.single()
        assertEquals("https://video.twimg.com/vx_full.mp4", video.url)
        assertEquals("https://pbs.twimg.com/vx_thumb.jpg", video.cover)
        assertEquals("https://pbs.twimg.com/vx_img.jpg", result.images.single().url)

        // 墓碑响应只请求了一次 syndication，然后直接请求 vxtwitter
        assertTrue(server.takeRequest().path.orEmpty().contains("id=2082841167251845607"))
        assertTrue(server.takeRequest().path.orEmpty().contains("/vx/i/status/2082841167251845607"))
    }

    @Test
    fun fallsBackToFxTwitterWhenVxTwitterFails() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"__typename":"TweetTombstone","tombstone":{}}"""))
        // vxtwitter 404
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":404,"message":"no tweet found"}"""))
        // fxtwitter 成功
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "code": 200,
                  "message": "ok",
                  "tweet": {
                    "text": "长视频 via fxtwitter",
                    "author": {"name": "作者", "screen_name": "author"},
                    "media": {
                      "photos": [{"url": "https://pbs.twimg.com/fx_img.jpg"}],
                      "videos": [{"url": "https://video.twimg.com/fx.m3u8", "thumbnailUrl": "https://pbs.twimg.com/fx_thumb.jpg"}]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/2082841167251845607")

        assertEquals("mixed", result.type)
        assertEquals("长视频 via fxtwitter", result.title)
        assertEquals("https://video.twimg.com/fx.m3u8", result.videos.single().url)
        assertEquals("https://pbs.twimg.com/fx_thumb.jpg", result.videos.single().cover)
        assertEquals("https://pbs.twimg.com/fx_img.jpg", result.images.single().url)
        assertTrue(server.takeRequest().path.orEmpty().contains("id=2082841167251845607"))
        assertTrue(server.takeRequest().path.orEmpty().contains("/vx/i/status/"))
        assertTrue(server.takeRequest().path.orEmpty().contains("/fx/status/"))
    }

    @Test
    fun throwsWhenAllFallbacksFail() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"__typename":"TweetTombstone","tombstone":{}}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":404}"""))
        server.enqueue(
            MockResponse().setBody("""{"code":404,"message":"Tweet not found","tweet":null}"""),
        )

        try {
            parser().parse("https://x.com/i/status/2082841167251845607")
            assertTrue("应当抛出 parse_failed", false)
        } catch (e: LocalParseException) {
            assertEquals("parse_failed", e.code)
            assertEquals("该推文不包含图片或视频", e.message)
            assertTrue(e.rawBody.orEmpty().contains("TweetTombstone"))
        }
    }
}
