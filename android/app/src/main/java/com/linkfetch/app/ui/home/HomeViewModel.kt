package com.linkfetch.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkfetch.app.data.ParseResultStore
import com.linkfetch.app.data.api.ApiException
import com.linkfetch.app.data.db.HistoryDao
import com.linkfetch.app.data.db.HistoryEntity
import com.linkfetch.app.data.db.HistoryRepository
import com.linkfetch.app.data.download.DownloadQueue
import com.linkfetch.app.data.model.ParseResponseDto
import com.linkfetch.app.data.parser.LocalParseException
import com.linkfetch.app.data.parser.ParseService
import com.linkfetch.app.util.NotificationPermissionGate
import com.linkfetch.app.util.Platform
import com.linkfetch.app.util.UrlExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class HomeViewModel(
    private val parseService: ParseService,
    private val historyDao: HistoryDao,
    private val historyRepository: HistoryRepository,
    private val json: Json,
    private val downloadQueue: DownloadQueue? = null,
    private val autoSaveProvider: () -> Boolean = { false },
    private val notificationPermission: NotificationPermissionGate? = null,
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

    /** 批量解析进度（已处理数 / 总数）；单条解析时为 null。 */
    var batchProgress by mutableStateOf<Pair<Int, Int>?>(null)
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

    /** 来自系统分享 / 深链的文本：直接填入并解析，用户从其他 App 过来时不需要再点一次。 */
    fun onSharedText(text: String) {
        val url = UrlExtractor.extractFirstPlatformUrl(text) ?: text.trim()
        if (url.isBlank()) return
        input = url
        clearError()
        handledClipboardUrl = url
        clipboardUrl = null
        parse()
    }

    fun parse() {
        val raw = input.trim()
        if (raw.isEmpty()) {
            fail("请先粘贴或输入平台链接")
            return
        }
        // 支持整段粘贴分享文案，也支持一次粘贴多条链接
        val urls = UrlExtractor.extractAllPlatformUrls(raw).ifEmpty {
            listOf(raw).filter { Platform.fromUrl(it) != null }
        }
        if (urls.isEmpty()) {
            fail("未识别到小红书、抖音、微博或 X 的链接，请检查后重试")
            return
        }
        // 输入框回填成规范化后的链接：单条时与旧行为一致，多条时让用户看清接下来会解析哪些
        input = urls.joinToString("\n")
        if (parsing) return
        if (urls.size > 1) parseBatch(urls) else parseSingle(urls.first())
    }

    private fun parseSingle(url: String) {
        viewModelScope.launch {
            parsing = true
            clearError()
            ParseResultStore.notice = null
            try {
                val outcome = parseService.parse(url)
                val response = outcome.response
                val historyId = store(url, response)
                ParseResultStore.originalUrl = url
                ParseResultStore.result = response
                ParseResultStore.historyId = historyId
                ParseResultStore.notice = if (outcome.viaServerFallback) {
                    "直连失败，已改用自建服务器解析"
                } else {
                    null
                }
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

    /**
     * 多条链接依次解析：串行而不是并发，避免同一平台的风控在瞬间收到一串请求。
     * 成功后照常入库、自动保存；界面打开第一条的结果，其余可在历史里查看。
     */
    private fun parseBatch(urls: List<String>) {
        viewModelScope.launch {
            parsing = true
            clearError()
            ParseResultStore.notice = null
            var success = 0
            var failed = 0
            var fallbackUsed = false
            var first: Triple<String, ParseResponseDto, Long>? = null
            var lastError: Throwable? = null
            try {
                urls.forEachIndexed { index, url ->
                    batchProgress = (index + 1) to urls.size
                    try {
                        val outcome = parseService.parse(url)
                        val historyId = store(url, outcome.response)
                        success++
                        if (outcome.viaServerFallback) fallbackUsed = true
                        if (first == null) first = Triple(url, outcome.response, historyId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        lastError = e
                    }
                }
            } finally {
                batchProgress = null
                parsing = false
            }
            val head = first
            if (head != null) {
                ParseResultStore.originalUrl = head.first
                ParseResultStore.result = head.second
                ParseResultStore.historyId = head.third
                ParseResultStore.notice = buildString {
                    append(
                        if (failed == 0) {
                            "本批共取回 $success 条，其余可在历史中查看"
                        } else {
                            "已取回 $success 条，$failed 条失败"
                        },
                    )
                    if (fallbackUsed) append("；部分内容经服务器备用通道解析")
                }
                result = head.second
                return@launch
            }
            when (val error = lastError) {
                is LocalParseException -> fail(error.message, error.code, error.rawBody)
                is ApiException -> fail(error.message, error.code)
                else -> fail("解析失败：${error?.message ?: "网络错误"}")
            }
        }
    }

    /** 入库并在需要时自动排队保存，返回历史记录 id。 */
    private suspend fun store(url: String, response: ParseResponseDto): Long {
        val historyId = historyRepository.save(url, response)
        if (autoSaveProvider()) {
            notificationPermission?.ensure()
            // 权限不足等失败由任务状态呈现，不阻塞解析本身
            runCatching {
                downloadQueue?.enqueue(
                    historyId = historyId,
                    response = response,
                    indices = response.medias.indices.toList(),
                    preferLive = true,
                )
            }
        }
        return historyId
    }

    fun consumeResult() {
        result = null
    }

    /** 用一条历史记录打开结果页（与历史页同一套还原逻辑，只是入口不同） */
    fun open(entity: HistoryEntity): Boolean {
        val response = runCatching {
            json.decodeFromString(ParseResponseDto.serializer(), entity.mediaJson)
        }.getOrNull() ?: run {
            fail("这条记录的数据已损坏，请重新解析原链接")
            return false
        }
        ParseResultStore.originalUrl = entity.originalUrl
        ParseResultStore.result = response
        ParseResultStore.historyId = entity.id
        return true
    }

    private companion object {
        /** 首页「最近记录」最多展示几条 */
        const val RECENT_LIMIT = 3
    }
}
