package com.linkfetch.app.ui.home

import com.linkfetch.app.data.api.ApiClient
import com.linkfetch.app.data.db.HistoryDao
import com.linkfetch.app.data.db.HistoryEntity
import com.linkfetch.app.data.model.AppSettings
import com.linkfetch.app.data.parser.LocalParseClient
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
        apiClient = ApiClient(settingsProvider = { AppSettings() }),
        localParseClient = LocalParseClient { null },
        parseModeProvider = { "direct" },
        historyDao = FakeHistoryDao(),
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

    private class FakeHistoryDao : HistoryDao {
        override suspend fun insert(entity: HistoryEntity): Long = 1L
        override suspend fun update(entity: HistoryEntity) = Unit
        override fun observeAll(): Flow<List<HistoryEntity>> = flowOf(emptyList())
        override suspend fun getLatestByUrl(url: String): HistoryEntity? = null
        override suspend fun updateDownloadedCount(id: Long, count: Int) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun clear() = Unit
    }
}

