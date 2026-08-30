package com.linkfetch.app.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import com.linkfetch.app.ui.components.BrandMark
import com.linkfetch.app.ui.components.ErrorCard
import com.linkfetch.app.ui.components.GroupCard
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.PlatformBadge
import com.linkfetch.app.ui.components.ShimmerBox
import com.linkfetch.app.ui.components.VerticalSpace
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.util.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // 剪贴板横幅 10 秒无操作自动收起
    LaunchedEffect(viewModel.clipboardUrl) {
        val url = viewModel.clipboardUrl ?: return@LaunchedEffect
        delay(10_000)
        viewModel.dismissClipboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
    ) {
        // 品牌头部
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 44.dp)
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    text = "链取",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "一键提取无水印图片和视频",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        VerticalSpace(16)

        // 新手引导：一行可关闭提示条
        AnimatedVisibility(visible = !onboardingDone) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "复制平台链接 → 打开即识别 → 一键下载",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    TextButton(onClick = { scope.launch { container.settingsRepository.markOnboardingDone() } }) {
                        Text("知道了")
                    }
                }
                VerticalSpace(12)
            }
        }

        // 剪贴板检测横幅：滑入滑出 + 10s 自动收起
        AnimatedVisibility(
            visible = viewModel.clipboardUrl != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            val url = viewModel.clipboardUrl ?: return@AnimatedVisibility
            val platform = Platform.fromUrl(url)
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (platform != null) {
                        PlatformBadge(platform, size = 28)
                        Spacer(Modifier.width(Spacing.sm))
                    } else {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("检测到平台链接，点击解析", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = url.take(40) + if (url.length > 40) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = viewModel::dismissClipboard) {
                        Icon(Icons.Filled.Close, contentDescription = "忽略")
                    }
                }
                VerticalSpace(12)
            }
        }

        // Hero 输入卡：输入 + 解析一体
        GroupCard {
            OutlinedTextField(
                value = viewModel.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
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
        }

        // 解析中：结果骨架卡
        if (viewModel.parsing) {
            VerticalSpace(12)
            ResultSkeletonCard()
        }

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
                    Text("复制原始响应")
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

/** 解析等待区的骨架卡：模拟结果页头部 + 图片网格 */
@Composable
private fun ResultSkeletonCard() {
    GroupCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(modifier = Modifier.size(32.dp), shape = CircleShape)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
                Spacer(Modifier.height(6.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ShimmerBox(modifier = Modifier.weight(1f).aspectRatio(1f), shape = MaterialTheme.shapes.medium)
            ShimmerBox(modifier = Modifier.weight(1f).aspectRatio(1f), shape = MaterialTheme.shapes.medium)
        }
    }
}
