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
 * X 长视频：媒体不在 mediaDetails，而在 card（unified_card / amplify）或顶层 photos/video 字段。
 */
class XParserCardTest {

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
    fun parsesUnifiedCardVideo() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "长视频 unified_card",
                  "card": {
                    "name": "unified_card",
                    "binding_values": {
                      "unified_card": {
                        "string_value": "{\"media_entities\":{\"m1\":{\"type\":\"video\",\"media_url_https\":\"https://pbs.twimg.com/ext_tw_video_thumb/cover.jpg\",\"video_info\":{\"variants\":[{\"bitrate\":832000,\"content_type\":\"video/mp4\",\"url\":\"https://video.twimg.com/low.mp4\"},{\"content_type\":\"application/x-mpegURL\",\"url\":\"https://video.twimg.com/master.m3u8\"},{\"bitrate\":2176000,\"content_type\":\"video/mp4\",\"url\":\"https://video.twimg.com/high.mp4\"}]}}}}"
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/2082841167251845607")

        assertEquals("video", result.type)
        val video = result.videos.single()
        assertEquals("https://video.twimg.com/high.mp4", video.url)
        assertEquals("https://pbs.twimg.com/ext_tw_video_thumb/cover.jpg", video.cover)
    }

    @Test
    fun parsesAmplifyCardWithPlayerHls() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "amplify 长视频",
                  "card": {
                    "name": "amplify",
                    "binding_values": {
                      "player_hls_url": {"string_value": "https://video.twimg.com/amplify_v2/abc/playlist.m3u8"},
                      "player_image": {"image_value": {"url": "https://pbs.twimg.com/amplify_thumb.jpg"}}
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/2082841167251845607")

        assertEquals("video", result.type)
        val video = result.videos.single()
        assertEquals("https://video.twimg.com/amplify_v2/abc/playlist.m3u8", video.url)
        assertEquals("https://pbs.twimg.com/amplify_thumb.jpg", video.cover)
    }

    @Test
    fun parsesAmplifyCardViaVmap() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "amplify vmap",
                  "card": {
                    "name": "amplify",
                    "binding_values": {
                      "amplify_url_vmap": {"string_value": "$base/vmap.xml"}
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                <vmap:VMAP xmlns:vmap="http://www.iab.net/vmap">
                  <vmap:AdBreak><vmap:AdSource><vmap:AdData><vmap:VASTData>
                    <Ad><Creatives><Creative><Linear><MediaFiles>
                      <MediaFile type="video/mp4">https://video.twimg.com/low.mp4</MediaFile>
                      <MediaFile type="application/x-mpegURL"><![CDATA[https://video.twimg.com/amplify_v2/abc/playlist.m3u8]]></MediaFile>
                    </MediaFiles></Linear></Creative></Creatives></Ad>
                  </vmap:VASTData></vmap:AdData></vmap:AdSource></vmap:AdBreak>
                </vmap:VMAP>
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/2082841167251845607")

        assertEquals("https://video.twimg.com/amplify_v2/abc/playlist.m3u8", result.videos.single().url)
    }

    @Test
    fun parsesTopLevelPhotosAndVideo() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "顶层字段",
                  "photos": [
                    {"url": "https://pbs.twimg.com/media/a.jpg"},
                    {"url": "https://pbs.twimg.com/media/b_large.jpg"}
                  ],
                  "video": {
                    "poster": "https://pbs.twimg.com/poster.jpg",
                    "variants": [
                      {"bitrate": 1000000, "content_type": "video/mp4", "url": "https://video.twimg.com/v.mp4"},
                      {"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/v.m3u8"}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/1")

        assertEquals("mixed", result.type)
        assertEquals(2, result.images.size)
        assertEquals("https://pbs.twimg.com/media/a.jpg?name=orig", result.images[0].url)
        assertEquals("https://pbs.twimg.com/media/b.jpg?name=orig", result.images[1].url)
        assertEquals("https://video.twimg.com/v.mp4", result.videos.single().url)
        assertEquals("https://pbs.twimg.com/poster.jpg", result.videos.single().cover)
    }

    @Test
    fun parsesQuotedTweetCard() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "text": "引用长视频",
                  "quoted_tweet": {
                    "card": {
                      "name": "unified_card",
                      "binding_values": {
                        "unified_card": {
                          "string_value": "{\"media_entities\":{\"m1\":{\"type\":\"photo\",\"media_url_https\":\"https://pbs.twimg.com/media/quoted_large.jpg\"}}}"
                        }
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse("https://x.com/i/status/2")

        assertEquals("image", result.type)
        assertEquals("https://pbs.twimg.com/media/quoted.jpg?name=orig", result.images.single().url)
    }

    @Test
    fun stillFailsWhenNoMediaAnywhere() = runBlocking {
        val textOnly = """
            {
              "text": "纯文字推文",
              "card": {"name": "summary", "binding_values": {}}
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(textOnly))
        server.enqueue(MockResponse().setBody(textOnly))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))

        try {
            parser().parse("https://x.com/i/status/3")
            assertTrue("应当抛出 parse_failed", false)
        } catch (e: LocalParseException) {
            assertEquals("parse_failed", e.code)
        }
    }
}
