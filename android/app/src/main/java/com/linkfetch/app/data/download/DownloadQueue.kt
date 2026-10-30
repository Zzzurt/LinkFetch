package com.linkfetch.app.data.download

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.linkfetch.app.data.db.DownloadStatus
import com.linkfetch.app.data.db.DownloadTaskDao
import com.linkfetch.app.data.db.DownloadTaskEntity
import com.linkfetch.app.data.model.ParseResponseDto

/**
 * 把「保存」从一次性协程变成可恢复的任务队列。
 *
 * 任务先落 Room 再交给 WorkManager：Worker 只拿到任务 id，进度、成功与失败都写回同一张表，
 * 因此退出结果页、切后台、进程被回收都不会让状态丢失，UI 回来时还能看到「已保存」。
 */
class DownloadQueue(
    private val dao: DownloadTaskDao,
    private val workManager: WorkManager,
) {
    /**
     * 为 [indices] 建任务并排队。已存在且未失败的任务会被跳过（避免重复保存），
     * 失败过的任务重新入队。返回实际进入队列的任务数。
     */
    suspend fun enqueue(
        historyId: Long,
        response: ParseResponseDto,
        indices: Collection<Int>,
        preferLive: Boolean,
    ): Int {
        val now = System.currentTimeMillis()
        val prefix = response.title.take(24).ifBlank { "LinkFetch" }
        val ids = mutableListOf<Long>()
        indices.distinct().sorted().forEach { index ->
            val media = response.medias.getOrNull(index) ?: return@forEach
            val variantLive = preferLive && media.live && !media.liveUrl.isNullOrBlank()
            val task = DownloadTaskEntity(
                historyId = historyId,
                mediaIndex = index,
                url = media.url,
                liveUrl = media.liveUrl,
                isVideo = media.isVideo,
                live = media.live,
                preferLive = variantLive,
                prefix = prefix,
                createdAt = now,
                updatedAt = now,
            )
            val id = dao.insert(task)
            if (id > 0) {
                ids += id
                return@forEach
            }
            val existing = dao.find(historyId, index, variantLive) ?: return@forEach
            when (existing.status) {
                DownloadStatus.FAILED -> {
                    dao.requeue(existing.id, DownloadStatus.QUEUED, now)
                    ids += existing.id
                }
                // 已有排队记录但 Work 请求可能已丢失：补一次入队，Worker 会跳过已完成项
                DownloadStatus.QUEUED -> ids += existing.id
                else -> Unit
            }
        }
        if (ids.isNotEmpty()) enqueueWork(ids)
        return ids.size
    }

    /** 重新入队该作品下所有失败任务。 */
    suspend fun retryFailed(historyId: Long): Int {
        val failed = dao.byStatus(historyId, DownloadStatus.FAILED)
        if (failed.isEmpty()) return 0
        val now = System.currentTimeMillis()
        failed.forEach { dao.requeue(it.id, DownloadStatus.QUEUED, now) }
        enqueueWork(failed.map { it.id })
        return failed.size
    }

    /**
     * 启动时调用：把被强制停止留下的 running 复原为 queued，并为所有 queued 任务补一次请求。
     * WorkManager 自己会恢复未取消的请求；这里兜底的是「任务已入库但请求没来得及入队」的窗口。
     */
    suspend fun resumePending() {
        val now = System.currentTimeMillis()
        dao.resetRunning(DownloadStatus.RUNNING, DownloadStatus.QUEUED, now)
        val queued = dao.idsByStatus(DownloadStatus.QUEUED)
        if (queued.isNotEmpty()) enqueueWork(queued)
    }

    private fun enqueueWork(ids: List<Long>) {
        ids.chunked(MAX_IDS_PER_REQUEST).forEach { chunk ->
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadWorker.KEY_TASK_IDS to chunk.toLongArray()))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }

    private companion object {
        const val WORK_NAME = "linkfetch_downloads"

        /** WorkManager 的输入 Data 有 ~10KB 上限，按批切分留足余量。 */
        const val MAX_IDS_PER_REQUEST = 100
    }
}
