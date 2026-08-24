package com.linkfetch.app.data.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真实网络手动验证（需要外网 + 环境变量 LINKFETCH_REAL_TEST=1）：
 *   LINKFETCH_REAL_TEST=1 .\gradlew.bat :app:testDebugUnitTest --tests "*RealLinkManualTest"
 */
class RealLinkManualTest {

    @Test
    fun realXhsLinkParses() {
        assumeTrue(System.getenv("LINKFETCH_REAL_TEST") == "1")
        runBlocking {
            val client = LocalParseClient { null }
            val result = client.parse("http://xhslink.cn/o/1OhM4NLR50k")
            assertEquals("xhs", result.platform)
            assertTrue(result.medias.isNotEmpty())
            // 原图：fileId + JPEG 转换参数（无水印、原分辨率）
            assertTrue(result.images.first().url.contains("imageView2/0/format/jpg"))
        }
    }

    @Test
    fun realWeiboLinkParses() {
        assumeTrue(System.getenv("LINKFETCH_REAL_TEST") == "1")
        runBlocking {
            val client = LocalParseClient { null }
            val result = client.parse("https://weibo.com/6238113311/5326855628391978")
            assertEquals("weibo", result.platform)
            assertTrue(result.medias.isNotEmpty())
            // 使用 mw2000 大图而非缩略图
            assertTrue(result.images.first().url.contains("mw2000"))
        }
    }

    @Test
    fun realDouyinLinkParses() {
        assumeTrue(System.getenv("LINKFETCH_REAL_TEST") == "1")
        runBlocking {
            val client = LocalParseClient { null }
            val result = client.parse("https://v.douyin.com/5LgdumGF1iw/")
            assertEquals("douyin", result.platform)
            assertTrue(result.medias.isNotEmpty())
        }
    }

    @Test
    fun realXImageLinkParses() {
        assumeTrue(System.getenv("LINKFETCH_REAL_TEST") == "1")
        runBlocking {
            val client = LocalParseClient { null }
            val result = client.parse("https://x.com/i/status/2083053411524850111")
            assertEquals("x", result.platform)
            assertTrue(result.medias.isNotEmpty())
            assertTrue(result.images.first().url.contains("name=orig"))
        }
    }

    @Test
    fun realXVideoLinkParses() {
        assumeTrue(System.getenv("LINKFETCH_REAL_TEST") == "1")
        runBlocking {
            val client = LocalParseClient { null }
            val result = client.parse("https://x.com/i/status/2082841167251845607")
            assertEquals("x", result.platform)
            assertTrue(result.videos.isNotEmpty())
        }
    }
}

