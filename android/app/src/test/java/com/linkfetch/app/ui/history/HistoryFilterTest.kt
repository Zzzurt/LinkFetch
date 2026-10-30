package com.linkfetch.app.ui.history

import com.linkfetch.app.data.db.HistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {

    private val items = listOf(
        entity(id = 1, platform = "xhs", title = "露营笔记", author = "阿岚"),
        entity(id = 2, platform = "douyin", title = "SUNSET 航拍", author = null),
        entity(id = 3, platform = "weibo", title = "城市夜景", author = "阿岚"),
    )

    @Test
    fun allFilterReturnsEverything() {
        assertEquals(items, filterHistory(items, HistoryViewModel.FILTER_ALL, ""))
    }

    @Test
    fun platformFilterKeepsOnlyThatPlatform() {
        assertEquals(
            listOf(1L),
            filterHistory(items, "xhs", "").map { it.id },
        )
    }

    @Test
    fun queryMatchesTitleIgnoringCase() {
        assertEquals(
            listOf(2L),
            filterHistory(items, HistoryViewModel.FILTER_ALL, "sunset").map { it.id },
        )
    }

    @Test
    fun queryMatchesAuthor() {
        assertEquals(
            listOf(1L, 3L),
            filterHistory(items, HistoryViewModel.FILTER_ALL, "阿岚").map { it.id },
        )
    }

    @Test
    fun blankQueryDoesNotFilter() {
        assertEquals(items, filterHistory(items, HistoryViewModel.FILTER_ALL, "   "))
    }

    private fun entity(
        id: Long,
        platform: String,
        title: String,
        author: String?,
    ) = HistoryEntity(
        id = id,
        platform = platform,
        title = title,
        author = author,
        type = "image",
        originalUrl = "https://example.com/$id",
        mediaJson = "{}",
        createdAt = 0L,
    )
}
