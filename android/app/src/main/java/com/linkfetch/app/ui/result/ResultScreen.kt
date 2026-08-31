package com.linkfetch.app.ui.result

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.model.MediaItemDto
import com.linkfetch.app.data.model.ParseResponseDto
import com.linkfetch.app.ui.components.ErrorCard
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.PlatformBadge
import com.linkfetch.app.ui.components.ShimmerBox
import com.linkfetch.app.ui.components.TypeTag
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.TabularNums
import com.linkfetch.app.util.Platform

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
            initializer { ResultViewModel(container, appContext) }
        },
    )
    val result = viewModel.result
    val snackbarHostState = remember { SnackbarHostState() }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var liveChoiceIndex by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.downloadAll()
    }

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

    val downloadedCount = viewModel.itemStates.values.count { it is ItemState.Done }
    val total = result?.medias?.size ?: 0

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
                    failedCount = viewModel.failedCount,
                    onDownloadAll = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.downloadAll()
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
                itemStates = viewModel.itemStates,
                downloading = viewModel.downloading,
                failedCount = viewModel.failedCount,
                error = viewModel.error,
                onRetryFailed = viewModel::retryFailed,
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
            val state = viewModel.itemStates[index]
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

/** 常驻底部操作条：进度 + 全部下载 */
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
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
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
                Spacer(Modifier.width(Spacing.lg))
                LoadingButton(
                    text = when {
                        downloading -> "保存中…"
                        allDone -> "已全部保存"
                        else -> "全部保存"
                    },
                    loading = downloading,
                    onClick = onDownloadAll,
                    enabled = total > 0 && !allDone,
                )
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

@Composable
private fun ResultContent(
    result: ParseResponseDto,
    itemStates: Map<Int, ItemState>,
    downloading: Boolean,
    failedCount: Int,
    error: String?,
    onRetryFailed: () -> Unit,
    onDownloadOne: (Int) -> Unit,
    onLiveChoice: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = Platform.fromKey(result.platform)
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            platform?.let { PlatformBadge(it, size = 32) }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                result.author?.let {
                    Text(
                        text = "@$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TypeTag(result.type)
        }
        Spacer(Modifier.height(Spacing.lg))

        result.videos.firstOrNull()?.let { video ->
            // 竖屏视频等比缩放并居中，避免 9:16 与限高冲突导致偏离
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                VideoPlayerView(
                    url = video.url,
                    modifier = Modifier
                        .aspectRatio(9f / 16f)
                        .heightIn(max = 380.dp)
                        .clip(Radii.card)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }

        if (result.images.isNotEmpty()) {
            Text(
                text = "图片（${result.images.size}）· 点击图片可全屏预览",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            val startIndex = result.videos.size
            result.images.chunked(2).forEachIndexed { rowIndex, rowImages ->
                val lastOdd = rowImages.size == 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    rowImages.forEachIndexed { colIndex, image ->
                        val index = startIndex + rowIndex * 2 + colIndex
                        MediaCard(
                            item = image,
                            index = index,
                            state = itemStates[index] ?: ItemState.Idle,
                            downloading = downloading,
                            onDownload = { onDownloadOne(index) },
                            onLiveChoice = { onLiveChoice(index) },
                            onClick = { onPreview(index - startIndex) },
                            // 奇数图：最后一张通栏 16:9，打破全 1:1 方块的单调
                            modifier = if (lastOdd) {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            } else {
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        error?.let {
            ErrorCard(it)
            Spacer(Modifier.height(Spacing.md))
        }
        if (failedCount > 0) {
            OutlinedButton(
                onClick = onRetryFailed,
                modifier = Modifier.fillMaxWidth(),
                enabled = !downloading,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("重试失败（$failedCount）")
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

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
    Card(
        modifier = modifier,
        shape = Radii.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box {
            // 图片加载前显示 shimmer 占位（尺寸由调用方 modifier 决定）
            ShimmerBox(
                modifier = Modifier.fillMaxSize(),
                shape = Radii.card,
            )
            AsyncImage(
                model = item.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(Radii.card)
                    .clickable(onClick = onClick),
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(34.dp)
                            .clip(RoundedCornerShape(17.dp))
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
                    if (item.live) {
                        Box(
                            modifier = scaleModifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(26.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .clickable(enabled = !downloading, onClick = onLiveChoice),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "已保存 Live 图，点击可再保存静态图",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    } else {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "已保存",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = scaleModifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(22.dp),
                        )
                    }
                }
                is ItemState.Failed -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.error)
                            .clickable(
                                enabled = !downloading,
                                onClick = if (item.live) onLiveChoice else onDownload,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = if (item.live) "Live 图保存失败，点击选择方式" else "保存失败，点击重试",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onError,
                        )
                    }
                }
                ItemState.Idle -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                            .clickable(
                                enabled = !downloading,
                                onClick = if (item.live) onLiveChoice else onDownload,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = if (item.live) "保存第 ${index + 1} 张（Live 图）" else "保存第 ${index + 1} 张",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
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
                        "Live 视频下载失败。你可以重试，或仅保存静态原图。"
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
