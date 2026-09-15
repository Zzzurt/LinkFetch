package com.linkfetch.app.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.ui.components.ErrorCard
import com.linkfetch.app.ui.components.GroupCard
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.PlatformBadge
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.ShimmerBox
import com.linkfetch.app.ui.components.VerticalSpace
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.SuccessGreen
import com.linkfetch.app.ui.theme.WarningAmber
import com.linkfetch.app.ui.theme.platformAccent
import com.linkfetch.app.util.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
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
    val snackbarHostState = remember { SnackbarHostState() }

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
        if (viewModel.clipboardUrl == null) return@LaunchedEffect
        delay(10_000)
        viewModel.dismissClipboard()
    }

    // 用 Box 承载 Snackbar 浮层，消息反馈与结果页/设置页统一走 Snackbar；
    // 整页包 ScreenFadeIn：进入时淡入，去掉页面硬切感
    ScreenFadeIn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        ) {
            // 品牌头部：品牌蓝渐变打底 + 四角平台色光晕（C3+）。
            // 蓝底压住亮度，白字对比度不依赖整卡深色遮罩；光晕浓度浅色 0.72/0.62/0.55/0.72，
            // 深色整体降约 30%（X 黑换中灰 #6B7280 后保持 0.70 才可见）。
            // 光晕用 drawBehind 画在背景层：Box 会测量子节点（含 align 定位的）撑高卡片，
            // 之前的 240dp 光晕子节点把 Hero 撑到 240dp 高，文字只占上部 1/4，下方大片留白。
            val heroDark = isSystemInDarkTheme()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, Radii.card)
                    .clip(Radii.card)
                    .background(
                        Brush.linearGradient(
                            if (heroDark) {
                                listOf(Color(0xFF1E3A8A), Color(0xFF1D4ED8))
                            } else {
                                listOf(Color(0xFF2563EB), Color(0xFF3B82F6))
                            },
                        ),
                    )
                    .drawBehind {
                        // 四角光晕：blob 圆心落在卡片角上，向卡片内扩散后自然渐隐
                        val haloRadius = 120.dp.toPx()
                        listOf(
                            Offset(0f, 0f) to (Platform.XHS to if (heroDark) 0.52f else 0.72f),
                            Offset(size.width, 0f) to (Platform.WEIBO to if (heroDark) 0.44f else 0.62f),
                            Offset(0f, size.height) to (Platform.DOUYIN to if (heroDark) 0.38f else 0.55f),
                            Offset(size.width, size.height) to (Platform.X to if (heroDark) 0.70f else 0.72f),
                        ).forEach { (center, spec) ->
                            val accent = platformAccent(spec.first, heroDark)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(accent.copy(alpha = spec.second), Color.Transparent),
                                    center = center,
                                    radius = haloRadius,
                                ),
                                radius = haloRadius,
                                center = center,
                            )
                        }
                    },
            ) {
                // 单行横排：mark + 标题 + 平台列表同行，Hero 高度压到横幅级别
                // （原先 mark 44dp + 两行文字竖排，整体近 80dp，占屏偏高）
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 品牌标：白底 + 蓝链图标。蓝渐变方块叠在蓝底 Hero 上会隐形，故改为白底。
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(Radii.card)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = "链取",
                        // 与历史/设置页的页标题同为 titleLarge，避免切页时标题大小跳动
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        // 副标题承载平台列表（产品价值句由引导条「复制链接→解析→保存」承担）
                        text = "小红书 · 抖音 · 微博 · X",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 区块间距用 24dp、组内用 8/12dp：间距有了对比，界面才有"重点"，
            // 否则整页都是均匀的 16dp，看起来就是一片平铺。
            VerticalSpace(24)

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
                            text = "复制平台链接 → 打开即解析 → 一键保存到相册",
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
                val isDark = isSystemInDarkTheme()
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable(onClick = viewModel::useClipboardUrl)
                            .padding(start = Spacing.sm, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (platform != null) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .clip(Radii.pill)
                                    .background(platformAccent(platform, isDark)),
                            )
                            Spacer(Modifier.width(Spacing.sm))
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
                        TextButton(onClick = viewModel::useClipboardUrl) {
                            Text("去解析")
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = { if (viewModel.input.isNotBlank()) viewModel.parse() },
                    ),
                    trailingIcon = {
                        if (viewModel.input.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearInput) {
                                Icon(Icons.Filled.Close, contentDescription = "清除")
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    val text = clipboard?.primaryClip
                                        ?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)
                                        ?.coerceToText(context)
                                        ?.toString()
                                    if (!text.isNullOrBlank()) viewModel.onInputChange(text.trim())
                                },
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = "从剪贴板粘贴",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
                VerticalSpace(12)
                LoadingButton(
                    // 原名「解析并保存」名实不符：此按钮只解析并写入一条历史记录，
                    // 媒体写入相册发生在结果页（「全部保存」）。统一术语：解析 / 保存 / 记录。
                    text = if (viewModel.parsing) "解析中…" else "开始解析",
                    loading = viewModel.parsing,
                    onClick = viewModel::parse,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.input.isNotBlank(),
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
                            scope.launch {
                                snackbarHostState.showSnackbar("原始响应已复制，请发给开发者排查")
                            }
                        },
                    ) {
                        Text("复制原始响应")
                    }
                }
            }
    
            // 操作区 → 状态/说明区：同样 24dp，和上一组的区块间距保持一致
            VerticalSpace(24)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val direct = settings.parseMode == "direct"
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        // 用语义 token 而不是内联色值：值相同，但改主题时不会再漏掉这里
                        .background(if (direct) SuccessGreen else WarningAmber),
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
            // 不再单列「支持平台」小标题：一行徽标 + 平台名本身已经说明含义，
            // 省掉一档标题可以缩短首屏下方的"信息尾巴"
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                // 本版 foundation 的 FlowRow 没有 verticalArrangement 参数，
                // 换行后的行间距由子项自身的纵向 padding 提供（否则两行会贴死）
            ) {
                Platform.values().forEach { platform ->
                    Row(
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
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
