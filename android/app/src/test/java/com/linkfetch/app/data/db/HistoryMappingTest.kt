package com.linkfetch.app.data.db

import com.linkfetch.app.data.model.MediaItemDto
import com.linkfetch.app.data.model.ParseResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMappingTest {

    @Test
    fun coverPrefersImageOverVideoThumbnail() {
        val response = response(
            MediaItemDto(kind = "video", url = "https://cdn/v.mp4", cover = "https://cdn/v.jpg"),
            MediaItemDto(kind = "image", url = "https://cdn/a.jpg"),
        )

        assertEquals("https://cdn/a.jpg", preferredCover(response))
    }

    @Test
    fun coverFallsBackToVideoThumbnail() {
        val response = response(
            MediaItemDto(kind = "video", url = "https://cdn/v.mp4", cover = "https://cdn/v.jpg"),
        )

        assertEquals("https://cdn/v.jpg", preferredCover(response))
    }

    @Test
    fun coverIsNullWithoutAnyImageData() {
        val response = response(MediaItemDto(kind = "video", url = "https://cdn/v.mp4"))

        assertNull(preferredCover(response))
    }

    @Test
    fun matchingTaskIsKept() {
        val media = MediaItemDto(kind = "image", url = "https://cdn/a.jpg")
        val task = task(mediaIndex = 0, url = "https://cdn/a.jpg", preferLive = false)

        assertEquals(emptyList<Long>(), outdatedTaskIds(listOf(task), listOf(media)))
        assertEquals(emptyList<Long>(), removedTaskIds(listOf(task), listOf(media)))
    }

    @Test
    fun taskForRemovedMediaIsRemoved() {
        val media = MediaItemDto(kind = "image", url = "https://cdn/a.jpg")
        val task = task(id = 9L, mediaIndex = 3, url = "https://cdn/old.jpg", preferLive = false)

        assertEquals(listOf(9L), removedTaskIds(listOf(task), listOf(media)))
        assertEquals(emptyList<Long>(), outdatedTaskIds(listOf(task), listOf(media)))
    }

    @Test
    fun taskWithChangedUrlNeedsRefresh() {
        val media = MediaItemDto(kind = "image", url = "https://cdn/new.jpg")
        val task = task(id = 7L, mediaIndex = 0, url = "https://cdn/old.jpg", preferLive = false)

        assertEquals(listOf(7L), outdatedTaskIds(listOf(task), listOf(media)))
    }

    @Test
    fun doneTaskKeepsItsStateWhenUrlChanges() {
        val media = MediaItemDto(kind = "image", url = "https://cdn/new.jpg")
        val task = task(
            id = 8L,
            mediaIndex = 0,
            url = "https://cdn/old.jpg",
            preferLive = false,
            status = DownloadStatus.DONE,
        )

        assertEquals(emptyList<Long>(), outdatedTaskIds(listOf(task), listOf(media)))
    }

    @Test
    fun liveVariantNeedsRefreshOnlyWhenItsLiveUrlChanges() {
        val media = MediaItemDto(
            kind = "image",
            url = "https://cdn/a.jpg",
            live = true,
            liveUrl = "https://cdn/new.mov",
        )
        val liveTask = task(
            id = 1L,
            mediaIndex = 0,
            url = "https://cdn/a.jpg",
            liveUrl = "https://cdn/old.mov",
            preferLive = true,
        )
        val staticTask = task(
            id = 2L,
            mediaIndex = 0,
            url = "https://cdn/a.jpg",
            liveUrl = "https://cdn/old.mov",
            preferLive = false,
        )

        assertEquals(listOf(1L), outdatedTaskIds(listOf(liveTask, staticTask), listOf(media)))
    }

    private fun response(vararg medias: MediaItemDto) = ParseResponseDto(
        platform = "xhs",
        title = "标题",
        type = "image",
        medias = medias.toList(),
    )

    private fun task(
        id: Long = 1L,
        mediaIndex: Int,
        url: String,
        liveUrl: String? = null,
        preferLive: Boolean,
        status: String = DownloadStatus.QUEUED,
    ) = DownloadTaskEntity(
        id = id,
        historyId = 1L,
        mediaIndex = mediaIndex,
        url = url,
        liveUrl = liveUrl,
        isVideo = false,
        live = liveUrl != null,
        preferLive = preferLive,
        prefix = "标题",
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
