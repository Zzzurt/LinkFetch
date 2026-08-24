package com.linkfetch.app.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.ui.components.ErrorCard
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.PlatformBadge
import com.linkfetch.app.ui.components.VerticalSpace
import com.linkfetch.app.util.Platform
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenResult: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    apiClient = container.apiClient,
                    localParseClient = container.localParseClient,
                    parseModeProvider = { container.settingsRepository.settings.value.parseMode },
                    historyDao = container.historyDao,
                    json = container.json,
                )
            }
        },
    )
    val context = LocalContext.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val onboardingDone by container.settingsRepository.onboardingDone
        .collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    // 进入首页时检测一次剪贴板（不做持续轮询，省电且不打扰输入）
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        viewModel.onClipboardText(text)
    }

    LaunchedEffect(viewModel.result) {
        if (viewModel.result != null) {
            onOpenResult()
            viewModel.consumeResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "链取",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "粘贴小红书 / 抖音 / 微博 / X 链接，一键提取无水印图片和视频",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VerticalSpace(20)

        if (!onboardingDone) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "三步开始使用",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "1. 在平台 App 里复制链接\n2. 打开链取，自动识别剪贴板\n3. 点击「解析并下载」保存到相册",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { scope.launch { container.settingsRepository.markOnboardingDone() } },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("知道了")
                    }
                }
            }
            VerticalSpace(12)
        }

        viewModel.clipboardUrl?.let { url ->
            Card(
                onClick = viewModel::useClipboardUrl,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("检测到平台链接，点击解析", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = url.take(46) + if (url.length > 46) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = viewModel::dismissClipboard) {
                        Icon(Icons.Filled.Close, contentDescription = "忽略")
                    }
                }
            }
            VerticalSpace(12)
        }

        OutlinedTextField(
            value = viewModel.input,
            onValueChange = viewModel::onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("链接") },
            placeholder = { Text("粘贴链接或整段分享文案") },
            minLines = 2,
            maxLines = 4,
            trailingIcon = {
                if (viewModel.input.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearInput) {
                        Icon(Icons.Filled.Close, contentDescription = "清除")
                    }
                }
            },
        )
        VerticalSpace(12)

        LoadingButton(
            text = if (viewModel.parsing) "解析中…" else "解析并下载",
            loading = viewModel.parsing,
            onClick = viewModel::parse,
            modifier = Modifier.fillMaxWidth(),
        )

        viewModel.error?.let {
            VerticalSpace(12)
            ErrorCard(it)
            viewModel.diagnosticBody?.let { body ->
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("X 原始响应", body))
                        Toast.makeText(context, "原始响应已复制，请发给开发者排查", Toast.LENGTH_LONG).show()
                    },
                ) {
                    Text("???????????")
                }
            }
        }

        VerticalSpace(16)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val direct = settings.parseMode == "direct"
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (direct) Color(0xFF22C55E) else Color(0xFFF59E0B)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (direct) {
                    "解析方式：App 直连（无需服务器）"
                } else {
                    "解析服务：${settings.baseUrl}"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            TextButton(onClick = onOpenSettings) {
                Text("修改")
            }
        }

        VerticalSpace(8)
        Text(
            text = "支持平台",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VerticalSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Platform.values().forEach { platform ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformBadge(platform, size = 24)
                    Spacer(Modifier.width(6.dp))
                    Text(platform.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        VerticalSpace(8)
        Text(
            text = "打开 App 时自动识别剪贴板中的链接，点击即可解析。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

