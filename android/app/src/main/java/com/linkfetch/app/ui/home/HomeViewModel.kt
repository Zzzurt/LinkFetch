package com.linkfetch.app.ui.home

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
import com.linkfetch.app.util.Platform
import com.linkfetch.app.util.UrlExtractor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class HomeViewModel(
    private val apiClient: ApiClient,
    private val localParseClient: LocalParseClient,
    private val parseModeProvider: () -> String,
    private val historyDao: HistoryDao,
    private val json: Json,
) : ViewModel() {

    var input by mutableStateOf("")
        private set
    var parsing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var diagnosticBody by mutableStateOf<String?>(null)
        private set
    var clipboardUrl by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<ParseResponseDto?>(null)
        private set

    /** 已处理过的剪贴板链接（已解析或已忽略），同一链接不再重复提示 */
    private var handledClipboardUrl: String? = null

    fun onInputChange(value: String) {
        input = value
        error = null
        diagnosticBody = null
    }

    fun clearInput() {
        input = ""
        error = null
        clipboardUrl = null
    }

    fun onClipboardText(text: String?) {
        if (text.isNullOrBlank()) return
        val url = UrlExtractor.extractFirstPlatformUrl(text) ?: return
        if (url == handledClipboardUrl) return
        if (url != clipboardUrl) {
            clipboardUrl = url
            // 仅在输入框为空时自动填充，避免覆盖用户正在编辑的内容
            if (input.isBlank()) input = url
        }
    }

    fun useClipboardUrl() {
        clipboardUrl?.let { url ->
            handledClipboardUrl = url
            clipboardUrl = null
            input = url
            parse()
        }
    }

    fun dismissClipboard() {
        handledClipboardUrl = clipboardUrl
        clipboardUrl = null
    }

    fun parse() {
        val raw = input.trim()
        if (raw.isEmpty()) {
            error = "请先粘贴或输入平台链接"
            return
        }
        // 支持整段粘贴分享文案：先尝试从中提取平台链接
        val url = UrlExtractor.extractFirstPlatformUrl(raw) ?: raw
        if (Platform.fromUrl(url) == null) {
            error = "未识别到小红书、抖音、微博或 X 的链接，请检查后重试"
            return
        }
        if (url != raw) input = url
        if (parsing) return
        viewModelScope.launch {
            parsing = true
            error = null
            diagnosticBody = null
            try {
                val response = if (parseModeProvider() == "server") {
                    apiClient.parse(url)
                } else {
                    localParseClient.parse(url)
                }
                ParseResultStore.originalUrl = url
                ParseResultStore.result = response
                saveHistory(url, response)
                result = response
            } catch (e: LocalParseException) {
                error = e.message
                diagnosticBody = e.rawBody
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = "解析失败：${e.message}"
            } finally {
                parsing = false
            }
        }
    }

    fun consumeResult() {
        result = null
    }

    private suspend fun saveHistory(url: String, response: ParseResponseDto) {
        val cover = response.medias.firstOrNull()?.let { it.cover ?: it.url }
        historyDao.insert(
            HistoryEntity(
                platform = response.platform,
                title = response.title,
                author = response.author,
                type = response.type,
                coverUrl = cover,
                originalUrl = url,
                mediaJson = json.encodeToString(ParseResponseDto.serializer(), response),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
