package com.linkfetch.app.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkfetch.app.data.ParseResultStore
import com.linkfetch.app.data.api.ApiClient
import com.linkfetch.app.data.api.ApiException
import com.linkfetch.app.data.db.HistoryDao
import com.linkfetch.app.data.db.HistoryEntity
import com.linkfetch.app.data.model.ParseResponseDto
import com.linkfetch.app.data.parser.LocalParseClient
import com.linkfetch.app.data.parser.LocalParseException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class HistoryViewModel(
    private val dao: HistoryDao,
    private val json: Json,
    private val localParseClient: LocalParseClient,
    private val apiClient: ApiClient,
    private val parseModeProvider: () -> String,
) : ViewModel() {

    val items: StateFlow<List<HistoryEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var filter by mutableStateOf("all")
        private set
    var selectedIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var confirmDeleteSelected by mutableStateOf(false)
        private set
    var reParsingId by mutableStateOf<Long?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val visibleItems: List<HistoryEntity>
        get() = items.value.filter { filter == "all" || it.platform == filter }

    val selectionMode: Boolean
        get() = selectedIds.isNotEmpty()

    fun onFilterChange(value: String) {
        filter = value
        clearSelection()
    }

    fun toggleSelect(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun longPress(id: Long) {
        if (selectedIds.isEmpty()) {
            selectedIds = setOf(id)
        } else {
            toggleSelect(id)
        }
    }

    /**
     * 进入多选模式：默认选中当前筛选下的第一条。
     * selectionMode 由"选中集非空"驱动，所以必须有一个起始选中项；
     * 这样用户点「选择」后能立刻看到底部操作条出现，而不用先猜要长按。
     */
    fun enterSelection() {
        visibleItems.firstOrNull()?.let { selectedIds = setOf(it.id) }
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    /** 全选/取消全选当前筛选可见项（供多选底部操作条使用） */
    fun selectAllOrClear() {
        val visibleIds = visibleItems.map { it.id }
        if (visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)) {
            clearSelection()
        } else {
            selectedIds = visibleIds.toSet()
        }
    }

    fun requestDeleteSelected() {
        if (selectedIds.isNotEmpty()) confirmDeleteSelected = true
    }

    fun handleDeleteSelected(confirm: Boolean) {
        confirmDeleteSelected = false
        if (confirm) {
            viewModelScope.launch {
                selectedIds.forEach { dao.deleteById(it) }
                clearSelection()
            }
        }
    }

    // 「清空历史」已从界面移除 —— 它与「选择 → 全选 → 删除」是同一条路径，
    // 保留两套会让用户在多选流程与清空按钮之间反复权衡。删除统一走多选。

    fun delete(entity: HistoryEntity) {
        viewModelScope.launch { dao.deleteById(entity.id) }
    }

    fun open(entity: HistoryEntity): Boolean {
        val response = runCatching {
            json.decodeFromString(ParseResponseDto.serializer(), entity.mediaJson)
        }.getOrNull() ?: return false
        ParseResultStore.originalUrl = entity.originalUrl
        ParseResultStore.result = response
        return true
    }

    fun reparse(entity: HistoryEntity) {
        if (reParsingId != null) return
        viewModelScope.launch {
            reParsingId = entity.id
            message = null
            try {
                val response = if (parseModeProvider() == "server") {
                    apiClient.parse(entity.originalUrl)
                } else {
                    localParseClient.parse(entity.originalUrl)
                }
                val cover = response.medias.firstOrNull()?.let { it.cover ?: it.url }
                dao.update(
                    entity.copy(
                        title = response.title,
                        author = response.author,
                        type = response.type,
                        coverUrl = cover,
                        mediaJson = json.encodeToString(ParseResponseDto.serializer(), response),
                        downloadedCount = 0,
                    ),
                )
                message = "已重新解析，直链已更新"
            } catch (e: LocalParseException) {
                message = "重新解析失败：${e.message}"
            } catch (e: ApiException) {
                message = "重新解析失败：${e.message}"
            } catch (e: Exception) {
                message = "重新解析失败：${e.message}"
            } finally {
                reParsingId = null
            }
        }
    }

    fun consumeMessage() {
        message = null
    }
}

