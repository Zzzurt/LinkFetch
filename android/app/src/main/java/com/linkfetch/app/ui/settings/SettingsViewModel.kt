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
        }
    }

    fun onParseModeChange(value: String) {
        parseMode = value
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
            repository.save(
                AppSettings(
                    parseMode = parseMode,
                    baseUrl = baseUrl.trim().ifBlank { "http://10.0.2.2:8000" },
                    apiToken = apiToken,
                    xhsCookie = xhsCookie,
                    douyinCookie = douyinCookie,
                    weiboCookie = weiboCookie,
                    downloadQuality = quality,
                ),
            )
            saving = false
            message = "设置已保存"
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
}

