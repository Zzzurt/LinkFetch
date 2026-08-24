package com.linkfetch.app.data.parser

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeiboLivePhotoParserTest {

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

    private fun parser() = WeiboParser(apiBase = base)

    @Test
    fun extractsLivePhotoVideosFromPics() = runBlocking {
        val statusId = "5326855628391978"
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": {
                    "text": "<p>实况微博</p>",
                    "user": {"screen_name": "作者"},
                    "pics": [
                      {
                        "type": "livephoto",
                        "videoSrc": "https://livephoto.weibo.cn/livephoto/a.mov?Expires=1730000000&ssig=abc&KID=unistore",
                        "large": {"size": "large", "url": "https://wx1.sinaimg.cn/mw2000/a.jpg"}
                      },
                      {
                        "type": "livephoto",
                        "videoSrc": "https://livephoto.weibo.cn/livephoto/b.mov?Expires=1730000000&ssig=def%2Fxyz&KID=unistore",
                        "original": "https://wx1.sinaimg.cn/large/b.jpg"
                      },
                      {
                        "url": "https://wx1.sinaimg.cn/orj360/c.jpg",
                        "large": {"size": "large", "url": "https://wx1.sinaimg.cn/mw2000/c.jpg"}
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse(server.url("/6238113311/$statusId").toString())

        assertEquals("weibo", result.platform)
        assertEquals(3, result.images.size)
        val images = result.images
        assertTrue(images[0].live)
        assertEquals("https://livephoto.weibo.cn/livephoto/a.mov?Expires=1730000000&ssig=abc&KID=unistore", images[0].liveUrl)
        assertTrue(images[1].live)
        assertEquals("https://livephoto.weibo.cn/livephoto/b.mov?Expires=1730000000&ssig=def%2Fxyz&KID=unistore", images[1].liveUrl)
        assertFalse(images[2].live)
        assertEquals(null, images[2].liveUrl)
    }
}
