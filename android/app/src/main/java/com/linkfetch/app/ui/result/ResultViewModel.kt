package com.linkfetch.app.ui.result

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkfetch.app.LinkFetchApp
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.ParseResultStore
import com.linkfetch.app.data.download.DownloadException
import com.linkfetch.app.data.model.ParseResponseDto
import kotlinx.coroutines.launch

/** 单个媒体的下载状态。 */
sealed interface ItemState {
    object Idle : ItemState
    data class Downloading(val progress: Float) : ItemState
    object Done : ItemState
    data class Failed(val message: String) : ItemState
}

class ResultViewModel(
    private val container: AppContainer,
    private val appContext: Context,
) : ViewModel() {

    val result: ParseResponseDto? get() = ParseResultStore.result
    val originalUrl: String get() = ParseResultStore.originalUrl

    var itemStates by mutableStateOf<Map<Int, ItemState>>(emptyMap())
        private set
    var downloading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastSavedUri by mutableStateOf<Uri?>(null)
        private set

    val failedCount: Int
        get() = itemStates.values.count { it is ItemState.Failed }

    private var historyId: Long? = null

    init {
        viewModelScope.launch {
            if (originalUrl.isNotBlank()) {
                historyId = container.historyDao.getLatestByUrl(originalUrl)?.id
            }
        }
    }

    fun downloadAll() {
        val items = result?.medias ?: return
        if (items.isEmpty() || downloading) return
        viewModelScope.launch {
            downloading = true
            error = null
            var success = 0
            var failed = 0
            items.indices.forEach { index ->
                // 默认行为：Live 图按 Live 格式保存，静态图按原图保存
                if (downloadOneInternal(index, preferLive = true)) success++ else failed++
            }
            downloading = false
            if (success > 0) {
                message = if (failed > 0) "已保存 $success 个，失败 $failed 个" else "已保存 $success 个文件到相册"
                historyId?.let { id -> container.historyDao.updateDownloadedCount(id, success) }
                notifySaved(success)
            } else if (failed > 0) {
                error = "下载失败，可点击失败项重试"
            }
        }
    }

    fun retryFailed() {
        val indices = itemStates.filterValues { it is ItemState.Failed }.keys
        if (indices.isEmpty() || downloading) return
        viewModelScope.launch {
            downloading = true
            error = null
            var success = 0
            indices.forEach { index ->
                if (downloadOneInternal(index, preferLive = true)) success++
            }
            downloading = false
            if (success > 0) {
                message = "重试成功 $success 个"
                historyId?.let { id ->
                    val current = container.historyDao.getLatestByUrl(originalUrl)?.downloadedCount ?: 0
                    container.historyDao.updateDownloadedCount(id, current + success)
                }
                notifySaved(success)
            }
        }
    }

    /** 兼容入口：默认按 Live 格式下载（无 Live 数据时下载静态图）。 */
    fun downloadOne(index: Int) {
        launchDownloadOne(index, preferLive = true)
    }

    /** 下载 Live 图的 Motion Photo 版本。 */
    fun downloadOneLive(index: Int) {
        val item = result?.medias?.getOrNull(index) ?: return
        if (downloading || !item.live) return
        launchDownloadOne(index, preferLive = true)
    }

    /** 仅下载静态原图（不包含动态效果）。 */
    fun downloadOneStatic(index: Int) {
        val item = result?.medias?.getOrNull(index) ?: return
        if (downloading) return
        launchDownloadOne(index, preferLive = false)
    }

    private fun launchDownloadOne(index: Int, preferLive: Boolean) {
        viewModelScope.launch {
            downloading = true
            error = null
            if (downloadOneInternal(index, preferLive)) {
                message = "已保存到相册"
                historyId?.let { id ->
                    val current = container.historyDao.getLatestByUrl(originalUrl)?.downloadedCount ?: 0
                    container.historyDao.updateDownloadedCount(id, current + 1)
                }
                notifySaved(1)
            } else {
                error = "下载失败，可重试"
            }
            downloading = false
        }
    }

    /** 下载单个媒体，成功返回 true；同时维护 itemStates 与 lastSavedUri。 */
    private suspend fun downloadOneInternal(index: Int, preferLive: Boolean): Boolean {
        val item = result?.medias?.getOrNull(index) ?: return false
        itemStates = itemStates + (index to ItemState.Downloading(0f))
        val useLive = preferLive && item.live && !item.liveUrl.isNullOrBlank()
        return try {
            val res = if (useLive) {
                container.mediaDownloader.downloadLive(item, prefix()) { progress ->
                    itemStates = itemStates + (index to ItemState.Downloading(progress))
                }
            } else {
                container.mediaDownloader.download(item, prefix()) { progress ->
                    itemStates = itemStates + (index to ItemState.Downloading(progress))
                }
            }
            itemStates = itemStates + (index to ItemState.Done)
            lastSavedUri = Uri.parse(res.uri)
            true
        } catch (e: DownloadException) {
            itemStates = itemStates + (index to ItemState.Failed(e.message ?: "下载失败"))
            false
        }
    }

    fun dismissMessage() {
        message = null
    }

    fun dismissError() {
        error = null
    }

    fun consumeSavedUri() {
        lastSavedUri = null
    }

    private fun prefix(): String = result?.title?.take(24)?.ifBlank { "LinkFetch" } ?: "LinkFetch"

    private fun notifySaved(count: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val savedUri = lastSavedUri
        var contentIntent: android.app.PendingIntent? = null
        if (savedUri != null) {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                data = savedUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            contentIntent = PendingIntentCompat.getActivity(
                appContext, 0, viewIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                false,
            )
        }
        val builder = NotificationCompat.Builder(appContext, LinkFetchApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText("已保存 $count 个文件到相册")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
        savedUri?.let {
            builder.addAction(
                0,
                "查看",
                PendingIntentCompat.getActivity(
                    appContext, 1, Intent(Intent.ACTION_VIEW).apply {
                        data = it
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    false,
                ),
            )
        }
        runCatching {
            NotificationManagerCompat.from(appContext).notify(100, builder.build())
        }
    }
}
