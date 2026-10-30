package com.linkfetch.app.ui.home

import com.linkfetch.app.data.db.DownloadTaskDao
import com.linkfetch.app.data.db.DownloadTaskEntity
import com.linkfetch.app.data.db.HistoryDao
import com.linkfetch.app.data.db.HistoryEntity
import com.linkfetch.app.data.db.HistoryRepository
import com.linkfetch.app.data.model.AppSettings
import com.linkfetch.app.data.parser.LocalParseException
import com.linkfetch.app.data.parser.ParseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(
        parseService = ParseService(
            localParse = { throw LocalParseException("parse_failed", "测试不发起解析") },
            serverParse = { throw LocalParseException("parse_failed", "测试不发起解析") },
            settingsProvider = { AppSettings() },
        ),
        historyDao = FakeHistoryDao(),
        historyRepository = HistoryRepository(
            historyDao = FakeHistoryDao(),
            downloadTaskDao = FakeDownloadTaskDao(),
            json = Json { ignoreUnknownKeys = true },
        ),
        json = Json { ignoreUnknownKeys = true },
    )

    @Test
    fun clipboardDetectionFillsEmptyInput() {
        val vm = createViewModel()

        vm.onClipboardText("看这个 https://xhslink.com/a/AbC 打开小红书")

        assertEquals("https://xhslink.com/a/AbC", vm.input)
        assertEquals("https://xhslink.com/a/AbC", vm.clipboardUrl)
    }

    @Test
    fun clipboardDetectionDoesNotOverwriteUserInput() {
        val vm = createViewModel()
        vm.onInputChange("https://m.weibo.cn/status/123")

        vm.onClipboardText("https://xhslink.com/a/AbC")

        // 用户正在编辑的内容不被覆盖，卡片仍然提示
        assertEquals("https://m.weibo.cn/status/123", vm.input)
        assertEquals("https://xhslink.com/a/AbC", vm.clipboardUrl)
    }

    @Test
    fun dismissedLinkIsNotReoffered() {
        val vm = createViewModel()
        vm.onClipboardText("https://xhslink.com/a/AbC")
        vm.dismissClipboard()

        vm.onClipboardText("https://xhslink.com/a/AbC")

        assertNull(vm.clipboardUrl)
    }

    @Test
    fun newLinkReoffersAfterDismiss() {
        val vm = createViewModel()
        vm.onClipboardText("https://xhslink.com/a/AbC")
        vm.dismissClipboard()

        vm.onClipboardText("https://v.douyin.com/abc/")

        assertEquals("https://v.douyin.com/abc/", vm.clipboardUrl)
    }

    @Test
    fun useClipboardUrlConsumesAndKeepsHandled() {
        val vm = createViewModel()
        vm.onClipboardText("https://xhslink.com/a/AbC")

        vm.useClipboardUrl()

        assertEquals("https://xhslink.com/a/AbC", vm.input)
        assertNull(vm.clipboardUrl)
        // 同一链接再次检测时不再提示（parse 协程已入队但未推进，不会发网络请求）
        vm.onClipboardText("https://xhslink.com/a/AbC")
        assertNull(vm.clipboardUrl)
    }

    @Test
    fun clearInputClearsClipboardCard() {
        val vm = createViewModel()
        vm.onClipboardText("https://xhslink.com/a/AbC")

        vm.clearInput()

        assertEquals("", vm.input)
        assertNull(vm.clipboardUrl)
    }

    @Test
    fun multipleLinksAreNormalizedIntoLines() {
        val vm = createViewModel()
        vm.onInputChange("看 https://v.douyin.com/aaa/ 和 https://xhslink.com/a/bbb")

        vm.parse()

        assertEquals(
            "https://v.douyin.com/aaa/\nhttps://xhslink.com/a/bbb",
            vm.input,
        )
    }

    @Test
    fun unsupportedInputFailsBeforeNetwork() {
        val vm = createViewModel()
        vm.onInputChange("https://www.bilibili.com/video/BV1xx")

        vm.parse()

        assertEquals("未识别到小红书、抖音、微博或 X 的链接，请检查后重试", vm.error)
        assertNull(vm.result)
    }

    private class FakeHistoryDao : HistoryDao {
        override suspend fun insert(entity: HistoryEntity): Long = 1L
        override suspend fun update(entity: HistoryEntity) = Unit
        override fun observeAll(): Flow<List<HistoryEntity>> = flowOf(emptyList())
        override suspend fun getLatestByUrl(url: String): HistoryEntity? = null
        override suspend fun getById(id: Long): HistoryEntity? = null
        override suspend fun deleteById(id: Long) = Unit
    }

    private class FakeDownloadTaskDao : DownloadTaskDao {
        override suspend fun insert(task: DownloadTaskEntity): Long = 0L
        override fun observeForHistory(historyId: Long): Flow<List<DownloadTaskEntity>> =
            flowOf(emptyList())
        override suspend fun getForHistory(historyId: Long): List<DownloadTaskEntity> = emptyList()
        override suspend fun deleteByIds(ids: List<Long>) = Unit
        override suspend fun getById(id: Long): DownloadTaskEntity? = null
        override suspend fun find(historyId: Long, mediaIndex: Int, preferLive: Boolean): DownloadTaskEntity? = null
        override suspend fun updateUrls(id: Long, url: String, liveUrl: String?, now: Long) = Unit
        override suspend fun idsByStatus(status: String): List<Long> = emptyList()
        override suspend fun byStatus(historyId: Long, status: String): List<DownloadTaskEntity> = emptyList()
        override suspend fun updateState(
            id: Long,
            status: String,
            progress: Float,
            savedUri: String?,
            error: String?,
            bytes: Long?,
            updatedAt: Long,
        ) = Unit

        override suspend fun requeue(id: Long, queued: String, now: Long) = Unit
        override suspend fun resetRunning(running: String, queued: String, now: Long) = Unit
    }
}

