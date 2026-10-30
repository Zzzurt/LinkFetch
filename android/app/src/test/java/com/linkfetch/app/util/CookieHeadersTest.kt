package com.linkfetch.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CookieHeadersTest {

    @Test
    fun mergesHeadersFromDifferentDomains() {
        assertEquals(
            "a1=abc; web_session=xyz",
            mergeCookieHeaders(listOf("a1=abc", "web_session=xyz")),
        )
    }

    @Test
    fun keepsFirstValueWhenNamesRepeat() {
        assertEquals(
            "SUB=first; ttwid=other",
            mergeCookieHeaders(listOf("SUB=first; ttwid=other", "SUB=second")),
        )
    }

    @Test
    fun toleratesNullAndBlankHeaders() {
        assertEquals(
            "t=1",
            mergeCookieHeaders(listOf(null, "", "t=1", "  ")),
        )
    }

    @Test
    fun skipsAttributeOnlyParts() {
        assertEquals(
            "passport=1",
            mergeCookieHeaders(listOf("\$Path=/; passport=1; \$Domain=.douyin.com")),
        )
    }

    @Test
    fun keepsEqualsSignsInsideValues() {
        assertEquals(
            "token=ab==; id=1",
            mergeCookieHeaders(listOf("token=ab==; id=1")),
        )
    }

    @Test
    fun returnsEmptyWhenNothingAvailable() {
        assertEquals("", mergeCookieHeaders(emptyList()))
        assertEquals("", mergeCookieHeaders(listOf(null)))
    }
}
