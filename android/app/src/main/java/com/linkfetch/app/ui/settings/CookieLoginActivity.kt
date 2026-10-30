package com.linkfetch.app.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkfetch.app.LinkFetchApp
import com.linkfetch.app.ui.components.GhostPill
import com.linkfetch.app.ui.theme.LinkFetchTheme
import com.linkfetch.app.ui.theme.TextColors
import com.linkfetch.app.ui.theme.resolveDarkTheme
import com.linkfetch.app.util.Platform
import com.linkfetch.app.util.mergeCookieHeaders

/**
 * 用官方网页完成登录，再把 Cookie 交给设置页。
 *
 * 为什么不在 App 里做账号密码登录：三个平台都没有公开的登录接口，逆向登录流程既脆弱又要
 * 处理验证码与设备指纹；WebView 登录走的是平台自己的页面，平台改版时这个入口不会失效。
 *
 * Cookie 只在「完成」时读取并回传给设置页，本页不打印、不落盘、不上传。
 */
class CookieLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val platform = Platform.fromKey(intent.getStringExtra(EXTRA_PLATFORM))
        if (platform == null || platform == Platform.X) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val container = (application as LinkFetchApp).container
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            LinkFetchTheme(darkTheme = resolveDarkTheme(settings.themeMode)) {
                CookieLoginScreen(
                    platform = platform,
                    onCancel = { finish() },
                    onDone = { cookie ->
                        if (cookie.isBlank()) {
                            false
                        } else {
                            CookieManager.getInstance().flush()
                            setResult(
                                RESULT_OK,
                                Intent()
                                    .putExtra(EXTRA_COOKIE, cookie)
                                    .putExtra(EXTRA_PLATFORM, platform.key),
                            )
                            finish()
                            true
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_PLATFORM = "extra_platform"
        const val EXTRA_COOKIE = "extra_cookie"

        fun intent(context: Context, platform: Platform): Intent =
            Intent(context, CookieLoginActivity::class.java)
                .putExtra(EXTRA_PLATFORM, platform.key)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CookieLoginScreen(
    platform: Platform,
    onCancel: () -> Unit,
    onDone: (String) -> Boolean,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var missingLogin by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    // 登录页里的「打开 App」深链会把用户踢出本页，留在 WebView 内即可
                    val scheme = request?.url?.scheme ?: return true
                    return scheme != "http" && scheme != "https"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loading = newProgress < 100
                }
            }
            loadUrl(startUrl(platform))
        }
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onCancel()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("取消", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = "${platform.label}登录",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            GhostPill(
                text = "完成",
                onClick = { if (!onDone(readCookie(platform))) missingLogin = true },
            )
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }

        if (missingLogin) {
            Text(
                text = "还没有读取到登录状态：请先在页面中完成登录，再点「完成」。",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextColors.muted,
            )
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

private fun startUrl(platform: Platform): String = when (platform) {
    Platform.XHS -> "https://www.xiaohongshu.com"
    Platform.DOUYIN -> "https://www.douyin.com"
    Platform.WEIBO -> "https://m.weibo.cn"
    Platform.X -> ""
}

/** 凭证可能落在主域和子域，两个都读再按名字去重。 */
private fun cookieUrls(platform: Platform): List<String> = when (platform) {
    Platform.XHS -> listOf("https://www.xiaohongshu.com", "https://edith.xiaohongshu.com")
    Platform.DOUYIN -> listOf("https://www.douyin.com", "https://www.iesdouyin.com")
    Platform.WEIBO -> listOf("https://m.weibo.cn", "https://weibo.com")
    Platform.X -> emptyList()
}

private fun readCookie(platform: Platform): String {
    val manager = CookieManager.getInstance()
    return mergeCookieHeaders(cookieUrls(platform).map { manager.getCookie(it) })
}
