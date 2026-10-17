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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.ParseResultStore
import com.linkfetch.app.data.model.MediaItemDto
import com.linkfetch.app.data.model.ParseResponseDto
import com.linkfetch.app.ui.components.InkButton
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.SectionHeader
import com.linkfetch.app.ui.components.ShimmerImage
import com.linkfetch.app.ui.components.SquareCheck
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.StrokeColors
import com.linkfetch.app.ui.theme.TabularNums
import com.linkfetch.app.ui.theme.TextColors
import com.linkfetch.app.util.Platform
import kotlinx.coroutines.launch

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
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ResultTopBar(
                    onBack = onBack,
                    onCopyOriginalUrl = onCopyOriginalUrl,
                )
            },
            bottomBar = {
                if (result != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
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

/**
 * 结果页顶栏：两个 36dp 的圆形按钮夹住居中的标题。
 *
 * 不用 M3 TopAppBar —— 那一版是「工具栏」语法（左图标 + 左标题），而结果页只有两个
 * 页面级动作，居中标题 + 对称圆钮更像阅读器，也和我们自己的 PageHeader 区分开。
 */
@Composable
private fun ResultTopBar(
    onBack: () -> Unit,
    onCopyOriginalUrl: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Text(
            text = "解析结果",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        CircleIconButton(
            icon = Icons.Filled.OpenInNew,
            contentDescription = "复制原链接",
            onClick = onCopyOriginalUrl,
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, StrokeColors.card, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(17.dp),
        )
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
    // 还没开始保存时折叠为单行：此时"已保存 0 / 3"和空进度条没有任何信息量
    val collapsed = !downloading && downloaded == 0 && failedCount == 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, StrokeColors.card, Radii.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (collapsed) {
            InkButton(
                text = "保存全部",
                onClick = onDownloadAll,
                modifier = Modifier.fillMaxWidth(),
                enabled = total > 0,
                trailingArrow = false,
            )
        } else if (stacked) {
            DownloadProgress(
                downloaded = downloaded,
                total = total,
                fraction = fraction,
                allDone = allDone,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            InkButton(
                text = when {
                    downloading -> "保存中…"
                    allDone -> "已全部保存"
                    else -> "保存全部"
                },
                onClick = onDownloadAll,
                modifier = Modifier.fillMaxWidth(),
                enabled = total > 0 && !allDone && !downloading,
                loading = downloading,
                trailingArrow = false,
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
                Spacer(Modifier.width(14.dp))
                InkButton(
                    text = when {
                        downloading -> "保存中…"
                        allDone -> "已全部保存"
                        else -> "保存全部"
                    },
                    onClick = onDownloadAll,
                    enabled = total > 0 && !allDone && !downloading,
                    loading = downloading,
                    compact = true,
                    trailingArrow = false,
                )
            }
        }
        if (failedCount > 0) {
            Spacer(Modifier.height(2.dp))
            TextButton(
                onClick = onRetryFailed,
                enabled = !downloading,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "重试失败（$failedCount）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
        Text(
            text = if (allDone) "$total / $total 已保存" else "$downloaded / $total 已保存",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNums),
            color = if (allDone) MaterialTheme.colorScheme.onSurface else TextColors.muted,
            fontWeight = if (allDone) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(Radii.pill),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
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
    modifier: Modifier = Modifier,
) {
    val platform = Platform.fromKey(result.platform)
    // medias 中视频与图片可能交错（例如 X 平台按 mediaDetails 原始顺序返回），
    // 不能用 videos.size + i 反推下标，否则会把保存/预览指向错误的条目。
    val videoIndices = result.medias.indices.filter { result.medias[it].isVideo }
    val imageMediaIndices = result.medias.indices.filter { !result.medias[it].isVideo }

    var entrance by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrance = true }

    // 图片网格几乎贴边（2dp），文字块保持 20dp：网格是「相册」，文字是「版面」。
    val gap = 2.dp
    val outerPadding = 2.dp
    val textPadding = 20.dp - outerPadding

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(outerPadding),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(horizontal = textPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        platform?.let {
                            Box(
                                modifier = Modifier
                                    .clip(Radii.chip)
                                    .border(1.dp, StrokeColors.card, Radii.chip)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = it.label,
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                        }
                        val meta = buildList {
                            if (imageMediaIndices.isNotEmpty()) add("${imageMediaIndices.size} 张原图")
                            if (videoIndices.isNotEmpty()) add("${videoIndices.size} 段视频")
                            add("原画质")
                        }.joinToString(" · ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextColors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val byline = buildList {
                        result.author?.let { add("@$it") }
                    }.joinToString(" · ")
                    if (byline.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = byline,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextColors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // 每个视频各占一个通栏 item
            videoIndices.forEach { mediaIndex ->
                item(key = "video-$mediaIndex", span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(horizontal = textPadding)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(9f / 16f),
                            ) {
                                VideoPlayerView(
                                    url = result.medias[mediaIndex].url,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(Radii.grid)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                // 位置取右上角：ExoPlayer 的控制条在底部
                                MediaSaveAction(
                                    state = itemStates[mediaIndex] ?: ItemState.Idle,
                                    downloading = downloading,
                                    live = false,
                                    labelBase = "视频",
                                    onDownload = { onDownloadOne(mediaIndex) },
                                    onLiveChoice = {},
                                    align = Alignment.TopEnd,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            if (imageMediaIndices.isNotEmpty()) {
                val lastFullWidthPosition =
                    if (imageMediaIndices.size % 2 == 1) imageMediaIndices.size - 1 else -1
                if (videoIndices.isNotEmpty()) {
                    item(key = "images-header", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = "图片",
                            modifier = Modifier.padding(
                                start = textPadding,
                                end = textPadding,
                                top = 6.dp,
                                bottom = 8.dp,
                            ),
                        )
                    }
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
                        onClick = { onPreview(imagePosition) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { this.alpha = alpha }
                            .aspectRatio(if (imagePosition == lastFullWidthPosition) 4f / 3f else 1f),
                    )
                }
            }

            // 信息条：格式 / 单张尺寸 / 数量，只在纯图片作品里出现
            if (imageMediaIndices.isNotEmpty() && videoIndices.isEmpty()) {
                item(key = "fileinfo", span = { GridItemSpan(maxLineSpan) }) {
                    FileInfoStrip(
                        result = result,
                        count = imageMediaIndices.size,
                        modifier = Modifier.padding(horizontal = textPadding, vertical = 18.dp),
                    )
                }
            }

            error?.let {
                item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(horizontal = textPadding)) {
                        Spacer(Modifier.height(12.dp))
                        com.linkfetch.app.ui.components.ErrorCard(it)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            item(key = "note", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "原图直存，不经过二次压缩。",
                    modifier = Modifier.padding(
                        start = textPadding,
                        end = textPadding,
                        top = if (error == null) 16.dp else 4.dp,
                        bottom = 8.dp,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextColors.muted,
                )
            }
        }
    }
}

/**
 * 图片信息条：格式 / 单张尺寸 / 数量。
 *
 * 这三项都来自解析结果或图片 URL 本身，不做任何推断 —— 拿不到时就写「原图尺寸」，
 * 不编一个好看的数字。三段之间用 1dp 竖线分隔，与卡片边框同一灰度。
 */
@Composable
private fun FileInfoStrip(
    result: ParseResponseDto,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val firstImage = result.medias.firstOrNull { !it.isVideo }
    val hasSize = (firstImage?.width ?: 0) > 0 && (firstImage?.height ?: 0) > 0
    val cells = buildList {
        add("格式" to formatLabel(firstImage?.url.orEmpty()))
        add("单张" to if (hasSize) "${firstImage?.width} × ${firstImage?.height}" else "原图尺寸")
        add("数量" to "$count 张")
    }
    Row(modifier = modifier.fillMaxWidth()) {
        cells.forEachIndexed { index, (label, value) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    color = TextColors.muted,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontFeatureSettings = TabularNums,
                    ),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (index != cells.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

/** 从 URL 推断文件格式，只做扩展名映射，不做内容嗅探 */
private fun formatLabel(url: String): String {
    val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "JPG"
        "png" -> "PNG"
        "webp" -> "WEBP"
        "gif" -> "GIF"
        "heic" -> "HEIC"
        "mp4" -> "MP4"
        "mov" -> "MOV"
        else -> "原始文件"
    }
}

/**
 * 结果页图片单元：容器透明，圆角只留 [Radii.grid]，让整组图片连成一片。
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
        ShimmerImage(
            model = item.url,
            modifier = Modifier
                .fillMaxSize()
                .clip(Radii.grid)
                .clickable(onClickLabel = "全屏预览", onClick = onClick),
            shape = Radii.grid,
            contentDescription = "第 ${index + 1} 张图片",
        )
        if (item.live) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(Radii.chip)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        letterSpacing = 0.8.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        MediaSaveAction(
            state = state,
            downloading = downloading,
            live = item.live,
            labelBase = "第 ${index + 1} 张",
            onDownload = onDownload,
            onLiveChoice = onLiveChoice,
        )
    }
}

/**
 * 媒体保存动作位：把「当前状态 → 视觉 + 读屏文案 + 可点行为」这套映射集中在一处。
 * [labelBase] 只影响读屏文案；[live] 对视频恒为 false。
 */
@Composable
private fun BoxScope.MediaSaveAction(
    state: ItemState,
    downloading: Boolean,
    live: Boolean,
    labelBase: String,
    onDownload: () -> Unit,
    onLiveChoice: () -> Unit,
    align: Alignment = Alignment.BottomEnd,
) {
    when (state) {
        is ItemState.Downloading -> {
            MediaActionSlot(label = "${labelBase}保存中", align = align) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(Radii.small)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
        ItemState.Done -> {
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
            MediaActionSlot(
                label = if (live) "已保存 Live 图，点击可再保存静态图" else "${labelBase}已保存",
                enabled = !downloading,
                onClick = if (live) onLiveChoice else null,
                align = align,
            ) {
                Box(modifier = scaleModifier) {
                    SquareCheck(size = 19.dp)
                }
            }
        }
        is ItemState.Failed -> {
            MediaActionSlot(
                label = if (live) "Live 图保存失败，点击选择方式" else "${labelBase}保存失败，点击重试",
                enabled = !downloading,
                onClick = if (live) onLiveChoice else onDownload,
                align = align,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(Radii.small)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
        ItemState.Idle -> {
            MediaActionSlot(
                label = if (live) "保存$labelBase（Live 图）" else "保存$labelBase",
                enabled = !downloading,
                onClick = if (live) onLiveChoice else onDownload,
                align = align,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(Radii.small)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                        .border(1.dp, StrokeColors.card.copy(alpha = 0.7f), Radii.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * 结果页媒体卡动作位的容器（位置 + 触控目标 + 语义）。
 * 外层固定 48dp 作为触控目标，内层由调用方决定视觉尺寸。
 */
@Composable
private fun BoxScope.MediaActionSlot(
    label: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    align: Alignment = Alignment.BottomEnd,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(align)
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
        shape = Radii.card,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (failed) "Live 图保存失败" else "选择保存方式",
                style = MaterialTheme.typography.titleSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = if (failed) {
                        "Live 视频保存失败。你可以重试，或仅保存静态原图。"
                    } else {
                        "Live 图会保存为单个动态照片（Motion Photo），Google 相册、小米、OPPO 等图库可直接播放；部分第三方相册仅显示静态图。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextColors.muted,
                )
                failedMessage?.let {
                    Spacer(Modifier.height(8.dp))
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
