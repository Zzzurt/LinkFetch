package com.linkfetch.app.data.parser

import com.linkfetch.app.data.download.HlsException
import com.linkfetch.app.data.download.HlsPlaylistResolver
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HlsPlaylistResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: HlsPlaylistResolver

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = HlsPlaylistResolver(
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun picksHighestBandwidthVariantAndResolvesSegments() = runBlocking {
        val masterUrl = server.url("/videos/master.m3u8").toString()
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
                low/index.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
                high/index.m3u8
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:6
                #EXTINF:6.0,
                seg1.ts
                #EXTINF:6.0,
                seg2.ts
                #EXTINF:3.0,
                seg3.ts
                """.trimIndent(),
            ),
        )

        val playlist = resolver.resolve(masterUrl)

        assertEquals("video/mp2t", playlist.mime)
        assertEquals("ts", playlist.ext)
        assertNull(playlist.initSegment)
        assertEquals(
            listOf(
                server.url("/videos/high/seg1.ts").toString(),
                server.url("/videos/high/seg2.ts").toString(),
                server.url("/videos/high/seg3.ts").toString(),
            ),
            playlist.segments,
        )
        assertEquals("/videos/master.m3u8", server.takeRequest().path)
        assertEquals("/videos/high/index.m3u8", server.takeRequest().path)
    }

    @Test
    fun parsesFmp4WithInitSegment() = runBlocking {
        val mediaUrl = server.url("/videos/media.m3u8").toString()
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U
                #EXT-X-VERSION:7
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:6.0,
                seg1.m4s
                #EXTINF:6.0,
                seg2.m4s
                """.trimIndent(),
            ),
        )

        val playlist = resolver.resolve(mediaUrl)

        assertEquals("video/mp4", playlist.mime)
        assertEquals("mp4", playlist.ext)
        assertEquals(server.url("/videos/init.mp4").toString(), playlist.initSegment)
        assertEquals(2, playlist.segments.size)
    }

    @Test
    fun throwsOnEncryptedStream() = runBlocking {
        val mediaUrl = server.url("/videos/enc.m3u8").toString()
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                #EXTINF:6.0,
                seg1.ts
                """.trimIndent(),
            ),
        )

        try {
            resolver.resolve(mediaUrl)
            fail("应当抛出 HlsException")
        } catch (e: HlsException) {
            assertTrue(e.message.orEmpty().contains("加密"))
        }
    }

    @Test
    fun throwsOnInvalidPlaylist() = runBlocking {
        val url = server.url("/videos/bad.m3u8").toString()
        server.enqueue(MockResponse().setBody("#EXTM3U\n# nothing useful"))

        try {
            resolver.resolve(url)
            fail("应当抛出 HlsException")
        } catch (e: HlsException) {
            assertTrue(true)
        }
    }
}
