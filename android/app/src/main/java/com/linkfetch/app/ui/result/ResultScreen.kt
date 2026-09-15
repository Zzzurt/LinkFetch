package com.linkfetch.app.ui.result

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.ParseResultStore
import com.linkfetch.app.data.model.MediaItemDto
import com.linkfetch.app.data.model.ParseResponseDto
import com.linkfetch.app.ui.components.ErrorCard
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.PlatformBadge
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.ShimmerImage
import com.linkfetch.app.ui.components.TypeTag
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.TabularNums
import com.linkfetch.app.util.Platform
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel: ResultViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ResultViewModel(container, AndroidDownloadNotifier(appContext)) }
        },
    )
    val result = viewModel.result
    val itemStates by viewModel.itemStates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var liveChoiceIndex by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    // 复制原链接：拿回分享文案里的链接去浏览器打开或转发给朋友，是这类工具的常见副线需求
    val onCopyOriginalUrl: () -> Unit = {
        val url = ParseResultStore.originalUrl
        if (url.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("原链接已丢失，请重新解析") }
        } else {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("链接", url))
            scope.launch { snackbarHostState.showSnackbar("原链接已复制") }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.downloadAll()
    }

    // API 28 及以下：向共享存储写入必须持有 WRITE_EXTERNAL_STORAGE（API 29+ 分区存储无需权限）
    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.downloadAll() else viewModel.onStoragePermissionDenied()
    }
    val needsLegacyStoragePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED
    val needsNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let { text ->
            val savedUri = viewModel.lastSavedUri
            val snackbarResult = if (savedUri != null) {
                // 下载成功：轻震反馈
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(
                    message = text,
                    actionLabel = "查看",
                    duration = androidx.compose.material3.SnackbarDuration.Short,
                )
            } else {
                snackbarHostState.showSnackbar(text)
            }
            if (snackbarResult == androidx.compose.material3.SnackbarResult.ActionPerformed && savedUri != null) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            data = savedUri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                }
            }
            viewModel.dismissMessage()
            viewModel.consumeSavedUri()
        }
    }
    LaunchedEffect(Unit) {
        if (result == null) onBack()
    }

    val downloadedCount = itemStates.values.count { it is ItemState.Done }
    val failedCount = itemStates.values.count { it is ItemState.Failed }
    val total = result?.medias?.size ?: 0

    ScreenFadeIn(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("解析结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            if (result != null) {
                DownloadBottomBar(
                    downloaded = downloadedCount,
                    total = total,
                    downloading = viewModel.downloading,
                    failedCount = failedCount,
                    onDownloadAll = {
                        when {
                            needsLegacyStoragePermission ->
                                legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            needsNotificationPermission ->
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> viewModel.downloadAll()
                        }
                    },
                    onRetryFailed = viewModel::retryFailed,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        result?.let { data ->
            ResultContent(
                result = data,
                itemStates = itemStates,
                downloading = viewModel.downloading,
                error = viewModel.error,
                onDownloadOne = viewModel::downloadOne,
                onLiveChoice = { liveChoiceIndex = it },
                onPreview = { previewIndex = it },
                onCopyLink = onCopyOriginalUrl,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }

    liveChoiceIndex?.let { index ->
        val item = result?.medias?.getOrNull(index)
        if (item != null && item.live) {
            val state = itemStates[index]
            LiveChoiceDialog(
                failed = state is ItemState.Failed,
                failedMessage = (state as? ItemState.Failed)?.message,
                onDismiss = { liveChoiceIndex = null },
                onLive = {
                    liveChoiceIndex = null
                    viewModel.downloadOneLive(index)
                },
                onStatic = {
                    liveChoiceIndex = null
                    viewModel.downloadOneStatic(index)
                },
            )
        }
    }

    previewIndex?.let { index ->
        val urls = result?.images?.map { it.url }.orEmpty()
        if (urls.isNotEmpty()) {
            FullScreenImagePreview(
                urls = urls,
                initialIndex = index.coerceIn(0, urls.size - 1),
                onDismiss = { previewIndex = null },
            )
        }
    }
    }
}

/** 常驻底部操作条：进度 + 全部保存 */
@Composable
private fun DownloadBottomBar(
    downloaded: Int,
    total: Int,
    downloading: Boolean,
    failedCount: Int,
    onDownloadAll: () -> Unit,
    onRetryFailed: () -> Unit,
) {
    val fraction = if (total > 0) downloaded.toFloat() / total else 0f
    val allDone = total > 0 && downloaded == total
    // 大字体下横向排列会把进度文案挤成省略号、按钮也会被压缩，改成上下两行堆叠
    val stacked = LocalDensity.current.fontScale >= 1.3f
    // 还没开始保存时折叠为单行：此时"已保存 0 / 12"和空进度条没有任何信息量，
    // 去掉后少一层噪音，底部条也矮 8dp。
    val collapsed = !downloading && downloaded == 0 && failedCount == 0
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.lg,
                vertical = if (collapsed) Spacing.sm else Spacing.md,
            ),
        ) {
            if (collapsed) {
                DownloadAllButton(
                    downloading = downloading,
                    allDone = allDone,
                    total = total,
                    onClick = onDownloadAll,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (stacked) {
                DownloadProgress(
                    downloaded = downloaded,
                    total = total,
                    fraction = fraction,
                    allDone = allDone,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                DownloadAllButton(
                    downloading = downloading,
                    allDone = allDone,
                    total = total,
                    onClick = onDownloadAll,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DownloadProgress(
                        downloaded = downloaded,
                        total = total,
                        fraction = fraction,
                        allDone = allDone,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.lg))
                    DownloadAllButton(
                        downloading = downloading,
                        allDone = allDone,
                        total = total,
                        onClick = onDownloadAll,
                    )
                }
            }
            if (failedCount > 0) {
                Spacer(Modifier.height(Spacing.xs))
                TextButton(
                    onClick = onRetryFailed,
                    enabled = !downloading,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("重试失败（$failedCount）")
                }
            }
        }
    }
}

/** 进度文案 + 进度条 */
@Composable
private fun DownloadProgress(
    downloaded: Int,
    total: Int,
    fraction: Float,
    allDone: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (allDone) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (allDone) "已全部保存到相册" else "已保存 $downloaded / $total",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularNums),
                color = if (allDone) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(Radii.pill),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun DownloadAllButton(
    downloading: Boolean,
    allDone: Boolean,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LoadingButton(
        text = when {
            downloading -> "保存中…"
            allDone -> "已全部保存"
            else -> "全部保存"
        },
        loading = downloading,
        onClick = onClick,
        modifier = modifier,
        enabled = total > 0 && !allDone,
    )
}

@Composable
private fun ResultContent(
    result: ParseResponseDto,
    itemStates: Map<Int, ItemState>,
    downloading: Boolean,
    error: String?,
    onDownloadOne: (Int) -> Unit,
    onLiveChoice: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    onCopyLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = Platform.fromKey(result.platform)
    // medias 中视频与图片可能交错（例如 X 平台按 mediaDetails 原始顺序返回），
    // 不能用 videos.size + i 反推下标，否则会把保存/预览指向错误的条目。
    val imageMediaIndices = result.medias.indices.filter { !result.medias[it].isVideo }
    val video = result.videos.firstOrNull()

    // 首帧入场：让网格逐项错峰淡入，避免一整页"啪"地出现。
    // entrance 置 true 后不再变，滚动新增的 item 直接显示、不重复动画。
    var entrance by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrance = true }

    // ---- 图片网格：「相册式」定稿排版 ----
    // 圆角 4dp、间隙 2dp、容器透明，图片彼此连成一片（此前每张图是 16dp 圆角 + 8dp 间隙的独立卡片，
    // 画面被切成若干块「卡」，视线在容器和图片之间反复切换）。
    // 网格外缘与文字块边距必须分开算：网格几乎贴边，而标题/说明要保持 16dp 与其他页面对齐；
    // LazyVerticalGrid 只有一个 contentPadding，所以给文字类 item 单独补内边距。
    val gap = 2.dp
    val outerPadding = 2.dp
    val textPadding = Spacing.lg - outerPadding

    // 内容最大宽度由 AppNavHost 统一限制并居中（PageMaxWidth），此处只负责承载网格
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            // 自适应列数：手机 2 列，宽屏自动 3~4 列；同时获得 item 复用
            // （原先用 Column + 手写 Row 网格，几十张图会一次性全量组合）
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(outerPadding),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(horizontal = textPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        platform?.let { PlatformBadge(it, size = 32) }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = result.title,
                                // 内容标题要压得住副标题与底部条文案(labelLarge 15sp)；
                                // 此前用的是 titleMedium 16sp，跟底部条几乎同级
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // 平台名补进副标题：原先只有一个色块+单字徽标，
                            // 对色觉障碍或陌生品牌色来说几乎没有信息量
                            val subtitle = listOfNotNull(
                                platform?.label,
                                result.author?.let { "@$it" },
                            ).joinToString(" · ")
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TypeTag(result.type)
                        IconButton(onClick = onCopyLink) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = "复制原链接",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    // 细线分隔「作品信息」与「媒体内容」，补上结果页缺失的层级
                    // 注：material3 1.1.0 里这个组件还叫 Divider（1.2 起才改名 HorizontalDivider）
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            if (video != null) {
                item(key = "video", span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(horizontal = textPadding)) {
                        // 限高必须放在「容器」上、内容用 fillMaxHeight 撑满高度。
                        // 不能写成 Modifier.aspectRatio(...).heightIn(max=380) —— AspectRatioModifier 是按
                        // 自己算出的尺寸上报的（子节点被 heightIn 压到 380，它依然按 9:16 上报 360x640），
                        // 结果是一个 640dp 高的槽位里只画 380dp 的卡片，下方留出约 260dp 空白。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            VideoPlayerView(
                                url = video.url,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(9f / 16f)
                                    .clip(Radii.card)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                        Spacer(Modifier.height(Spacing.lg))
                    }
                }
            }

            if (imageMediaIndices.isNotEmpty()) {
                // 奇数张时的最后一张通栏展示，打破全 1:1 方块的单调。
                // 通栏判定只在这里算一次，span 与宽高比共用，避免两处条件不一致。
                val lastFullWidthPosition =
                    if (imageMediaIndices.size % 2 == 1) imageMediaIndices.size - 1 else -1
                item(key = "images-header", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "图片（${result.images.size}）· 点击图片可全屏预览",
                        modifier = Modifier.padding(horizontal = textPadding),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(
                    items = imageMediaIndices,
                    key = { _, mediaIndex -> "media-$mediaIndex" },
                    span = { imagePosition, _ ->
                        if (imagePosition == lastFullWidthPosition) {
                            GridItemSpan(maxLineSpan)
                        } else {
                            GridItemSpan(1)
                        }
                    },
                ) { imagePosition, mediaIndex ->
                    // 首帧逐项错峰淡入：每张比前一张晚 40ms，上限 400ms，250ms 淡入。
                    // 用 graphicsLayer 只调透明度，不改变测量尺寸，网格布局不会跳动。
                    val alpha by animateFloatAsState(
                        targetValue = if (entrance) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 250,
                            delayMillis = (imagePosition * 40).coerceAtMost(400),
                        ),
                        label = "mediaEntrance",
                    )
                    MediaCard(
                        item = result.medias[mediaIndex],
                        index = mediaIndex,
                        state = itemStates[mediaIndex] ?: ItemState.Idle,
                        downloading = downloading,
                        onDownload = { onDownloadOne(mediaIndex) },
                        onLiveChoice = { onLiveChoice(mediaIndex) },
                        // imagePosition 即该图在 result.images 中的位置，供全屏预览定位
                        onClick = { onPreview(imagePosition) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { this.alpha = alpha }
                            // 通栏卡用 4:3 而不是 16:9：竖构图（小红书/抖音绝大多数是 3:4、9:16）
                            // 放进 16:9 会被裁掉一大半，4:3 的裁切量小得多
                            .aspectRatio(if (imagePosition == lastFullWidthPosition) 4f / 3f else 1f),
                    )
                }
            }

            // 失败时的重试入口只保留底部常驻条那一处：
            // 正文再放一个同名按钮会让用户犹豫"这两个是不是不一样"，长列表里它也容易被滚过去
            error?.let {
                item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(horizontal = textPadding)) {
                        Spacer(Modifier.height(Spacing.md))
                        ErrorCard(it)
                    }
                }
            }
        }
    }
}

/**
 * 结果页图片单元。
 *
 * 「相册式」排版：容器透明（不铺卡片底），圆角只留 [Radii.thumbnail]，
 * 配合网格的 2dp 间隙让整组图片连成一片 —— 内容本身即版式，而不是被装进一个个卡片里。
 */
@Composable
private fun MediaCard(
    item: MediaItemDto,
    index: Int,
    state: ItemState,
    downloading: Boolean,
    onDownload: () -> Unit,
    onLiveChoice: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // 图片加载前显示 shimmer 占位（尺寸由调用方 modifier 决定）
        ShimmerImage(
            model = item.url,
            modifier = Modifier
                .fillMaxSize()
                .clip(Radii.thumbnail)
                // 读屏用户否则既听不到"这是一张图"，也不知道点击会做什么
                .clickable(onClickLabel = "全屏预览", onClick = onClick),
            shape = Radii.thumbnail,
            contentDescription = "第 ${index + 1} 张图片",
        )
            if (item.live) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(Radii.pill)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Live",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            when (state) {
                is ItemState.Downloading -> {
                    // 下载中：胶囊内进度环
                    MediaActionSlot(label = "第 ${index + 1} 张保存中") {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                progress = state.progress,
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            )
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                ItemState.Done -> {
                    // 保存成功：对勾弹性放大入场（稳定 API，替代 AnimatedVisibility + scaleIn）
                    val scale = remember { Animatable(0.4f) }
                    LaunchedEffect(Unit) {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                    val scaleModifier = Modifier.graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(Radii.pill)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "已保存",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    // Live 图：对勾本身可点，用来改存静态图；静态图则只是完成标记
                    MediaActionSlot(
                        label = if (item.live) "已保存 Live 图，点击可再保存静态图" else "已保存",
                        enabled = !downloading,
                        onClick = if (item.live) onLiveChoice else null,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = scaleModifier.size(26.dp),
                        )
                    }
                }
                is ItemState.Failed -> {
                    MediaActionSlot(
                        label = if (item.live) "Live 图保存失败，点击选择方式" else "保存失败，点击重试",
                        enabled = !downloading,
                        onClick = if (item.live) onLiveChoice else onDownload,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onError,
                            )
                        }
                    }
                }
                ItemState.Idle -> {
                    MediaActionSlot(
                        label = if (item.live) "保存第 ${index + 1} 张（Live 图）" else "保存第 ${index + 1} 张",
                        enabled = !downloading,
                        onClick = if (item.live) onLiveChoice else onDownload,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
}

/**
 * 结果页媒体卡右下角的统一动作位。
 *
 * 外层固定 48dp 作为触控目标（Material 建议的最小可点尺寸），内层由调用方决定视觉尺寸 ——
 * 这样"视觉 26/30/34dp 的圆点"与"手指可点区域"解耦，且位置不再随下载状态在左右角之间跳变。
 * 可访问性：动作文案挂在整块区域上，读屏会把"保存第 N 张"识别成一个整体动作。
 */
@Composable
private fun BoxScope.MediaActionSlot(
    label: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(48.dp)
            .semantics { contentDescription = label }
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LiveChoiceDialog(
    failed: Boolean,
    failedMessage: String?,
    onDismiss: () -> Unit,
    onLive: () -> Unit,
    onStatic: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
            shape = MaterialTheme.shapes.large,
        title = { Text(if (failed) "Live 图保存失败" else "选择保存方式") },
        text = {
            Column {
                Text(
                    if (failed) {
                        "Live 视频保存失败。你可以重试，或仅保存静态原图。"
                    } else {
                        "Live 图会保存为单个动态照片（Motion Photo），Google 相册、小米、OPPO 等图库可直接播放；部分第三方相册仅显示静态图。"
                    },
                )
                failedMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onLive) {
                Text(if (failed) "重试保存 Live 图" else "保存 Live 图")
            }
        },
        dismissButton = {
            TextButton(onClick = onStatic) {
                Text(if (failed) "仅保存静态图" else "保存静态图")
            }
        },
    )
}
