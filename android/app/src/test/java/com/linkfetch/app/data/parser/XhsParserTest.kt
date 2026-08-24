package com.linkfetch.app.data.parser

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XhsParserTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun exploreHtml(noteId: String, state: String) =
        "<html><script>window.__INITIAL_STATE__=$state</script></html>"

    private fun redirectTo(path: String) =
        MockResponse().setResponseCode(302).addHeader("Location", server.url(path).toString())

    @Test
    fun parsesNewNoteDataStructureWithOriginalImageUrls() = runBlocking {
        val noteId = "6a6c672e0000000005029cb8"
        server.enqueue(
            redirectTo("/discovery/item/$noteId?xsec_token=TOK%3D&xsec_source=app_share"),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        val state = """
            {"noteData":{"data":{"noteData":{
              "title":"测试笔记🌸",
              "desc":"",
              "type":"normal",
              "user":{"nickName":"作者甲"},
              "imageList":[
                {"fileId":"fileabc123","url":"http://sns-webpic-qc.xhscdn.com/2026/01/fileabc123!h5_1080jpg"},
                {"fileId":"filedef456","url":"http://sns-webpic-h.xhscdn.com/2026/01/filedef456!h5_1080jpg"}
              ]
            }}},"other":undefined}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(exploreHtml(noteId, state)))

        val result = XhsParser().parse(server.url("/s/AbC").toString())

        assertEquals("xhs", result.platform)
        assertEquals("测试笔记🌸", result.title)
        assertEquals("作者甲", result.author)
        assertEquals("image", result.type)
        assertEquals(2, result.medias.size)
        // 使用 fileId 原图 URL：原分辨率 JPEG、无水印
        assertEquals(
            "https://sns-img-qc.xhscdn.com/fileabc123?imageView2/0/format/jpg",
            result.medias[0].url,
        )
        assertEquals(
            "https://sns-img-h.xhscdn.com/filedef456?imageView2/0/format/jpg",
            result.medias[1].url,
        )
    }

    @Test
    fun fallsBackToLegacyNoteDetailMap() = runBlocking {
        val noteId = "6a6c672e0000000005029cb8"
        server.enqueue(redirectTo("/explore/$noteId"))
        server.enqueue(MockResponse().setBody("redirect target"))
        val state = """
            {"note":{"noteDetailMap":{"$noteId":{"note":{
              "title":"旧版笔记",
              "type":"video",
              "user":{"nickname":"作者乙"},
              "imageList":[
                {"urlDefault":"https://sns-webpic-h.xhscdn.com/abc/03!nd_dft_wl_watermark_webp"}
              ],
              "video":{"consumer":{"originVideoKey":"video_key_456"}}
            }}}}}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(exploreHtml(noteId, state)))

        val result = XhsParser().parse(server.url("/s/De").toString())

        assertEquals("旧版笔记", result.title)
        assertEquals("作者乙", result.author)
        assertEquals("mixed", result.type)
        assertEquals("https://sns-video-bd.xhscdn.com/video_key_456", result.videos.first().url)
        // 旧版水印后缀会被剥离并切到原图域名
        assertTrue(result.images.first().url.startsWith("https://sns-img-h.xhscdn.com/abc/03"))
    }

    @Test
    fun throwsFriendlyErrorWhenNoteMissing() = runBlocking {
        val noteId = "6a6c672e0000000005029cb8"
        server.enqueue(redirectTo("/discovery/item/$noteId"))
        server.enqueue(MockResponse().setBody("redirect target"))
        server.enqueue(
            MockResponse().setBody("<html><script>window.__INITIAL_STATE__={\"noteData\":{\"data\":{}}}</script></html>"),
        )

        val exception = runCatching { XhsParser().parse(server.url("/s/None").toString()) }.exceptionOrNull()

        assertTrue(exception is LocalParseException)
        assertEquals("parse_failed", (exception as LocalParseException).code)
    }
}

