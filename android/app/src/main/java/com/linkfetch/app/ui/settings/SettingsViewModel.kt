package com.linkfetch.app.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkfetch.app.BuildConfig
import com.linkfetch.app.data.api.ApiClient
import com.linkfetch.app.data.api.ApiException
import com.linkfetch.app.data.model.AppSettings
import com.linkfetch.app.data.prefs.SettingsRepository
import com.linkfetch.app.data.update.UpdateChecker
import com.linkfetch.app.data.update.UpdateInfo
import com.linkfetch.app.data.update.localChangelogForDisplay
import com.linkfetch.app.util.Platform
import com.linkfetch.app.util.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateDialogState(
    val title: String,
    val body: String,
    val apkUrl: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val apiClient: ApiClient,
    private val updateChecker: UpdateChecker,
    private val appContext: Context,
) : ViewModel() {

    var parseMode by mutableStateOf("direct")
        private set
    var baseUrl by mutableStateOf("")
        private set
    var apiToken by mutableStateOf("")
        private set
    var xhsCookie by mutableStateOf("")
        private set
    var douyinCookie by mutableStateOf("")
        private set
    var weiboCookie by mutableStateOf("")
        private set
    var quality by mutableStateOf("hd")
        private set
    var autoSave by mutableStateOf(false)
        private set
    var themeMode by mutableStateOf("system")
        private set

    var saving by mutableStateOf(false)
        private set
    var testing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var checkingUpdate by mutableStateOf(false)
        private set
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateDialog by mutableStateOf<UpdateDialogState?>(null)
        private set
    var cacheSize by mutableStateOf("计算中…")
        private set

    val updateStatus: String
        get() = when {
            checkingUpdate -> "检查中…"
            updateInfo == null -> "未检查"
            updateInfo?.isNewer == true -> "v${updateInfo?.latestVersion}"
            else -> "已是最新"
        }

    /**
     * 最近一次落盘的设置，用于判断「是否有未保存的修改」。
     * 用比对而不是布尔标记：用户改完又改回去时，状态会自己回到"已保存"。
     */
    private var persisted by mutableStateOf<AppSettings?>(null)

    /** 是否存在未保存的修改 */
    val dirty: Boolean
        get() = persisted?.let { buildSettings() != it } ?: false

    /** 当前后端地址是否为明文 http（用于给出安全提示） */
    val isCleartext: Boolean
        get() = baseUrl.trim().startsWith("http://", ignoreCase = true)

    init {
        viewModelScope.launch {
            val settings = repository.awaitLoaded()
            parseMode = settings.parseMode
            baseUrl = settings.baseUrl
            apiToken = settings.apiToken
            xhsCookie = settings.xhsCookie
            douyinCookie = settings.douyinCookie
            weiboCookie = settings.weiboCookie
            quality = settings.downloadQuality
            autoSave = settings.autoSave
            themeMode = settings.themeMode
            persisted = settings
        }
        refreshCacheSize()
    }

    /**
     * 解析方式即时生效。
     *
     * Switch 的心理模型是"拨了就生效"，原先只改内存状态、要等用户滚到底点「保存设置」才落盘，
     * 会出现"开关已打开、首页却仍显示直连"的错觉。这里只落盘这一个字段，
     * 基底取已保存的值，因此其他未保存的修改不会被顺带提交。
     */
    fun onParseModeChange(value: String) {
        parseMode = value
        viewModelScope.launch {
            val base = persisted ?: repository.awaitLoaded()
            val updated = base.copy(parseMode = value)
            repository.save(updated)
            persisted = updated
            message = if (value == "server") {
                "已切换到自建服务器，请配置地址后保存"
            } else {
                "已切换为 App 直连解析"
            }
        }
    }

    fun onBaseUrlChange(value: String) {
        baseUrl = value
    }

    fun onApiTokenChange(value: String) {
        apiToken = value
    }

    fun onXhsCookieChange(value: String) {
        xhsCookie = value
    }

    fun onDouyinCookieChange(value: String) {
        douyinCookie = value
    }

    fun onWeiboCookieChange(value: String) {
        weiboCookie = value
    }

    /**
     * 网页登录取回的 Cookie 立即落盘。
     *
     * 登录本身已经是一次明确的用户动作，若还要再点一次「保存」，用户很容易在回填后直接返回
     * 而丢掉凭证；这里沿用 [clearCredentials] 的做法，只把该平台字段写进「已保存的值」，
     * 其他未保存的修改保持原样（dirty 会如实反映）。
     */
    fun onCookieLogin(platform: Platform, cookie: String) {
        if (cookie.isBlank()) return
        when (platform) {
            Platform.XHS -> xhsCookie = cookie
            Platform.DOUYIN -> douyinCookie = cookie
            Platform.WEIBO -> weiboCookie = cookie
            Platform.X -> return
        }
        viewModelScope.launch {
            val base = persisted ?: repository.awaitLoaded()
            val updated = when (platform) {
                Platform.XHS -> base.copy(xhsCookie = cookie)
                Platform.DOUYIN -> base.copy(douyinCookie = cookie)
                Platform.WEIBO -> base.copy(weiboCookie = cookie)
                Platform.X -> base
            }
            repository.save(updated)
            persisted = updated
            message = "已保存${platform.label}登录凭证"
        }
    }

    /** 主题与解析方式、自动保存一致：选中即生效，并只落盘这一个字段。 */
    fun onThemeModeChange(value: String) {
        if (value == themeMode) return
        themeMode = value
        viewModelScope.launch {
            val base = persisted ?: repository.awaitLoaded()
            val updated = base.copy(themeMode = value)
            repository.save(updated)
            persisted = updated
        }
    }

    /** 与解析方式一样即时生效：开关拨动后不应再依赖底部「保存」。 */
    fun onAutoSaveChange(value: Boolean) {
        autoSave = value
        viewModelScope.launch {
            val base = persisted ?: repository.awaitLoaded()
            val updated = base.copy(autoSave = value)
            repository.save(updated)
            persisted = updated
            message = if (value) "解析完成后会自动保存全部内容" else "已关闭自动保存"
        }
    }

    fun save() {
        viewModelScope.launch {
            saving = true
            try {
                val settings = buildSettings()
                repository.save(settings)
                persisted = settings
                message = "设置已保存"
            } catch (e: Exception) {
                message = "保存失败：${e.message ?: "未知错误"}"
            } finally {
                saving = false
            }
        }
    }

    /**
     * 只清空凭证并立即落盘（避免用户点了清除却忘记保存，凭证仍留在本地）。
     *
     * 落盘基底取「已保存的值」而不是当前 UI state：否则用户改了但没点保存的后端地址、
     * 解析方式会被这个"清空"按钮顺带提交，属于意外的隐式写入。
     */
    fun clearCredentials() {
        viewModelScope.launch {
            val base = persisted ?: repository.awaitLoaded()
            val cleared = base.copy(
                apiToken = "",
                xhsCookie = "",
                douyinCookie = "",
                weiboCookie = "",
            )
            repository.save(cleared)
            persisted = cleared
            // 同步擦掉界面上的凭证输入；其它字段保持原样（可能仍是未保存状态，dirty 会如实反映）
            apiToken = ""
            xhsCookie = ""
            douyinCookie = ""
            weiboCookie = ""
            message = "已清空 API Token 与平台 Cookie"
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            testing = true
            message = null
            message = try {
                apiClient.health()
                "连接成功 ✓"
            } catch (e: ApiException) {
                "连接失败：${e.message}"
            } catch (e: Exception) {
                "连接失败：${e.message}"
            } finally {
                testing = false
            }
        }
    }

    fun consumeMessage() {
        message = null
    }

    fun checkUpdate() {
        if (checkingUpdate) return
        viewModelScope.launch {
            checkingUpdate = true
            updateChecker.check()
                .onSuccess { info ->
                    updateInfo = info
                    if (info.isNewer) {
                        updateDialog = UpdateDialogState(
                            title = "发现新版本 v${info.latestVersion}",
                            body = info.notes.ifBlank { "本次更新暂无说明。" },
                            apkUrl = info.apkUrl,
                        )
                    } else {
                        message = "已是最新版本（v${info.currentVersion}）"
                    }
                }
                .onFailure { error ->
                    message = "检查更新失败：${error.message ?: "网络错误"}"
                }
            checkingUpdate = false
        }
    }

    fun showChangelog() {
        if (checkingUpdate) return
        viewModelScope.launch {
            checkingUpdate = true
            updateChecker.check()
                .onSuccess { info ->
                    updateInfo = info
                    updateDialog = if (info.isNewer) {
                        UpdateDialogState(
                            title = "更新日志 · v${info.latestVersion}",
                            body = info.notes.ifBlank { "该版本暂无更新说明。" },
                        )
                    } else {
                        localChangelogDialog()
                    }
                }
                .onFailure {
                    // 拉不到远端时退回本机版本日志，入口不会只留一条报错
                    updateDialog = localChangelogDialog()
                    message = "无法检查更新，已显示当前版本日志"
                }
            checkingUpdate = false
        }
    }

    /** 当前安装版本的日志从打包进 assets 的 CHANGELOG.md 读取，离线也能看。 */
    private suspend fun localChangelogDialog(): UpdateDialogState {
        val version = BuildConfig.VERSION_NAME
        val body = withContext(Dispatchers.IO) {
            runCatching {
                appContext.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }.let { localChangelogForDisplay(it, version) }
        return UpdateDialogState(
            title = "更新日志 · v$version",
            body = body.ifBlank { "该版本暂无更新说明。" },
        )
    }

    fun dismissUpdateDialog() {
        updateDialog = null
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            cacheSize = withContext(Dispatchers.IO) {
                StorageUsage.formatBytes(StorageUsage.cacheBytes(appContext))
            }
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { StorageUsage.clearCache(appContext) }
            refreshCacheSize()
            message = "缓存已清理"
        }
    }

    private fun buildSettings(): AppSettings = AppSettings(
        parseMode = parseMode,
        baseUrl = baseUrl.trim().ifBlank { AppSettings.DEFAULT_BASE_URL },
        apiToken = apiToken.trim(),
        xhsCookie = xhsCookie.trim(),
        douyinCookie = douyinCookie.trim(),
        weiboCookie = weiboCookie.trim(),
        downloadQuality = quality,
        autoSave = autoSave,
        themeMode = themeMode,
    )
}
