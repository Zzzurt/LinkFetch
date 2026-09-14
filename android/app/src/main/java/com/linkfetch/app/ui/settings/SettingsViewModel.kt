package com.linkfetch.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkfetch.app.data.api.ApiClient
import com.linkfetch.app.data.api.ApiException
import com.linkfetch.app.data.model.AppSettings
import com.linkfetch.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val apiClient: ApiClient,
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

    var saving by mutableStateOf(false)
        private set
    var testing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

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
            val settings = repository.settings.first()
            parseMode = settings.parseMode
            baseUrl = settings.baseUrl
            apiToken = settings.apiToken
            xhsCookie = settings.xhsCookie
            douyinCookie = settings.douyinCookie
            weiboCookie = settings.weiboCookie
            quality = settings.downloadQuality
            persisted = settings
        }
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
            val base = persisted ?: repository.settings.first()
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

    fun onQualityChange(value: String) {
        quality = value
    }

    fun save() {
        viewModelScope.launch {
            saving = true
            val settings = buildSettings()
            repository.save(settings)
            persisted = settings
            saving = false
            message = "设置已保存"
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
            val base = persisted ?: repository.settings.first()
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

    private fun buildSettings(): AppSettings = AppSettings(
        parseMode = parseMode,
        baseUrl = baseUrl.trim().ifBlank { "http://10.0.2.2:8000" },
        apiToken = apiToken.trim(),
        xhsCookie = xhsCookie.trim(),
        douyinCookie = douyinCookie.trim(),
        weiboCookie = weiboCookie.trim(),
        downloadQuality = quality,
    )
}
