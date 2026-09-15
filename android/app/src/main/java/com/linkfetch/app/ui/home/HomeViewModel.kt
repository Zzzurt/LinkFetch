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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /**
     * 最近一次失败的统一错误码（`unsupported_link` / `parse_failed` / `rate_limited` / `network_error`）。
     *
     * 供 UI 层生成「下一步建议」：只靠 message 做关键词匹配太脆弱，而 code 是解析层本来
     * 就有的结构化信息（见 LocalParseException / ApiException），带出来零成本。
     */
    var errorCode by mutableStateOf<String?>(null)
        private set
    var diagnosticBody by mutableStateOf<String?>(null)
        private set
    var clipboardUrl by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<ParseResponseDto?>(null)
        private set

    /**
     * 最近记录：首页只取前 [RECENT_LIMIT] 条，点击可直接回到结果页。
     *
     * 首页此前在关掉引导条之后只剩输入卡一块内容，下面整屏空着；功能上也没给"再回来一次"
     * 的理由 —— 想再取一次上一条内容得先切到历史页。这里复用历史页的同一份数据源，
     * 只在首页截取前几条，因此不需要改动 DAO 接口或测试用的 fake。
     */
    val recentItems: StateFlow<List<HistoryEntity>> = historyDao.observeAll()
        .map { it.take(RECENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已处理过的剪贴板链接（已解析或已忽略），同一链接不再重复提示 */
    private var handledClipboardUrl: String? = null

    /**
     * 统一设置失败状态。
     *
     * 合成一个出口而不是分散赋值：error / errorCode / diagnosticBody 三者必须同步更新，
     * 分三处写极易漏改（errorCode 是后加的，正是为了不让它成为又一个会被漏掉的字段）。
     */
    private fun fail(message: String?, code: String? = null, rawBody: String? = null) {
        error = message
        errorCode = code
        diagnosticBody = rawBody
    }

    private fun clearError() {
        error = null
        errorCode = null
        diagnosticBody = null
    }

    fun onInputChange(value: String) {
        input = value
        clearError()
    }

    fun clearInput() {
        input = ""
        clearError()
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
            fail("请先粘贴或输入平台链接")
            return
        }
        // 支持整段粘贴分享文案：先尝试从中提取平台链接
        val url = UrlExtractor.extractFirstPlatformUrl(raw) ?: raw
        if (Platform.fromUrl(url) == null) {
            fail("未识别到小红书、抖音、微博或 X 的链接，请检查后重试")
            return
        }
        if (url != raw) input = url
        if (parsing) return
        viewModelScope.launch {
            parsing = true
            clearError()
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
                // code 一并带出：UI 层据此给「下一步建议」，比从文案里猜关键词可靠得多
                fail(e.message, e.code, e.rawBody)
            } catch (e: ApiException) {
                fail(e.message, e.code)
            } catch (e: Exception) {
                fail("解析失败：${e.message}")
            } finally {
                parsing = false
            }
        }
    }

    fun consumeResult() {
        result = null
    }

    /** 用一条历史记录打开结果页（与历史页同一套还原逻辑，只是入口不同） */
    fun open(entity: HistoryEntity): Boolean {
        val response = runCatching {
            json.decodeFromString(ParseResponseDto.serializer(), entity.mediaJson)
        }.getOrNull() ?: return false
        ParseResultStore.originalUrl = entity.originalUrl
        ParseResultStore.result = response
        return true
    }

    private suspend fun saveHistory(url: String, response: ParseResponseDto) {
        // 封面选取必须避开「把视频地址当图片用」：
        //   1. 优先取第一张**图片**的 cover，没有 cover 就退到它自己的 url（图片 url 本身就是图）；
        //   2. 没有图片时，退到任意媒体的 cover（视频封面通常是 jpg/webp，能当缩略图）；
        //   3. 都没有才返回 null —— 此时列表走「平台色渐变底 + 徽标」占位，而不是一块空白。
        //
        // 此前是 `medias.firstOrNull()?.let { it.cover ?: it.url }`：medias 的第一项通常是**视频**，
        // 而微博视频的 cover 取自 pageInfo.page_pic（可能不存在）、X 的几个分支压根不设 cover，
        // 于是一旦 cover 为 null 就回退成**视频地址本身** —— Coil 解不了 mp4，缩略图就是空白。
        val cover = response.medias.firstOrNull { !it.isVideo }?.let { it.cover ?: it.url }
            ?: response.medias.firstNotNullOfOrNull { it.cover }
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

    private companion object {
        /** 首页「最近记录」最多展示几条 */
        const val RECENT_LIMIT = 3
    }
}
