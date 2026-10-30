package com.linkfetch.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linkfetch.app.data.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "linkfetch_settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                dataStore.data.map { prefs ->
                    AppSettings(
                        parseMode = prefs[Keys.PARSE_MODE] ?: "direct",
                        baseUrl = prefs[Keys.BASE_URL] ?: AppSettings().baseUrl,
                        apiToken = prefs[Keys.API_TOKEN] ?: "",
                        xhsCookie = prefs[Keys.XHS_COOKIE] ?: "",
                        douyinCookie = prefs[Keys.DOUYIN_COOKIE] ?: "",
                        weiboCookie = prefs[Keys.WEIBO_COOKIE] ?: "",
                        downloadQuality = prefs[Keys.DOWNLOAD_QUALITY] ?: "hd",
                        autoSave = prefs[Keys.AUTO_SAVE] ?: false,
                        themeMode = prefs[Keys.THEME_MODE] ?: "system",
                    )
                }.collect {
                    _settings.value = it
                    _loaded.value = true
                }
            } catch (_: Exception) {
                // 读取失败时放行等待者，避免设置页永远停在默认值
                _loaded.value = true
            }
        }
    }

    /**
     * 等待 DataStore 首次真正读出磁盘值。
     *
     * [settings] 的初始值是出厂默认值，DataStore 加载完成前直接读它会拿到空 Cookie/Token；
     * 设置页若据此回写，就会把用户已保存的凭证覆盖掉。
     */
    suspend fun awaitLoaded(): AppSettings {
        if (!_loaded.value) _loaded.first { it }
        return _settings.value
    }

    suspend fun save(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.PARSE_MODE] = settings.parseMode
            prefs[Keys.BASE_URL] = settings.baseUrl.trim()
            prefs[Keys.API_TOKEN] = settings.apiToken
            prefs[Keys.XHS_COOKIE] = settings.xhsCookie
            prefs[Keys.DOUYIN_COOKIE] = settings.douyinCookie
            prefs[Keys.WEIBO_COOKIE] = settings.weiboCookie
            prefs[Keys.DOWNLOAD_QUALITY] = settings.downloadQuality
            prefs[Keys.AUTO_SAVE] = settings.autoSave
            prefs[Keys.THEME_MODE] = settings.themeMode
        }
    }

    private object Keys {
        val PARSE_MODE = stringPreferencesKey("parse_mode")
        val BASE_URL = stringPreferencesKey("base_url")
        val API_TOKEN = stringPreferencesKey("api_token")
        val XHS_COOKIE = stringPreferencesKey("xhs_cookie")
        val DOUYIN_COOKIE = stringPreferencesKey("douyin_cookie")
        val WEIBO_COOKIE = stringPreferencesKey("weibo_cookie")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}

