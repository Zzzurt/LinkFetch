package com.linkfetch.app.data.parser

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeiboParserTest {

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
    fun parsesMixedVideoAndImages() = runBlocking {
        val statusId = "AbCdEf123"
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/status/$statusId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": {
                    "text": "<p>测试微博</p>",
                    "user": {"screen_name": "微博作者"},
                    "pics": [
                      {
                        "url": "https://wx1.sinaimg.cn/orj360/a.jpg",
                        "large": {"size": "large", "url": "https://wx1.sinaimg.cn/mw2000/a.jpg"}
                      },
                      {
                        "url": "https://wx1.sinaimg.cn/orj360/b.jpg",
                        "large": {"size": "large", "url": "https://wx1.sinaimg.cn/mw2000/b.jpg"}
                      }
                    ],
                    "page_info": {
                      "page_pic": "https://wx1.sinaimg.cn/large/cover.jpg",
                      "media_info": {"mp4_hd_url": "https://f.video.weibocdn.com/hd.mp4"}
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse(server.url("/s/A6xYz").toString())

        assertEquals("weibo", result.platform)
        assertEquals("测试微博", result.title)
        assertEquals("微博作者", result.author)
        assertEquals("mixed", result.type)
        assertEquals("https://f.video.weibocdn.com/hd.mp4", result.videos.first().url)
        // 大图字段是对象时取 mw2000 原图
        assertEquals("https://wx1.sinaimg.cn/mw2000/a.jpg", result.images[0].url)
        assertEquals("https://wx1.sinaimg.cn/mw2000/b.jpg", result.images[1].url)
    }

    @Test
    fun parsesDesktopUidStatusUrlDirectly() = runBlocking {
        val statusId = "5326855628391978"
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"text":"只有图片","user":{"screen_name":"作者"},"pics":[{"url":"https://wx4.sinaimg.cn/large/a.jpg"}]}}""",
            ),
        )

        // 桌面端链接直接包含 /<uid>/<id>，无需展开即可提取
        val result = parser().parse(server.url("/6238113311/$statusId").toString())

        assertEquals("image", result.type)
        assertEquals(1, result.medias.size)
        assertTrue(result.medias[0].url.startsWith("https://wx4.sinaimg.cn/"))
        val recorded = server.takeRequest()
        assertTrue(recorded.path?.contains("id=$statusId") == true)
    }

    @Test
    fun completesMixedPostMediaBeyondFirstNineTiles() = runBlocking {
        val statusId = "Rkw7rC72F"
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": {
                    "text": "混合发布",
                    "user": {"screen_name": "作者"},
                    "pic_num": 4,
                    "pic_ids": ["pidA", "pidB", "pidC", "pidD"],
                    "mix_media_ids": ["pidA", "http://t.cn/AVideo1", "pidB", "http://t.cn/AVideo2"],
                    "pics": [
                      {"pid": "pidA", "url": "https://wx1.sinaimg.cn/orj360/a.jpg"},
                      {
                        "pid": "poster1",
                        "type": "video",
                        "videoSrc": "https://f.video.weibocdn.com/v1.mp4",
                        "large": {"url": "https://wx1.sinaimg.cn/large/poster1.jpg"}
                      },
                      {
                        "pid": "pidB",
                        "type": "livephoto",
                        "videoSrc": "https://livephoto.weibo.cn/b.mov",
                        "large": {"url": "https://wx2.sinaimg.cn/mw2000/b.jpg"}
                      },
                      {
                        "pid": "poster2",
                        "type": "video",
                        "videoSrc": "https://f.video.weibocdn.com/v2.mp4",
                        "url": "https://wx3.sinaimg.cn/orj360/poster2.jpg"
                      }
                    ],
                    "page_info": {
                      "page_pic": "https://wx1.sinaimg.cn/large/poster1.jpg",
                      "media_info": {"stream_url_hd": "https://f.video.weibocdn.com/v1-540p.mp4"}
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = parser().parse(server.url("/6238113311/$statusId").toString())

        assertEquals("mixed", result.type)
        // 前 9 个媒体按 mix 顺序还原，第 10 个起用 pic_ids 补全（此前只会返回前 9 个）
        assertEquals(6, result.medias.size)
        assertEquals(listOf("image", "video", "image", "video", "image", "image"), result.medias.map { it.kind })
        assertEquals("https://f.video.weibocdn.com/v1.mp4", result.videos[0].url)
        assertEquals("https://wx1.sinaimg.cn/large/poster1.jpg", result.videos[0].cover)
        assertEquals("https://f.video.weibocdn.com/v2.mp4", result.videos[1].url)
        // Live 图在前 9 个媒体内保留 .mov 直链
        assertTrue(result.images[1].live)
        assertEquals("https://livephoto.weibo.cn/b.mov", result.images[1].liveUrl)
        // 第 10 个媒体起只有 pic_id，按 id 拼原图直链
        assertEquals("https://wx1.sinaimg.cn/large/pidC.jpg", result.images[2].url)
        assertEquals("https://wx1.sinaimg.cn/large/pidD.jpg", result.images[3].url)
    }

    @Test
    fun sendsXRequestedWithHeader() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"data":{"text":"hi","pics":[{"url":"https://wx4.sinaimg.cn/large/a.jpg"}]}}"""),
        )

        parser().parse(server.url("/6238113311/AbC123").toString())

        assertEquals("XMLHttpRequest", server.takeRequest().getHeader("X-Requested-With"))
    }
}

