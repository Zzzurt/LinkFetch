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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.linkfetch.app.data.db.HistoryEntity
import com.linkfetch.app.ui.components.CoverPlaceholder
import com.linkfetch.app.ui.components.ErrorCard
import com.linkfetch.app.ui.components.GroupCard
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.PlatformBadge
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.SectionHeader
import com.linkfetch.app.ui.components.ShimmerBox
import com.linkfetch.app.ui.components.ShimmerImage
import com.linkfetch.app.ui.components.VerticalSpace
import com.linkfetch.app.ui.components.errorAdvice
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.TextColors
import com.linkfetch.app.ui.theme.WarningAmber
import com.linkfetch.app.util.Platform
import com.linkfetch.app.util.formatHistoryTime
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
    val recent by viewModel.recentItems.collectAsStateWithLifecycle()
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
            // 品牌条（v1.8）：从「大 Hero 渐变横幅」压缩为一行的品牌锚点。
            //
            // 之前的 Hero 是一整块蓝色渐变卡（40dp+ 高），把首屏近四分之一的高度让给了
            // 装饰，而这一屏真正的动作在输入区 —— 大色块与「过路式工具」的定位不符。
            // 压缩后：logo + 名称一行收起品牌身份，右侧补上「直达设置」的入口。
            // 品牌蓝只出现在这块 logo 里（以及下方主按钮），大面积的填充色让位给中性底。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(Radii.card)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = "链取",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            VerticalSpace(12)
            // 价值主张：整屏唯一的说明性大标题，聚焦「做什么」。
            // 不再写平台名 —— 「支持哪些平台」收敛到输入区的彩点行（见下），一处说一遍。
            Text(
                text = "粘贴链接，直取原图原视频",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            // 区块间距：lead 与下方输入工作台之间用 24dp，拉开「说明」与「动作」两组内容
            VerticalSpace(24)

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
                            .clickable(onClick = viewModel::useClipboardUrl)
                            .padding(start = Spacing.sm, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (platform != null) {
                            // 原先这里还有一条 4dp 的平台色竖条。移除原因：横幅里已经用
                            // PlatformBadge 表达了平台身份，色条是同一件事的第二次表达；
                            // 而平台色的职责被限定为「标识身份」（小面积徽标，配 onPlatform()
                            // 做前景自适应），拿它当装饰条会再次破例 —— 与 Hero 四色光晕同类。
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
    
            // 输入工作台（v1.8 第二轮）：品牌蓝渐变面板。
            // 蓝色语言此前只在 Hero 横幅上，v1.8 首轮改成了中性淡层；真机反馈想保留
            // 品牌色填充 —— 蓝色只给「输入」这一个主动作，品牌色收敛到动作区：
            // - 面板：品牌蓝渐变（沿用原 Hero 的 #2563EB→#3B82F6 / 深色 #1E3A8A→#1D4ED8）；
            // - 输入框：白色浮块（深色下用 surface 深块），去掉描边只留白底；
            // - 面板内边距 16dp：内容（含输入框 label 与图标）不再贴边。
            // - 平台信息用带文字的徽标，比彩点更能表达「支持哪些平台」。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radii.card)
                    .background(
                        Brush.linearGradient(
                            if (isSystemInDarkTheme()) {
                                listOf(Color(0xFF1E3A8A), Color(0xFF1D4ED8))
                            } else {
                                listOf(Color(0xFF2563EB), Color(0xFF3B82F6))
                            },
                        ),
                    )
                    .padding(Spacing.lg),
            ) {
                Column {
                    OutlinedTextField(
                        value = viewModel.input,
                        onValueChange = viewModel::onInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        shape = Radii.field,
                        // 白色输入浮层：容器 surface（浅色白块 / 深色深块），指示线透明
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            // 聚焦态 = 品牌蓝描边（圆角与输入框自身的 Radii.field 一致），
                            // 「正在输入」的语义由描边承担；悬浮 label 统一用页面主文字色
                            // onSurface（浅色深墨/深色近白）—— 对比最强，浅色白块上绝不隐身。
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        // 悬浮 label：保留 M3 的「聚焦/有内容时上浮到框内顶部」行为，
                        // 只把字号收到的 labelMedium —— 不再用 16sp 大字把整块输入区撑高，
                        // 悬浮后视觉略轻，与下方徽标行层次拉开。
                        label = {
                            Text("链接", style = MaterialTheme.typography.labelMedium)
                        },
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 平台徽标行：平时整行同色（未识别的平台为中性灰），安静不花哨；
                        // 输入内容含有某平台链接/文案时，对应徽标点亮为平台色 —— 即时反馈。
                        val detectedPlatform = remember(viewModel.input) {
                            Platform.detectFromText(viewModel.input)
                        }
                        Platform.values().forEach { platform ->
                            PlatformBadge(
                                platform = platform,
                                size = 24,
                                active = platform == detectedPlatform,
                                modifier = Modifier.padding(end = Spacing.sm),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        LoadingButton(
                            text = if (viewModel.parsing) "解析中…" else "解析",
                            loading = viewModel.parsing,
                            onClick = viewModel::parse,
                            enabled = viewModel.input.isNotBlank(),
                            // 蓝底上的主按钮反转为「白底 + 品牌蓝文字」，与面板同色可读
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            ),
                        )
                    }
                }
            }
    
            // 解析中：结果骨架卡
            if (viewModel.parsing) {
                VerticalSpace(12)
                ResultSkeletonCard()
            }
    
            viewModel.error?.let { message ->
                VerticalSpace(12)
                ErrorCard(
                    message = message,
                    // 下一步建议按错误码裁决，避免与 message 里已有的指引重复（详见 errorAdvice）
                    suggestion = errorAdvice(viewModel.errorCode),
                )
                viewModel.diagnosticBody?.let { body ->
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            // label 不再写死 "X 原始响应"：这份诊断体对抖音等平台同样适用
                            // （它是逐级回退链的失败记录），写死平台名会误导。
                            clipboard?.setPrimaryClip(ClipData.newPlainText("解析诊断信息", body))
                            scope.launch {
                                snackbarHostState.showSnackbar("诊断信息已复制，反馈时可附上")
                            }
                        },
                    ) {
                        Text("复制诊断信息")
                    }
                }
            }
    
            // 最近记录：给首页一个「回来继续」的落点。
            // 关掉引导条之后首页原本只剩输入卡一块内容，下面整屏空着；功能上也没给二次进入
            // 的理由 —— 想再取一次上一条内容得先切到历史页。这里摆上最近 3 条（与历史页同一份
            // 数据源），点一下直接回到结果页。封面缩略图本身也是这屏唯一的"实物"，比任何
            // 装饰都更能把版面填满。
            if (recent.isNotEmpty()) {
                VerticalSpace(24)
                SectionHeader(title = "最近记录", count = recent.size)
                Spacer(Modifier.height(Spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    recent.forEach { entity ->
                        RecentRecordRow(
                            entity = entity,
                            onClick = {
                                if (viewModel.open(entity)) onOpenResult()
                            },
                        )
                    }
                }
            }

            // 页脚：把「解析方式状态行（仅 server）+ 平台徽标 + 底部说明」收进同一个语义块。
            //
            // 这三段此前各自独立平铺，间距虽然都落在体系内（区块 24 / 组内 8），但缺少
            // "它们是一组"的信号 —— 读起来是三条并列的说明，把页面下方铺成一片均匀的辅助信息。
            // 收进一个 Column 后组内统一 12dp、与上方内容保持 24dp，层级从"堆叠"变成"内容 + 页脚"。
            // 页脚不承担任何操作，所以整块保持次级文字权重，不加卡片、不加分隔线。
            VerticalSpace(24)
            Column {
                // 解析方式只在「自建服务器」模式下提示。
                // 直连是默认且推荐的方式，常驻显示「App 直连（无需服务器）」对绝大多数用户是
                // 零信息量；而一旦切到服务器模式，用户就需要知道请求走的是哪个地址（失败排查
                // 全靠它）。所以这是一个「只在非默认状态出现」的状态条，而不是常驻信息。
                if (settings.parseMode == "server") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                // 用语义 token 而不是内联色值：值相同，但改主题时不会再漏掉这里
                                .background(WarningAmber),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = "解析服务：${settings.baseUrl}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        TextButton(onClick = onOpenSettings) {
                            Text("修改")
                        }
                    }
                    VerticalSpace(12)
                }
                // v1.8 第二轮：页脚说明文字已删除 —— 剪贴板识别能力改用输入框右侧的粘贴图标表达，
                // 首页底部不再需要一行灰色说明占用空间。
            }
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
                Spacer(Modifier.height(Spacing.sm))
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

/**
 * 首页「最近记录」的一行。
 *
 * 刻意不用卡片，也不加背景：首页上方已经有一张动作卡，再叠三张同形状的盒子会把整页
 * 变成一摞矩形，而这一屏最缺的恰恰是"实物"。这里靠 48dp 封面缩略图提供视觉密度，
 * 结构交给留白与右侧箭头，点击反馈交给波纹。
 */
@Composable
private fun RecentRecordRow(
    entity: HistoryEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = Platform.fromKey(entity.platform)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.field)
            .clickable(onClickLabel = "打开结果页", onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(Radii.small),
        ) {
            if (entity.coverUrl != null) {
                ShimmerImage(
                    model = entity.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = Radii.small,
                    onError = { CoverPlaceholder(platform, badgeSize = 22) },
                )
            } else {
                // 无封面：可能是平台没给封面，也可能是封面字段存的是视频地址
                // （见 HomeViewModel.saveHistory 的封面选取）—— 两种情况都走同一个占位
                CoverPlaceholder(platform, badgeSize = 22)
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = buildString {
                    platform?.let { append(it.label).append(" · ") }
                    append(formatHistoryTime(entity.createdAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextColors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextColors.muted,
            modifier = Modifier.size(20.dp),
        )
    }
}
