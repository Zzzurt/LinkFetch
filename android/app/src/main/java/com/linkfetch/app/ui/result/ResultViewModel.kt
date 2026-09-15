package com.linkfetch.app.ui.result

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.ParseResultStore
import com.linkfetch.app.data.download.DownloadException
import com.linkfetch.app.data.model.ParseResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 单个媒体的下载状态。 */
sealed interface ItemState {
    object Idle : ItemState
    data class Downloading(val progress: Float) : ItemState
    object Done : ItemState
    data class Failed(val message: String) : ItemState
}

class ResultViewModel(
    private val container: AppContainer,
    /** 通知能力通过接口注入：ViewModel 不再持有 Context，单测可替换为 fake。 */
    private val notifier: DownloadNotifier,
) : ViewModel() {

    val result: ParseResponseDto? get() = ParseResultStore.result
    val originalUrl: String get() = ParseResultStore.originalUrl

    /**
     * 用 StateFlow 而非 mutableStateOf：下载进度回调运行在 Dispatchers.IO 线程，
     * StateFlow.update 是原子的，可避免「读—改—写」互相覆盖导致状态丢失。
     */
    private val _itemStates = MutableStateFlow<Map<Int, ItemState>>(emptyMap())
    val itemStates: StateFlow<Map<Int, ItemState>> = _itemStates.asStateFlow()

    var downloading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastSavedUri by mutableStateOf<Uri?>(null)
        private set

    /** 串行化下载：避免用户快速连点造成多个任务并发写同一份状态。 */
    private val downloadMutex = Mutex()

    private var cachedHistoryId: Long? = null
    private var historyIdResolved = false

    /** 首次使用时再解析历史记录 ID，避免 init 中的异步赋值与下载动作竞态。 */
    private suspend fun historyId(): Long? {
        if (!historyIdResolved) {
            cachedHistoryId = if (originalUrl.isNotBlank()) {
                container.historyDao.getLatestByUrl(originalUrl)?.id
            } else {
                null
            }
            historyIdResolved = true
        }
        return cachedHistoryId
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
                historyId()?.let { id -> container.historyDao.updateDownloadedCount(id, success) }
                notifier.notifySaved(success, lastSavedUri)
            } else if (failed > 0) {
                error = "保存失败，可点击失败项重试"
            }
        }
    }

    fun retryFailed() {
        val indices = _itemStates.value.filterValues { it is ItemState.Failed }.keys
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
                historyId()?.let { id ->
                    val current = container.historyDao.getLatestByUrl(originalUrl)?.downloadedCount ?: 0
                    container.historyDao.updateDownloadedCount(id, current + success)
                }
                notifier.notifySaved(success, lastSavedUri)
            }
        }
    }

    /** 兼容入口：默认按 Live 格式下载（无 Live 数据时下载静态图）。 */
    fun downloadOne(index: Int) {
        if (downloading) return
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
        if (result?.medias?.getOrNull(index) == null || downloading) return
        launchDownloadOne(index, preferLive = false)
    }

    /** 用户在 API 28 及以下拒绝了存储权限：给出明确原因，而不是让下载静默失败。 */
    fun onStoragePermissionDenied() {
        error = "未获得存储权限，无法保存到相册。可在系统设置中为本应用开启存储权限后重试。"
    }

    private fun launchDownloadOne(index: Int, preferLive: Boolean) {
        viewModelScope.launch {
            downloading = true
            error = null
            if (downloadOneInternal(index, preferLive)) {
                message = "已保存到相册"
                historyId()?.let { id ->
                    val current = container.historyDao.getLatestByUrl(originalUrl)?.downloadedCount ?: 0
                    container.historyDao.updateDownloadedCount(id, current + 1)
                }
                notifier.notifySaved(1, lastSavedUri)
            } else {
                // 失败原因必须直接说出来。此前这里只设 error（显示在网格**尾部**的 ErrorCard），
                // 用户点了视频/图片上的保存按钮、看到红色 × 之后，要一路滚到底才知道为什么失败
                // —— 大多数情况下根本不会去滚。改成同时走 message（Snackbar），就地给出原因。
                val reason = (_itemStates.value[index] as? ItemState.Failed)?.message
                error = "保存失败，可重试"
                message = "保存失败：${reason ?: "未知原因"}"
            }
            downloading = false
        }
    }

    /** 下载单个媒体，成功返回 true；同时维护 itemStates 与 lastSavedUri。 */
    private suspend fun downloadOneInternal(index: Int, preferLive: Boolean): Boolean =
        downloadMutex.withLock {
            val item = result?.medias?.getOrNull(index) ?: return@withLock false
            updateState(index, ItemState.Downloading(0f))
            val useLive = preferLive && item.live && !item.liveUrl.isNullOrBlank()
            try {
                val res = if (useLive) {
                    container.mediaDownloader.downloadLive(item, prefix()) { progress ->
                        updateState(index, ItemState.Downloading(progress))
                    }
                } else {
                    container.mediaDownloader.download(item, prefix()) { progress ->
                        updateState(index, ItemState.Downloading(progress))
                    }
                }
                updateState(index, ItemState.Done)
                lastSavedUri = Uri.parse(res.uri)
                true
            } catch (e: DownloadException) {
                updateState(index, ItemState.Failed(e.message ?: "保存失败"))
                false
            } catch (e: Exception) {
                // 兜底：MediaDownloader 已收敛大部分异常，这里再保证 UI 不会因单条失败而崩溃
                updateState(index, ItemState.Failed(e.message ?: "保存失败"))
                false
            }
        }

    private fun updateState(index: Int, state: ItemState) {
        _itemStates.update { it + (index to state) }
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
}
