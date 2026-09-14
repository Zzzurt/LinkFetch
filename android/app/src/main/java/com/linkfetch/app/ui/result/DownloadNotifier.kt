package com.linkfetch.app.ui.result

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import com.linkfetch.app.LinkFetchApp

/**
 * 保存完成后的通知能力。
 *
 * 抽成接口的目的：
 * 1. 让 ResultViewModel 不再持有 Context（lint 会报 StaticFieldLeak，且无法脱离设备做单测）；
 * 2. 单测可注入 fake，验证「保存成功后是否发出通知」这类逻辑。
 */
interface DownloadNotifier {
    fun notifySaved(count: Int, savedUri: Uri?)
}

/** 基于系统通知栏的实现。持有 application context，不会泄漏 Activity。 */
class AndroidDownloadNotifier(private val appContext: Context) : DownloadNotifier {

    override fun notifySaved(count: Int, savedUri: Uri?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // 未授予通知权限：静默跳过，保存本身已经成功
            return
        }

        var contentIntent: PendingIntent? = null
        if (savedUri != null) {
            contentIntent = PendingIntentCompat.getActivity(
                appContext,
                0,
                viewIntent(savedUri),
                // FLAG_IMMUTABLE 由 PendingIntentCompat 依据 isMutable 参数补上，不应在此重复传入
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )
        }

        val builder = NotificationCompat.Builder(appContext, LinkFetchApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("保存完成")
            .setContentText("已保存 $count 个文件到相册")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        savedUri?.let { uri ->
            builder.addAction(
                0,
                "查看",
                PendingIntentCompat.getActivity(
                    appContext,
                    1,
                    viewIntent(uri),
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    false,
                ),
            )
        }

        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun viewIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        data = uri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private companion object {
        const val NOTIFICATION_ID = 100
    }
}
