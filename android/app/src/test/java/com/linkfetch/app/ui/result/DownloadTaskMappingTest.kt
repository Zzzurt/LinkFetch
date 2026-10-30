package com.linkfetch.app.ui.result

import com.linkfetch.app.data.db.DownloadStatus
import com.linkfetch.app.data.db.DownloadTaskEntity
import com.linkfetch.app.data.model.MediaItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskMappingTest {

    private fun task(
        index: Int,
        status: String,
        preferLive: Boolean = false,
        progress: Float = 0f,
        error: String? = null,
    ) = DownloadTaskEntity(
        id = index.toLong(),
        historyId = 1L,
        mediaIndex = index,
        url = "https://example.com/$index.jpg",
        liveUrl = "https://example.com/$index.mp4",
        isVideo = false,
        live = true,
        preferLive = preferLive,
        prefix = "t",
        status = status,
        progress = progress,
        error = error,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private val liveMedia = MediaItemDto(
        kind = "image",
        url = "https://example.com/0.jpg",
        live = true,
        liveUrl = "https://example.com/0.mp4",
    )

    @Test
    fun emptyTasksMapToEmptyStates() {
        assertTrue(downloadStates(emptyList(), listOf(liveMedia)).isEmpty())
    }

    @Test
    fun runningAndFailedTasksCarryTheirDetails() {
        val tasks = listOf(
            task(index = 0, status = DownloadStatus.RUNNING, progress = 0.42f),
            task(index = 1, status = DownloadStatus.FAILED, error = "HTTP 403"),
        )

        val states = downloadStates(tasks, listOf(liveMedia, liveMedia))

        assertEquals(ItemState.Downloading(0.42f), states[0])
        assertEquals(ItemState.Failed("HTTP 403"), states[1])
    }

    @Test
    fun liveVariantWinsOverStaticWhenBothExist() {
        val tasks = listOf(
            task(index = 0, status = DownloadStatus.DONE, preferLive = false),
            task(index = 0, status = DownloadStatus.RUNNING, preferLive = true, progress = 0.5f),
        )

        val states = downloadStates(tasks, listOf(liveMedia))

        assertEquals(ItemState.Downloading(0.5f), states[0])
    }

    @Test
    fun queuedTaskShowsPendingProgress() {
        val states = downloadStates(
            listOf(task(index = 0, status = DownloadStatus.QUEUED)),
            listOf(liveMedia),
        )

        assertEquals(ItemState.Downloading(0f), states[0])
    }
}
