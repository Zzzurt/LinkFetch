package com.linkfetch.app.ui.home

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.R
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.db.HistoryEntity
import com.linkfetch.app.ui.components.CoverPlaceholder
import com.linkfetch.app.ui.components.ErrorCard
import com.linkfetch.app.ui.components.GroupCard
import com.linkfetch.app.ui.components.InkButton
import com.linkfetch.app.ui.components.MicroLabel
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.SectionHeader
import com.linkfetch.app.ui.components.ShimmerBox
import com.linkfetch.app.ui.components.ShimmerImage
import com.linkfetch.app.ui.components.SquareCheck
import com.linkfetch.app.ui.components.VerticalSpace
import com.linkfetch.app.ui.components.errorAdvice
import com.linkfetch.app.ui.theme.InkColors
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.StrokeColors
import com.linkfetch.app.ui.theme.TextColors
import com.linkfetch.app.util.Platform
import com.linkfetch.app.util.UrlExtractor
import com.linkfetch.app.util.formatHistoryTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenResult: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    // ViewModel 比 Activity 活得久，闭包里只持有 Application Context
    val appContext = context.applicationContext
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    parseService = container.parseService,
                    historyDao = container.historyDao,
                    historyRepository = container.historyRepository,
                    json = container.json,
                    downloadQueue = container.downloadQueue,
                    autoSaveProvider = {
                        container.settingsRepository.settings.value.autoSave && canAutoSave(appContext)
                    },
                    notificationPermission = container.notificationPermission,
                )
            }
        },
    )
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val recent by viewModel.recentItems.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        viewModel.onClipboardText(text)
    }

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            viewModel.onSharedText(sharedText)
            onSharedTextConsumed()
        }
    }

    LaunchedEffect(viewModel.result) {
        if (viewModel.result != null) {
            onOpenResult()
            viewModel.consumeResult()
        }
    }

    LaunchedEffect(viewModel.clipboardUrl) {
        if (viewModel.clipboardUrl == null) return@LaunchedEffect
        delay(10_000)
        viewModel.dismissClipboard()
    }

    val detectedPlatform = remember(viewModel.input) {
        Platform.detectFromText(viewModel.input)
    }
    // 批量粘贴时链接数量比「哪个平台」更值得提示
    val detectedCount = remember(viewModel.input) {
        UrlExtractor.extractAllPlatformUrls(viewModel.input).size
    }

    ScreenFadeIn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = Spacing.md),
        ) {
            // ---- 品牌条：黑色方块标记 + 名字 + 字距副标 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(Radii.field)
                        .background(InkColors.inverseSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_link_mark),
                        contentDescription = null,
                        tint = InkColors.inverse,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        text = "链取",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = "LINKFETCH",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.2.sp),
                        color = TextColors.muted,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, StrokeColors.card, CircleShape)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            VerticalSpace(22)

            // ---- 价值主张：一行微标签 + 两行大标题 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(1.5.dp)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
                Spacer(Modifier.width(9.dp))
                MicroLabel("无水印 · 不压缩", color = MaterialTheme.colorScheme.onSurface)
            }
            VerticalSpace(14)
            Text(
                text = "粘贴链接，\n取回原片。",
                style = MaterialTheme.typography.displayLarge.copy(
                    lineHeight = 43.sp,
                    letterSpacing = (-0.6).sp,
                ),
                fontWeight = FontWeight.Bold,
            )
            VerticalSpace(24)

            // 剪贴板提示：只在检测到链接时出现，保持一行，不抢输入卡的视觉
            AnimatedVisibility(
                visible = viewModel.clipboardUrl != null,
                enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
            ) {
                val url = viewModel.clipboardUrl ?: return@AnimatedVisibility
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(Radii.field)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, StrokeColors.card, Radii.field)
                        .clickable(onClick = viewModel::useClipboardUrl)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "剪贴板里有一条链接",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = url.take(28) + if (url.length > 28) "…" else "",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = TextColors.muted,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "使用",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "忽略",
                        tint = TextColors.muted,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable(onClick = viewModel::dismissClipboard),
                    )
                }
            }

            // ---- 输入工作台：白卡 + 细线，动作收在卡内 ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radii.card)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, StrokeColors.card, Radii.card)
                    .padding(17.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MicroLabel("链接 / LINK")
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(Radii.pill)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                val text = clipboard?.primaryClip
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                if (!text.isNullOrBlank()) viewModel.onInputChange(text.trim())
                            }
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "粘贴",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                BasicTextField(
                    value = viewModel.input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = { if (viewModel.input.isNotBlank()) viewModel.parse() },
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (viewModel.input.isEmpty()) {
                                Text(
                                    text = "https://… 或分享文案",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                    color = TextColors.muted.copy(alpha = 0.75f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Spacer(Modifier.height(10.dp))

                // 识别结果 / 自动识别提示
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (detectedPlatform != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    TextColors.muted
                                },
                            ),
                    )
                    Spacer(Modifier.width(7.dp))
                    if (detectedPlatform != null) {
                        Text(
                            text = if (detectedCount > 1) {
                                "已识别 $detectedCount 条链接"
                            } else {
                                "已识别 ${detectedPlatform.label}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text(
                            text = "粘贴后自动识别平台",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextColors.muted,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (viewModel.input.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清空输入",
                            tint = TextColors.muted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(onClick = viewModel::clearInput),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 平台名：识别到的平台用墨色，其余退成灰 —— 只用明度表达「当前是哪家」
                    val names = listOf(
                        Platform.XHS to "小红书",
                        Platform.DOUYIN to "抖音",
                        Platform.WEIBO to "微博",
                        Platform.X to "X",
                    )
                    names.forEachIndexed { index, (platform, label) ->
                        if (index > 0) {
                            Text(
                                text = "/",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextColors.muted.copy(alpha = 0.55f),
                            )
                        }
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 3.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                letterSpacing = 0.2.sp,
                            ),
                            color = if (platform == detectedPlatform) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                TextColors.muted
                            },
                            fontWeight = if (platform == detectedPlatform) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    InkButton(
                        text = when {
                            viewModel.batchProgress != null ->
                                "解析 ${viewModel.batchProgress?.first}/${viewModel.batchProgress?.second}"
                            viewModel.parsing -> "解析中…"
                            else -> "解析"
                        },
                        onClick = viewModel::parse,
                        enabled = viewModel.input.isNotBlank(),
                        loading = viewModel.parsing,
                        trailingArrow = true,
                        compact = true,
                    )
                }
            }

            if (viewModel.parsing) {
                VerticalSpace(12)
                ResultSkeletonCard()
            }

            viewModel.error?.let { message ->
                VerticalSpace(12)
                ErrorCard(
                    message = message,
                    suggestion = errorAdvice(viewModel.errorCode),
                )
                viewModel.diagnosticBody?.let { body ->
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
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

            if (recent.isNotEmpty()) {
                VerticalSpace(26)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "最近取回",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = recent.size.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = TextColors.muted,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.clickable(onClick = onOpenHistory),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "全部",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextColors.muted,
                        )
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = TextColors.muted,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                VerticalSpace(12)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    recent.take(3).forEach { entity ->
                        RecentTile(
                            entity = entity,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (viewModel.open(entity)) onOpenResult()
                            },
                        )
                    }
                    // 不足 3 条时补空位，避免单条记录被拉伸成巨幅
                    repeat((3 - recent.size).coerceAtLeast(0)) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            VerticalSpace(22)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (settings.parseMode == "server") {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (settings.parseMode == "server") {
                        "自建服务 · ${settings.baseUrl}"
                    } else {
                        "直连解析 · 正常"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextColors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "解析记录本机保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextColors.muted,
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 解析等待区的骨架卡 */
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
            ShimmerBox(modifier = Modifier.weight(1f).aspectRatio(1f), shape = Radii.small)
            ShimmerBox(modifier = Modifier.weight(1f).aspectRatio(1f), shape = Radii.small)
        }
    }
}

/**
 * 首页「最近取回」的一个格子：封面即内容。
 * 保存状态用右上角的黑色小方块表达，视频用播放圆点 —— 不写文字也能读出来。
 */
@Composable
private fun RecentTile(
    entity: HistoryEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val platform = Platform.fromKey(entity.platform)
    val isVideo = entity.type == "video" || entity.type == "mixed"
    Column(
        modifier = modifier
            .clip(Radii.small)
            .clickable(onClickLabel = "打开结果页", onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(Radii.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (entity.coverUrl != null) {
                ShimmerImage(
                    model = entity.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = Radii.small,
                    onError = { CoverPlaceholder(platform, badgeSize = 20) },
                )
            } else {
                CoverPlaceholder(platform, badgeSize = 20)
            }
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(11.dp),
                    )
                }
            } else if (entity.downloadedCount > 0) {
                SquareCheck(
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                    size = 18.dp,
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = entity.title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = buildString {
                platform?.let { append(it.label).append(" · ") }
                append(if (entity.type == "video") "视频" else "图片")
            },
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
            color = TextColors.muted,
            maxLines = 1,
        )
    }
}

/** API 28 及以下写相册需要存储权限；没有权限时不自动排队，留待用户在结果页手动授权后保存。 */
private fun canAutoSave(context: Context): Boolean =
    Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
