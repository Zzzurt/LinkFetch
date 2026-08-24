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

class XhsLivePhotoParserTest {

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

    @Test
    fun extractsLivePhotoVideosFromMultipleStructures() = runBlocking {
        val noteId = "6a6c672e0000000005029cb8"
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/discovery/item/$noteId").toString()),
        )
        server.enqueue(MockResponse().setBody("redirect target"))
        val state = """
            {"noteData":{"data":{"noteData":{
              "title":"实况笔记",
              "user":{"nickName":"作者"},
              "imageList":[
                {"fileId":"imgOld","url":"http://sns-webpic-qc.xhscdn.com/imgOld.jpg",
                 "stream":{"h264":[{"masterUrl":"https://sns-video-bd.xhscdn.com/old_live.mp4"}]}},
                {"fileId":"imgNew","url":"http://sns-webpic-qc.xhscdn.com/imgNew.jpg",
                 "livePhoto":{"media":{"stream":{"h264":[{"masterUrl":"https://sns-video-bd.xhscdn.com/new_live.mp4"}]}}}},
                {"fileId":"imgFallback","url":"http://sns-webpic-qc.xhscdn.com/imgFallback.jpg",
                 "livePhotoVideo":"https://sns-video-bd.xhscdn.com/fallback_live.mp4"},
                {"fileId":"imgPlain","url":"http://sns-webpic-qc.xhscdn.com/imgPlain.jpg","livePhoto":false}
              ]
            }}}}
        """.trimIndent()
        server.enqueue(MockResponse().setBody("<html><script>window.__INITIAL_STATE__=$state</script></html>"))

        val result = XhsParser().parse(server.url("/s/Live").toString())

        assertEquals("xhs", result.platform)
        assertEquals(4, result.images.size)
        val images = result.images
        assertTrue(images[0].live)
        assertEquals("https://sns-video-bd.xhscdn.com/old_live.mp4", images[0].liveUrl)
        assertTrue(images[1].live)
        assertEquals("https://sns-video-bd.xhscdn.com/new_live.mp4", images[1].liveUrl)
        assertTrue(images[2].live)
        assertEquals("https://sns-video-bd.xhscdn.com/fallback_live.mp4", images[2].liveUrl)
        assertFalse(images[3].live)
        assertEquals(null, images[3].liveUrl)
    }
}
