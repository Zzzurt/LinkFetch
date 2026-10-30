package com.linkfetch.app.ui.result

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.ParseResultStore
import com.linkfetch.app.data.db.DownloadStatus
import com.linkfetch.app.data.db.DownloadTaskEntity
import com.linkfetch.app.data.model.ParseResponseDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 单个媒体的下载状态。 */
sealed interface ItemState {
    object Idle : ItemState
    data class Downloading(val progress: Float) : ItemState
    object Done : ItemState
    data class Failed(val message: String) : ItemState
}

/**
 * 结果页只负责「排队」与「展示」，真正的下载在 WorkManager 里跑。
 * 所有状态来自 download_tasks 表，因此退出页面再回来也能看到进度与已保存标记。
 */
class ResultViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val result: ParseResponseDto? get() = ParseResultStore.result
    val originalUrl: String get() = ParseResultStore.originalUrl

    private val _itemStates = MutableStateFlow<Map<Int, ItemState>>(emptyMap())
    val itemStates: StateFlow<Map<Int, ItemState>> = _itemStates.asStateFlow()

    private var activeTasks by mutableIntStateOf(0)
    private var enqueuing by mutableStateOf(false)

    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastSavedUri by mutableStateOf<Uri?>(null)
        private set

    var selectedIndices by mutableStateOf<Set<Int>>(emptySet())
        private set

    val selectionMode: Boolean get() = selectedIndices.isNotEmpty()

    /** 队列里有任务，或正在把选中项写进队列。 */
    val downloading: Boolean get() = enqueuing || activeTasks > 0

    private val historyIdReady = CompletableDeferred<Long?>()

    private var seenSnapshot = false
    private var previousActive = 0
    private var previousDone = 0
    private var previousFailed = 0

    init {
        // 批量解析后带过来的一次性提示（"已取回 N 条"），只展示一次
        ParseResultStore.notice?.let {
            message = it
            ParseResultStore.notice = null
        }
        viewModelScope.launch {
            val id = ParseResultStore.historyId ?: run {
                val found = if (originalUrl.isNotBlank()) {
                    container.historyDao.getLatestByUrl(originalUrl)?.id
                } else {
                    null
                }
                ParseResultStore.historyId = found
                found
            }
            historyIdReady.complete(id)
            if (id != null) {
                container.downloadTaskDao.observeForHistory(id).collect(::applyTasks)
            }
        }
    }

    fun downloadAll() {
        enqueue(result?.medias?.indices?.toList().orEmpty(), preferLive = true)
    }

    fun retryFailed() {
        container.notificationPermission.ensure()
        viewModelScope.launch {
            val id = historyIdReady.await() ?: return@launch
            enqueuing = true
            error = null
            try {
                val count = container.downloadQueue.retryFailed(id)
                if (count > 0) message = "已重试 $count 项"
            } catch (e: Exception) {
                error = "重试失败：${e.message ?: "未知错误"}"
            } finally {
                enqueuing = false
            }
        }
    }

    /** 兼容入口：默认按 Live 格式下载（无 Live 数据时下载静态图）。 */
    fun downloadOne(index: Int) {
        enqueue(listOf(index), preferLive = true)
    }

    fun downloadOneLive(index: Int) {
        val item = result?.medias?.getOrNull(index) ?: return
        if (!item.live) return
        enqueue(listOf(index), preferLive = true)
    }

    /** 仅下载静态原图（不包含动态效果）。 */
    fun downloadOneStatic(index: Int) {
        if (result?.medias?.getOrNull(index) == null) return
        enqueue(listOf(index), preferLive = false)
    }

    fun toggleSelected(index: Int) {
        selectedIndices = if (index in selectedIndices) {
            selectedIndices - index
        } else {
            selectedIndices + index
        }
    }

    fun clearSelection() {
        selectedIndices = emptySet()
    }

    /** 从底部「选择」进入多选：默认勾选第一项尚未保存的内容。 */
    fun enterSelection() {
        val indices = result?.medias?.indices?.toList().orEmpty()
        if (indices.isEmpty()) return
        val saved = _itemStates.value.filterValues { it is ItemState.Done }.keys
        val first = indices.firstOrNull { it !in saved } ?: indices.first()
        selectedIndices = setOf(first)
    }

    fun selectAllOrClear() {
        val all = result?.medias?.indices?.toSet().orEmpty()
        selectedIndices = if (all.isNotEmpty() && selectedIndices.containsAll(all)) {
            emptySet()
        } else {
            all
        }
    }

    fun saveSelected() {
        val indices = selectedIndices.toList()
        if (indices.isEmpty()) return
        enqueue(indices, preferLive = true)
        clearSelection()
    }

    /** 用户在 API 28 及以下拒绝了存储权限：给出明确原因，而不是让下载静默失败。 */
    fun onStoragePermissionDenied() {
        error = "未获得存储权限，无法保存到相册。可在系统设置中为本应用开启存储权限后重试。"
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

    private fun enqueue(indices: Collection<Int>, preferLive: Boolean) {
        val data = result ?: return
        if (indices.isEmpty() || enqueuing) return
        // 所有保存入口（单张 / 批量 / 重试 / 自动保存）都在此汇合，首次保存前统一请求通知权限
        container.notificationPermission.ensure()
        viewModelScope.launch {
            enqueuing = true
            error = null
            try {
                val id = historyIdReady.await()
                if (id == null) {
                    error = "没有找到这条解析记录，请返回首页重新解析"
                    return@launch
                }
                val count = container.downloadQueue.enqueue(id, data, indices, preferLive)
                message = when {
                    count == 0 -> "所选内容已在队列或已保存"
                    indices.size == 1 -> "已加入保存队列"
                    else -> "已加入保存队列（$count 项）"
                }
            } catch (e: Exception) {
                error = "加入保存队列失败：${e.message ?: "未知错误"}"
            } finally {
                enqueuing = false
            }
        }
    }

    private fun applyTasks(tasks: List<DownloadTaskEntity>) {
        _itemStates.value = downloadStates(tasks, result?.medias.orEmpty())
        activeTasks = tasks.count {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING
        }
        val done = tasks.count { it.status == DownloadStatus.DONE }
        val failed = tasks.count { it.status == DownloadStatus.FAILED }

        if (seenSnapshot && previousActive > 0 && activeTasks == 0) {
            val doneDelta = done - previousDone
            val failedDelta = failed - previousFailed
            if (doneDelta > 0 || failedDelta > 0) {
                lastSavedUri = tasks
                    .filter { it.status == DownloadStatus.DONE }
                    .maxByOrNull { it.updatedAt }
                    ?.savedUri
                    ?.let(Uri::parse)
                message = when {
                    doneDelta > 0 && failedDelta > 0 ->
                        "已保存 $doneDelta 个，$failedDelta 个失败"
                    doneDelta > 0 -> "已保存 $doneDelta 个文件到相册"
                    else -> "保存失败，可点击失败项重试"
                }
            }
        }
        seenSnapshot = true
        previousActive = activeTasks
        previousDone = done
        previousFailed = failed
    }
}
