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

    @Test
    fun fallsBackToWebViewWhenAllHttpPathsBlocked() = runBlocking {
        val contentId = "7678719774034416518"
        // 短链重定向（2 响应）+ 分享页无数据（note + slides 两条）+ PC 详情空 + SEO 两页空 + iteminfo 空
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/slides/$contentId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        repeat(6) { server.enqueue(MockResponse().setBody("<html>no router data</html>")) }
        server.enqueue(MockResponse().setBody("""{"aweme_detail":null,"status_code":0}"""))
        server.enqueue(MockResponse().setBody(""))

        // WebView 兜底返回桌面版 SSR 的 camelCase aweme 对象
        val awemeJson = """
            {"statusCode":0,"detail":{
              "desc":"WebView图集","itemTitle":"WebView图集",
              "authorInfo":{"nickname":"WebView作者"},
              "images":[
                {"urlList":["https://p3-pc-sign.douyinpic.com/a.webp","https://p3-pc-sign.douyinpic.com/a.jpeg"],
                 "video":{"playAddr":[{"src":"https://v.douyinvod.com/live1.mp4"}]}},
                {"urlList":["https://p3-pc-sign.douyinpic.com/b.jpeg"]}
              ],
              "video":{"playAddr":[{"src":"https://v.douyinvod.com/attached.mp4"}],"uri":"https://v.douyinvod.com/u.mp4","ratio":"720p"}
            }}
        """.trimIndent()
        var fetchedPageUrl: String? = null
        val parser = DouyinParser(
            shareBases = listOf(base),
            pcDetailApi = "$base/aweme/v1/web/aweme/detail/",
            noteShareUrl = "$base/share/note/%s/",
            notePageUrl = "$base/note/%s",
            webViewPageFetcher = { pageUrl ->
                fetchedPageUrl = pageUrl
                awemeJson
            },
        )
        val result = parser.parse(server.url("/s/iWebView/").toString())

        // slides 重定向 → kind=note → WebView 应请求 note 页
        assertEquals("$base/note/$contentId", fetchedPageUrl)
        assertEquals("douyin", result.platform)
        assertEquals("WebView图集", result.title)
        assertEquals("WebView作者", result.author)
        assertEquals("image", result.type)
        // 2 张图：第一张优先取非 webp 变体并识别实况图，第二张普通图
        assertEquals(2, result.medias.size)
        assertEquals("https://p3-pc-sign.douyinpic.com/a.jpeg", result.medias[0].url)
        assertEquals("https://v.douyinvod.com/live1.mp4", result.medias[0].liveUrl)
        assertTrue(result.medias[0].live)
        assertEquals("https://p3-pc-sign.douyinpic.com/b.jpeg", result.medias[1].url)
        assertFalse(result.medias[1].live)
        // 图集场景不混入顶层附属 video
        assertTrue(result.medias.none { it.isVideo })
    }

    @Test
    fun webviewFallbackParsesVideoOnlyWork() = runBlocking {
        val contentId = "7123456789012345999"
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/video/$contentId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        repeat(2) { server.enqueue(MockResponse().setBody("<html>no router data</html>")) }
        server.enqueue(MockResponse().setBody("""{"aweme_detail":null,"status_code":0}"""))
        server.enqueue(MockResponse().setBody(""))

        val awemeJson = """
            {"statusCode":0,"detail":{
              "desc":"WebView视频",
              "authorInfo":{"nickname":"视频作者"},
              "video":{
                "playAddr":[{"src":"https://v26-web.douyinvod.com/video.mp4"}],
                "uri":"https://v26-web.douyinvod.com/video.mp4",
                "ratio":"1080p",
                "cover":{"urlList":["https://p3-pc.douyinpic.com/cover.jpeg"]}
              }
            }}
        """.trimIndent()
        var fetchedPageUrl: String? = null
        val parser = DouyinParser(
            shareBases = listOf(base),
            pcDetailApi = "$base/aweme/v1/web/aweme/detail/",
            noteShareUrl = "$base/share/note/%s/",
            notePageUrl = "$base/note/%s",
            videoPageUrl = "$base/video/%s",
            webViewPageFetcher = { pageUrl ->
                fetchedPageUrl = pageUrl
                awemeJson
            },
        )
        val result = parser.parse(server.url("/s/iWvVideo/").toString())

        // video 重定向 → WebView 应请求 video 页
        assertEquals("$base/video/$contentId", fetchedPageUrl)
        assertEquals("video", result.type)
        assertEquals("https://v26-web.douyinvod.com/video.mp4", result.medias[0].url)
        assertEquals("https://p3-pc.douyinpic.com/cover.jpeg", result.medias[0].cover)
        assertEquals("1080p", result.medias[0].quality)
    }
}

