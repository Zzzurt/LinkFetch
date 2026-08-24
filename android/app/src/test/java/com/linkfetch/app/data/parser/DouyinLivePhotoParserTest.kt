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

class DouyinLivePhotoParserTest {

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
    fun extractsLivePhotoVideosFromImages() = runBlocking {
        val contentId = "7668346756942644842"
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/note/$contentId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        val state = """
            {"loaderData":{"note_(id)/page":{"videoInfoRes":{"item_list":[
              {
                "desc":"实况图集",
                "author":{"nickname":"作者"},
                "images":[
                  {"url_list":["https://p3-sign.douyinpic.com/img1.jpeg"],
                   "video":{"play_addr":{"url_list":["https://aweme.snssdk.com/aweme/v1/play/?video_id=live1","https://aweme.snssdk.com/aweme/v1/playwm/?video_id=live1_wm"]}}},
                  {"url_list":["https://p3-sign.douyinpic.com/img2.jpeg"],
                   "video":{"play_addr":{"uri":"v0300f10000live2"}}},
                  {"url_list":["https://p3-sign.douyinpic.com/img3.jpeg"]}
                ]
              }
            ]}}}}
        """.trimIndent()
        server.enqueue(
            MockResponse().setBody("<html><script>window._ROUTER_DATA=$state</script></html>"),
        )

        val result = parser().parse(server.url("/s/Live").toString())

        assertEquals("douyin", result.platform)
        assertEquals(3, result.images.size)
        val images = result.images
        assertTrue(images[0].live)
        assertEquals("https://aweme.snssdk.com/aweme/v1/play/?video_id=live1", images[0].liveUrl)
        assertTrue(images[1].live)
        assertEquals(
            "https://aweme.snssdk.com/aweme/v1/play/?video_id=v0300f10000live2&ratio=1080p&line=0",
            images[1].liveUrl,
        )
        assertFalse(images[2].live)
        assertEquals(null, images[2].liveUrl)
    }
}
