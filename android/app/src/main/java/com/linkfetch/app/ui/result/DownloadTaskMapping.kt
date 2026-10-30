package com.linkfetch.app.ui.result

import com.linkfetch.app.data.db.DownloadStatus
import com.linkfetch.app.data.db.DownloadTaskEntity
import com.linkfetch.app.data.model.MediaItemDto

/**
 * 任务表 -> 媒体下标状态的映射（纯函数，便于单测）。
 *
 * 同一条媒体可能有 Live / 静态两个变体：优先展示用户最近一次选择保存的那种。
 */
internal fun downloadStates(
    tasks: List<DownloadTaskEntity>,
    medias: List<MediaItemDto>,
): Map<Int, ItemState> {
    if (tasks.isEmpty()) return emptyMap()
    return tasks.groupBy { it.mediaIndex }
        .mapNotNull { (index, variants) ->
            val media = medias.getOrNull(index)
            val preferred = if (media?.live == true && !media.liveUrl.isNullOrBlank()) {
                variants.firstOrNull { it.preferLive } ?: variants.firstOrNull()
            } else {
                variants.firstOrNull { !it.preferLive } ?: variants.firstOrNull()
            }
            preferred?.let { index to it.toItemState() }
        }
        .toMap()
}

private fun DownloadTaskEntity.toItemState(): ItemState = when (status) {
    DownloadStatus.RUNNING -> ItemState.Downloading(progress.coerceIn(0f, 1f))
    DownloadStatus.QUEUED -> ItemState.Downloading(0f)
    DownloadStatus.DONE -> ItemState.Done
    DownloadStatus.FAILED -> ItemState.Failed(error ?: "保存失败")
    else -> ItemState.Idle
}
