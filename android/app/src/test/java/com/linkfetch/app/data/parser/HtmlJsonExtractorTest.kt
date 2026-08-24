package com.linkfetch.app.data.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlJsonExtractorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun extractsObjectWithJsLiteralsAndScriptInside() {
        val html =
            "<script>window.__INITIAL_STATE__={\"a\":\"</script>{}\",\"b\":undefined,\"c\":NaN,\"d\":{\"e\":1}}</script>"

        val blob = HtmlJsonExtractor.extractJsonObject(html, "window.__INITIAL_STATE__")
        val obj = json.parseToJsonElement(blob!!).jsonObject

        assertEquals("</script>{}", obj["a"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, obj["b"])
        assertEquals(JsonNull, obj["c"])
        assertEquals("1", obj["d"]!!.jsonObject["e"]!!.jsonPrimitive.content)
    }

    @Test
    fun returnsNullWhenMarkerMissing() {
        assertNull(HtmlJsonExtractor.extractJsonObject("<html>nothing</html>", "window.__INITIAL_STATE__"))
    }

    @Test
    fun returnsNullWhenNoBraceAfterMarker() {
        assertNull(HtmlJsonExtractor.extractJsonObject("<script>window.__X__=null;</script>", "window.__X__"))
    }
}

