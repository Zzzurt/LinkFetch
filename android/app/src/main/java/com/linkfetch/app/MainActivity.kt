package com.linkfetch.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkfetch.app.ui.navigation.AppNavHost
import com.linkfetch.app.ui.theme.LinkFetchTheme
import com.linkfetch.app.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
    private val sharedText = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        (application as LinkFetchApp).container.notificationPermission.updateGranted(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 只在全新启动时消费一次分享 / 深链；旋转或进程重建时系统会重放同一个 Intent，
        // 再次解析会把用户从结果页拽回首页并重复写历史
        if (savedInstanceState == null) handleShareIntent(intent)

        val container = (application as LinkFetchApp).container
        container.notificationPermission.updateGranted(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
        container.notificationPermission.attach {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            LinkFetchTheme(darkTheme = resolveDarkTheme(settings.themeMode)) {
                AppNavHost(
                    container = container,
                    sharedText = sharedText.value,
                    onSharedTextConsumed = { sharedText.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        (application as LinkFetchApp).container.notificationPermission.detach()
        super.onDestroy()
    }

    private fun handleShareIntent(intent: Intent?) {
        val action = intent?.action
        val text = when (action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!text.isNullOrBlank()) {
            sharedText.value = text.trim()
        } else if (action == Intent.ACTION_SEND) {
            // 图片卡片分享没有附带文字，提示用户改分享文案，而不是停在首页无反馈
            Toast.makeText(this, "没有读取到链接，请分享平台文案而不是纯图片", Toast.LENGTH_SHORT).show()
        }
    }
}
