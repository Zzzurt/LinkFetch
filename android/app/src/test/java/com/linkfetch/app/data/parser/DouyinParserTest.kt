package com.linkfetch.app.data.parser

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DouyinParserTest {

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

    private fun parser() = DouyinParser(shareBases = listOf(base))

    @Test
    fun parsesVideoFromSharePage() = runBlocking {
        val contentId = "7123456789012345678"
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/video/$contentId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        val state = """
            {"loaderData":{"video_(id)/page":{"videoInfoRes":{"item_list":[
              {
                "desc":"测试视频",
                "author":{"nickname":"抖音作者"},
                "video":{
                  "play_addr":{"uri":"v0300f10000abc","url_list":["https://www.douyin.com/play"]},
                  "cover":{"url_list":["https://p3.douyinpic.com/cover.jpg"]}
                }
              }
            ]}}}}
        """.trimIndent()
        server.enqueue(
            MockResponse().setBody("<html><script>window._ROUTER_DATA=$state</script></html>"),
        )

        val result = parser().parse(server.url("/s/iAbCdEf/").toString())

        assertEquals("douyin", result.platform)
        assertEquals("测试视频", result.title)
        assertEquals("video", result.type)
        assertEquals(
            "https://aweme.snssdk.com/aweme/v1/play/?video_id=v0300f10000abc&ratio=1080p&line=0",
            result.medias[0].url,
        )
        assertEquals("https://p3.douyinpic.com/cover.jpg", result.medias[0].cover)
    }

    @Test
    fun parsesNoteImagesFromVideoInfoRes() = runBlocking {
        val contentId = "7668346756942644842"
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/note/$contentId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        val state = """
            {"loaderData":{"note_(id)/page":{"videoInfoRes":{"item_list":[
              {
                "desc":"",
                "author":{"nickname":"图文作者"},
                "images":[
                  {"url_list":["https://p3-sign.douyinpic.com/img1.jpeg"]},
                  {"url_list":["https://p3-sign.douyinpic.com/img2.jpeg"]}
                ],
                "video":{"play_addr":{"uri":"https://sf6-cdn-tos.douyinstatic.com/obj/video.mp4"},"cover":{"url_list":["https://p3.douyinpic.com/c.jpg"]}}
              }
            ]}}}}
        """.trimIndent()
        server.enqueue(
            MockResponse().setBody("<html><script>window._ROUTER_DATA=$state</script></html>"),
        )

        val result = parser().parse(server.url("/s/iGhIjKl/").toString())

        assertEquals("mixed", result.type)
        assertEquals(3, result.medias.size)
        // play_addr.uri 是完整 URL 时直接使用
        assertEquals("https://sf6-cdn-tos.douyinstatic.com/obj/video.mp4", result.videos.first().url)
        assertTrue(result.images.all { it.url.startsWith("https://p3-sign.douyinpic.com/") })
    }

    @Test
    fun fallsBackToIteminfoWhenSharePageMissing() = runBlocking {
        val contentId = "7123456789012345680"
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/video/$contentId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        server.enqueue(MockResponse().setBody("<html>no router data</html>"))
        server.enqueue(
            MockResponse().setBody(
                """{"item_list":[{"desc":"回退视频","author":{"nickname":"作者"},"video":{"play_addr":{"uri":"v0300fbbbb","url_list":[]}}}]}""",
            ),
        )

        val result = parser().parse(server.url("/s/iFallback/").toString())

        assertEquals("回退视频", result.title)
        assertEquals(
            "https://aweme.snssdk.com/aweme/v1/play/?video_id=v0300fbbbb&ratio=1080p&line=0",
            result.medias[0].url,
        )
    }

    @Test
    fun fallsBackToPcDetailWhenSharePageMissing() = runBlocking {
        val contentId = "7123456789012345678"
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/video/$contentId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        server.enqueue(MockResponse().setBody("<html>no router data</html>"))
        server.enqueue(
            MockResponse().setBody(
                """{"status_code":0,"aweme_detail":{"desc":"PC详情视频","author":{"nickname":"视频作者"},"video":{"play_addr":{"uri":"v0200pc00001","url_list":[]},"cover":{"url_list":["https://p3.douyinpic.com/pc_cover.jpg"]}}}}""",
            ),
        )

        val parser = DouyinParser(
            shareBases = listOf(base),
            pcDetailApi = "$base/aweme/v1/web/aweme/detail/",
        )
        val result = parser.parse(server.url("/s/iPcDetail/").toString())

        assertEquals("douyin", result.platform)
        assertEquals("PC详情视频", result.title)
        assertEquals("视频作者", result.author)
        assertEquals("video", result.type)
        assertEquals(
            "https://aweme.snssdk.com/aweme/v1/play/?video_id=v0200pc00001&ratio=1080p&line=0",
            result.medias[0].url,
        )
        assertEquals("https://p3.douyinpic.com/pc_cover.jpg", result.medias[0].cover)
    }

    @Test
    fun parsesNoteFromSeoLdJsonWhenSharePageMissing() = runBlocking {
        val contentId = "7668346756942644842"
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/note/$contentId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        server.enqueue(MockResponse().setBody("<html>no router data</html>"))
        server.enqueue(MockResponse().setBody("<html>no router data</html>"))
        server.enqueue(
            MockResponse().setBody(
                """{"aweme_detail":null,"filter_detail":{"filter_reason":"images_base"},"status_code":0}""",
            ),
        )
        val ldHtml =
            """<html><script type="application/ld+json">{"@context":"https://schema.org","@type":"article","headline":"图","articleBody":"图文笔记标题","image":["https://p3-pc-sign.douyinpic.com/img1.jpeg","https://p3-pc-sign.douyinpic.com/img2.jpeg"],"author":{"@type":"Person","name":"图文作者"}}</script></html>"""
        server.enqueue(MockResponse().setBody(ldHtml))

        val parser = DouyinParser(
            shareBases = listOf(base),
            pcDetailApi = "$base/aweme/v1/web/aweme/detail/",
            noteShareUrl = "$base/share/note/%s/",
        )
        val result = parser.parse(server.url("/s/iSeoNote/").toString())

        assertEquals("douyin", result.platform)
        assertEquals("图文笔记标题", result.title)
        assertEquals("图文作者", result.author)
        assertEquals("image", result.type)
        assertEquals(
            listOf(
                "https://p3-pc-sign.douyinpic.com/img1.jpeg",
                "https://p3-pc-sign.douyinpic.com/img2.jpeg",
            ),
            result.images.map { it.url },
        )
    }
}

