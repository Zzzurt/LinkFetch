package com.linkfetch.app.data

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.linkfetch.app.data.api.ApiClient
import com.linkfetch.app.data.db.AppDatabase
import com.linkfetch.app.data.db.DownloadTaskDao
import com.linkfetch.app.data.db.HistoryDao
import com.linkfetch.app.data.db.HistoryRepository
import com.linkfetch.app.data.db.MIGRATION_1_2
import com.linkfetch.app.data.db.MIGRATION_2_3
import com.linkfetch.app.data.download.DownloadQueue
import com.linkfetch.app.data.download.MediaDownloader
import com.linkfetch.app.data.parser.DouyinWebViewExtractor
import com.linkfetch.app.data.parser.LocalParseClient
import com.linkfetch.app.data.parser.ParseService
import com.linkfetch.app.data.prefs.SettingsRepository
import com.linkfetch.app.data.update.UpdateChecker
import com.linkfetch.app.util.NotificationPermissionGate
import com.linkfetch.app.util.Platform
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val json: Json = Json { ignoreUnknownKeys = true }

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "linkfetch.db",
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    val historyDao: HistoryDao = database.historyDao()

    val downloadTaskDao: DownloadTaskDao = database.downloadTaskDao()

    val historyRepository: HistoryRepository = HistoryRepository(
        historyDao = historyDao,
        downloadTaskDao = downloadTaskDao,
        json = json,
    )

    val downloadQueue: DownloadQueue = DownloadQueue(
        dao = downloadTaskDao,
        workManager = WorkManager.getInstance(appContext),
    )

    /**
     * 全局共享的 HTTP 客户端。
     *
     * OkHttp 的连接池、Dispatcher 线程池与空闲连接回收线程都挂在实例上：
     * 每个解析器各建一个实例会带来 5+ 份线程与连接池，连接也无法跨解析器复用
     * （重复 TLS 握手与 DNS 解析）。各解析器仍保留默认参数，便于单测注入 MockWebServer 客户端。
     */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 解析专用客户端：给整次调用（含重定向）设总时限。
     * 下载仍用 [okHttp]，避免大文件在慢网络下被总时限误杀；newBuilder 复用同一连接池。
     */
    private val parseHttp: OkHttpClient = okHttp.newBuilder()
        .callTimeout(100, TimeUnit.SECONDS)
        .build()

    val apiClient: ApiClient = ApiClient(
        settingsProvider = { settingsRepository.settings.value },
        client = parseHttp,
    )

    val localParseClient: LocalParseClient = LocalParseClient(
        cookieProvider = { platform ->
            val settings = settingsRepository.settings.value
            when (platform) {
                Platform.XHS -> settings.xhsCookie
                Platform.DOUYIN -> settings.douyinCookie
                Platform.WEIBO -> settings.weiboCookie
                // X syndication 接口无需 Cookie
                Platform.X -> null
            }?.takeIf { it.isNotBlank() }
        },
        client = parseHttp,
        // 抖音分享页/直连接口被风控时的最终兜底：隐形 WebView 加载桌面版详情页
        douyinWebViewFallback = { pageUrl, cookie ->
            DouyinWebViewExtractor(appContext).extractAwemeJson(pageUrl, cookie)
        },
    )

    val mediaDownloader: MediaDownloader = MediaDownloader(appContext, okHttp)

    val parseService: ParseService = ParseService(
        localParse = { url -> localParseClient.parse(url) },
        serverParse = { url -> apiClient.parse(url) },
        settingsProvider = { settingsRepository.settings.value },
    )

    val updateChecker: UpdateChecker = UpdateChecker(okHttp, json)

    val notificationPermission: NotificationPermissionGate = NotificationPermissionGate()
}
