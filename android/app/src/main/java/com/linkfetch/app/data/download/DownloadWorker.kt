package com.linkfetch.app.data.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.linkfetch.app.LinkFetchApp
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.db.DownloadStatus
import com.linkfetch.app.data.db.DownloadTaskEntity
import com.linkfetch.app.data.model.MediaItemDto
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 串行消费一批保存任务。
 *
 * 用前台服务跑：锁屏或切到其他 App 时仍能继续下载；进度写回 Room，结果页回来即可复原。
 * 单条失败不终止整批，失败原因同样落库，用户可以只重试失败项。
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ids = inputData.getLongArray(KEY_TASK_IDS)?.toList().orEmpty()
        if (ids.isEmpty()) return Result.success()

        val container = (applicationContext as LinkFetchApp).container
        val dao = container.downloadTaskDao
        val downloader = container.mediaDownloader
        val notifier = AndroidDownloadNotifier(applicationContext)

        setForeground(createForegroundInfo("准备保存 ${ids.size} 项…"))

        var position = 0
        ids.forEach { id ->
            position++
            val task = dao.getById(id) ?: return@forEach
            if (task.status == DownloadStatus.DONE) return@forEach

            setForeground(createForegroundInfo("正在保存 $position / ${ids.size}"))
            val now = System.currentTimeMillis()
            dao.updateState(
                id = id,
                status = DownloadStatus.RUNNING,
                progress = 0f,
                savedUri = null,
                error = null,
                bytes = null,
                updatedAt = now,
            )

            coroutineScope {
                val progress = AtomicInteger(0)
                val reporter = launch {
                    while (isActive) {
                        delay(PROGRESS_INTERVAL_MS)
                        dao.updateState(
                            id = id,
                            status = DownloadStatus.RUNNING,
                            progress = progress.get() / 100f,
                            savedUri = null,
                            error = null,
                            bytes = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                }

                try {
                    val result = downloadWithExpiryRetry(task, downloader, container) { p ->
                        progress.set((p * 100).toInt())
                    }
                    // 先停掉进度上报再写终态，否则最后一次 RUNNING 写入可能晚于 DONE，
                    // 把已保存的记录改回「下载中」
                    reporter.cancelAndJoin()
                    dao.updateState(
                        id = id,
                        status = DownloadStatus.DONE,
                        progress = 1f,
                        savedUri = result.uri,
                        error = null,
                        bytes = result.bytes,
                        updatedAt = System.currentTimeMillis(),
                    )
                } catch (e: CancellationException) {
                    // 取消（停机 / 系统回收）不写成失败：启动时由 DownloadQueue.resumePending 复原
                    reporter.cancel()
                    throw e
                } catch (e: Exception) {
                    reporter.cancelAndJoin()
                    dao.updateState(
                        id = id,
                        status = DownloadStatus.FAILED,
                        progress = progress.get() / 100f,
                        savedUri = null,
                        error = e.message ?: "保存失败",
                        bytes = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                } finally {
                    reporter.cancel()
                }
            }
        }

        val done = ids.mapNotNull { dao.getById(it) }
            .filter { it.status == DownloadStatus.DONE }
        if (done.isNotEmpty()) {
            val latestUri = done.maxByOrNull { it.updatedAt }?.savedUri?.let(Uri::parse)
            notifier.notifySaved(done.size, latestUri)
        }
        return Result.success()
    }

    /**
     * 403 通常意味着直链的时效签名过期。此时重新解析一次，换上新直链后再试一次；
     * 只重试一次，且备用通道/重新解析失败时保留原始 403 错误，避免把用户困在重试循环里。
     */
    private suspend fun downloadWithExpiryRetry(
        task: DownloadTaskEntity,
        downloader: MediaDownloader,
        container: AppContainer,
        onProgress: (Float) -> Unit,
    ): DownloadResult {
        return try {
            downloadOnce(task, downloader, onProgress)
        } catch (e: DownloadException) {
            if (!e.linkExpired) throw e
            val history = container.historyDao.getById(task.historyId) ?: throw e
            if (history.originalUrl.isBlank()) throw e
            val outcome = try {
                container.parseService.parse(history.originalUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw e
            }
            val fresh = outcome.response.medias.getOrNull(task.mediaIndex) ?: throw e
            val unchanged = fresh.url == task.url &&
                (!task.preferLive || fresh.liveUrl == task.liveUrl)
            if (unchanged) throw e
            container.historyRepository.refresh(history, outcome.response)
            val updated = container.downloadTaskDao.getById(task.id) ?: throw e
            onProgress(0f)
            downloadOnce(updated, downloader, onProgress)
        }
    }

    private suspend fun downloadOnce(
        task: DownloadTaskEntity,
        downloader: MediaDownloader,
        onProgress: (Float) -> Unit,
    ): DownloadResult {
        val item = task.toMediaItem()
        return if (task.preferLive && task.live && !task.liveUrl.isNullOrBlank()) {
            downloader.downloadLive(item, task.prefix, onProgress)
        } else {
            downloader.download(item, task.prefix, onProgress)
        }
    }

    private fun DownloadTaskEntity.toMediaItem(): MediaItemDto = MediaItemDto(
        kind = if (isVideo) "video" else "image",
        url = url,
        live = live,
        liveUrl = liveUrl,
    )

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntentCompat.getActivity(
                applicationContext,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )
        }
        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            LinkFetchApp.CHANNEL_DOWNLOAD_PROGRESS,
        )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在保存")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_TASK_IDS = "task_ids"

        private const val NOTIFICATION_ID = 200
        private const val PROGRESS_INTERVAL_MS = 250L
    }
}
