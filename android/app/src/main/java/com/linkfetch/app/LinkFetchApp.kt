package com.linkfetch.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.linkfetch.app.data.AppContainer

class LinkFetchApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createDownloadChannel()
    }

    private fun createDownloadChannel() {
        // minSdk 26 == NotificationChannel 的引入版本，无需再做版本判断
        val channel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            // 渠道 id 保持 "downloads" 不变（改 id 会新建渠道并丢失用户已设的优先级）；
            // 名称可随时更新，系统会对已存在渠道应用新的 name。
            "保存完成",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
    }
}

