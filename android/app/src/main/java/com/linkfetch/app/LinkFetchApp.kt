package com.linkfetch.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.linkfetch.app.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LinkFetchApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createDownloadChannels()
        // 被强制停止时可能留下 running 任务或未入队的 queued 任务，启动时补一次
        appScope.launch { container.downloadQueue.resumePending() }
    }

    private fun createDownloadChannels() {
        // minSdk 26 == NotificationChannel 的引入版本，无需再做版本判断
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                // 渠道 id 保持 "downloads" 不变（改 id 会新建渠道并丢失用户已设的优先级）；
                // 名称可随时更新，系统会对已存在渠道应用新的 name。
                "保存完成",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        // 前台下载进度单独一条低优先级渠道：常驻通知不发声，也不打扰已完成通知的提醒
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOAD_PROGRESS,
                "保存进度",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_DOWNLOAD_PROGRESS = "download_progress"
    }
}

