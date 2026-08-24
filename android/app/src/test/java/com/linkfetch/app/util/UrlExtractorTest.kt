package com.linkfetch.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun extractsDouyinFromChineseText() {
        assertEquals(
            "https://v.douyin.com/iAbCdEf/",
            UrlExtractor.extractFirstPlatformUrl("看这个视频 https://v.douyin.com/iAbCdEf/ 太棒了"),
        )
    }

    @Test
    fun extractsXhsFromText() {
        assertEquals(
            "https://xhslink.com/a/AbC12",
            UrlExtractor.extractFirstPlatformUrl("复制这条链接 https://xhslink.com/a/AbC12 打开小红书"),
        )
    }

    @Test
    fun extractsXFromShareText() {
        assertEquals(
            "https://x.com/i/status/2083053411524850111",
            UrlExtractor.extractFirstPlatformUrl("看看这个 https://x.com/i/status/2083053411524850111 转发"),
        )
    }

    @Test
    fun extractsUrlFromShareTextWithEmoji() {
        val text = "一些记录🌸☁️💜🐶🚗 http://xhslink.cn/o/1OhM4NLR50k \n复制后直奔【小红书】，笔记等你来翻~"
        assertEquals(
            "http://xhslink.cn/o/1OhM4NLR50k",
            UrlExtractor.extractFirstPlatformUrl(text),
        )
    }

    @Test
    fun extractsUrlWrappedInBrackets() {
        assertEquals(
            "https://v.douyin.com/iAbCdEf/",
            UrlExtractor.extractFirstPlatformUrl("【https://v.douyin.com/iAbCdEf/】"),
        )
    }

    @Test
    fun extractsUrlBeforeFullWidthPunctuation() {
        assertEquals(
            "https://m.weibo.cn/status/1234567890",
            UrlExtractor.extractFirstPlatformUrl("https://m.weibo.cn/status/1234567890。"),
        )
    }

    @Test
    fun stripsTrailingPunctuation() {
        assertEquals(
            "https://t.cn/A6xYz",
            UrlExtractor.extractFirstPlatformUrl("复制 https://t.cn/A6xYz，打开微博看看"),
        )
    }

    @Test
    fun extractsWeiboMobileUrl() {
        assertEquals(
            "https://m.weibo.cn/status/1234567890",
            UrlExtractor.extractFirstPlatformUrl("https://m.weibo.cn/status/1234567890"),
        )
    }

    @Test
    fun returnsNullForUnsupportedPlatform() {
        assertNull(UrlExtractor.extractFirstPlatformUrl("https://www.bilibili.com/video/BV1xx"))
    }

    @Test
    fun returnsNullWhenNoUrl() {
        assertNull(UrlExtractor.extractFirstPlatformUrl("这里没有链接"))
    }

    @Test
    fun returnsNullWhenTextBlank() {
        assertNull(UrlExtractor.extractFirstPlatformUrl(""))
    }

    @Test
    fun platformFromUrlCases() {
        assertEquals(Platform.XHS, Platform.fromUrl("https://www.xiaohongshu.com/explore/64abc"))
        assertEquals(Platform.XHS, Platform.fromUrl("https://xhslink.com/a/AbC12"))
        assertEquals(Platform.DOUYIN, Platform.fromUrl("https://v.douyin.com/iAbCdEf/"))
        assertEquals(Platform.DOUYIN, Platform.fromUrl("https://www.douyin.com/video/7123456789"))
        assertEquals(Platform.WEIBO, Platform.fromUrl("https://t.cn/A6xYz"))
        assertEquals(Platform.WEIBO, Platform.fromUrl("https://m.weibo.cn/status/123"))
        assertEquals(Platform.X, Platform.fromUrl("https://x.com/i/status/2083053411524850111"))
        assertEquals(Platform.X, Platform.fromUrl("https://twitter.com/user/status/2083053411524850111"))
        assertEquals(Platform.X, Platform.fromUrl("https://t.co/abc123"))
        assertNull(Platform.fromUrl("https://example.com/x"))
        assertNull(Platform.fromUrl("not a url"))
    }

    @Test
    fun platformFromKey() {
        assertEquals(Platform.XHS, Platform.fromKey("xhs"))
        assertEquals(Platform.DOUYIN, Platform.fromKey("douyin"))
        assertEquals(Platform.WEIBO, Platform.fromKey("weibo"))
        assertEquals(Platform.X, Platform.fromKey("x"))
        assertNull(Platform.fromKey("unknown"))
        assertNull(Platform.fromKey(null))
    }
}
