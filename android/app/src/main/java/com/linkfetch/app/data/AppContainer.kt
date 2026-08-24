package com.linkfetch.app.data

import android.content.Context
import androidx.room.Room
import com.linkfetch.app.data.api.ApiClient
import com.linkfetch.app.data.db.AppDatabase
import com.linkfetch.app.data.db.HistoryDao
import com.linkfetch.app.data.download.MediaDownloader
import com.linkfetch.app.data.parser.LocalParseClient
import com.linkfetch.app.data.prefs.SettingsRepository
import com.linkfetch.app.util.Platform
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val json: Json = Json { ignoreUnknownKeys = true }

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "linkfetch.db",
    ).build()

    val historyDao: HistoryDao = database.historyDao()

    val apiClient: ApiClient = ApiClient(
        settingsProvider = { settingsRepository.settings.value },
    )

    val localParseClient: LocalParseClient = LocalParseClient { platform ->
        val settings = settingsRepository.settings.value
        when (platform) {
            Platform.XHS -> settings.xhsCookie
            Platform.DOUYIN -> settings.douyinCookie
            Platform.WEIBO -> settings.weiboCookie
            // X syndication 接口无需 Cookie
            Platform.X -> null
        }?.takeIf { it.isNotBlank() }
    }

    val mediaDownloader: MediaDownloader = MediaDownloader(appContext)
}
