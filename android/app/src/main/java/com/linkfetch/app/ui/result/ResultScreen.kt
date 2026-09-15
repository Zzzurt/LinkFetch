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
import com.linkfetch.app.ui.components.PlatformDot
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.SectionHeader
import com.linkfetch.app.ui.components.ShimmerImage
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
                // 显式 Bold：另外三页的 PageHeader 是 Bold，而 Typography.titleLarge 是
                // SemiBold —— 不写这一句，结果页的标题会比别的页细一档。
                title = { Text("解析结果", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                // 复制原链接从信息头挪到 TopAppBar：它是页面级动作（对整条结果生效），
                // 不是标题的附属。留在信息头行内还有个副作用 —— IconButton 的最小触控目标是
                // 48dp，会把那一整行撑到 48dp 高，信息头就压不下去。
                actions = {
                    IconButton(onClick = onCopyOriginalUrl) {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = "复制原链接",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (result != null) {
                // v1.8：保存条改为「悬浮胶囊」—— 外边距 + 圆角，不再贴满底边。
                // 「保存」是结果页的主动作但不是常驻状态，浮起来比压底更轻；
                // 上下留 8dp、左右留 16dp，内容网格滚动时能观察到胶囊浮在上层。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
        // v1.8：胶囊圆角 + 更强投影，与外侧 Box 的留白配合成「悬浮操作条」
        shape = Radii.card,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
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
                    Spacer(Modifier.width(Spacing.sm))
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
                Spacer(Modifier.width(Spacing.sm))
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
        Spacer(Modifier.height(Spacing.sm))
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
    modifier: Modifier = Modifier,
) {
    val platform = Platform.fromKey(result.platform)
    // medias 中视频与图片可能交错（例如 X 平台按 mediaDetails 原始顺序返回），
    // 不能用 videos.size + i 反推下标，否则会把保存/预览指向错误的条目。
    //
    // 视频与图片各自按 kind 分组渲染（视频在前、图片在后），两组都持有 medias 的原始下标。
    // 这里必须遍历**全部**视频：此前只取 videos.firstOrNull()，而图片那组又把所有 video
    // 都过滤掉，于是 medias 里第 2 个及以后的视频既不在视频区、也不进网格 —— 界面上完全
    // 不可见（底部「全部保存」仍会下载它们，所以数据不丢，但用户只会以为少了几张）。
    val videoIndices = result.medias.indices.filter { result.medias[it].isVideo }
    val imageMediaIndices = result.medias.indices.filter { !result.medias[it].isVideo }

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
                    // 信息头：从 5 个元素压到 2 行（徽标 + 标题 + 元信息），约 93dp → 63dp。
                    // 三处变更及理由：
                    //  1. 标题 headlineSmall(20sp) → titleMedium(16sp)，maxLines 2 → 1。
                    //     这一行是「扫一眼确认是不是我要的那条」，不是阅读区 —— 降档后不再与
                    //     页标题(22sp)、底部条文案(15sp)抢层级，行数收敛也让信息头能真正变矮。
                    //     完整标题在历史页与原平台都能看到，不必在这里全展开。
                    //  2. TypeTag 退场。结果页里类型标签是冗余的：下面就是 9:16 播放器与图片网格，
                    //     用户看得见内容是什么。它的价值在历史页列表（那里没有预览），所以只从这一页移除。
                    //     类型信息并入元信息行（「12 图 · 1 视频」），数据没丢、行数没增。
                    //  3. 复制原链接移到 TopAppBar actions（见上方注释）。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // v1.8：身份标识从 28dp 徽标换成 8dp 平台彩点，
                        // 「这是哪个平台」还在，但不再与标题抢视觉重量
                        platform?.let {
                            PlatformDot(it)
                            Spacer(Modifier.width(Spacing.sm))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = result.title,
                                // 标题正文化：titleMedium → bodyLarge 半粗，
                                // 和页标题(22sp)拉开后，这一行更接近「内容」而不是「标题」
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // 元信息行同时承担三件事：平台身份（不能只靠色块 —— 对色觉障碍或
                            // 陌生品牌色来说几乎没有信息量）、作者、媒体构成。
                            // 数量用实际参与渲染的条目数，与下方视频区/网格保持一致。
                            val meta = buildList {
                                platform?.let { add(it.label) }
                                result.author?.let { add("@$it") }
                                if (imageMediaIndices.isNotEmpty()) add("${imageMediaIndices.size} 图")
                                if (videoIndices.isNotEmpty()) add("${videoIndices.size} 视频")
                            }.joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    // v1.8：取消信息头与媒体内容之间的满宽分隔线，改用留白分组 ——
                    // 线上方是「这是一条什么内容」，线下方是「可浏览的媒体本体」，
                    // 16dp 的留白足够分界，不需要再画一条贯穿整页的横线。
                    Spacer(Modifier.height(Spacing.lg))
                }
            }

            // 每个视频各占一个通栏 item。单视频（绝大多数情况）观感与之前完全一致；
            // 多视频时纵向依次排开，每个都能播放，不再有被默默吞掉的视频。
            videoIndices.forEach { mediaIndex ->
                item(key = "video-$mediaIndex", span = { GridItemSpan(maxLineSpan) }) {
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
                            // 内层再包一个「与视频等宽等高」的 Box：动作位需要一个精确贴在视频上的
                            // 定位容器。直接把动作位放进外层，它会贴到那个 `fillMaxWidth` 的限高容器
                            // 右上角 —— 宽屏下视频居中、容器更宽，按钮就会飘到视频外面去。
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(9f / 16f),
                            ) {
                                VideoPlayerView(
                                    url = result.medias[mediaIndex].url,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(Radii.card)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                // 单个视频的保存入口。此前视频区**没有任何保存按钮** ——
                                // 用户想只留这段视频，只能点底部「全部保存」把图文一并存下来。
                                // 位置取右上角（而图片是右下角）：ExoPlayer 的控制条在底部，
                                // 右下角会在控制条出现时被盖住、点不到。
                                MediaSaveAction(
                                    state = itemStates[mediaIndex] ?: ItemState.Idle,
                                    downloading = downloading,
                                    live = false,
                                    labelBase = "视频",
                                    onDownload = { onDownloadOne(mediaIndex) },
                                    // 视频不可能是 Live 图，live 恒为 false，此回调不会被调用
                                    onLiveChoice = {},
                                    align = Alignment.TopEnd,
                                )
                            }
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
                // 图片区标题只在「页面里同时存在视频」时才出现：此时它承担的是分组
                //（视频组 / 图片组），而不是"下面是一组图片"这种用户一眼就能看出来的信息。
                //
                // 纯图片作品不再显示这个标题 —— 顶部信息头已经写了「N 图」，网格本身也说明了
                // 它是图片，再补一个「图片 N」就是同一件事的第三次表达；而且它会在信息头与内容
                // 之间多插进一层「标签 + 引线」，和上面那条满宽分隔线挤在一起，整块显得碎。
                // 数量同理只由信息头说一次，所以这里不再传 count。
                //
                // ⚠️ 标题的显示条件必须与网格的渲染条件**分开判断**：两者原来共用同一个
                // `imageMediaIndices.isNotEmpty()`，若直接把它改成「且存在视频」，
                // 纯图片作品的整个网格都会消失。
                if (videoIndices.isNotEmpty()) {
                    item(key = "images-header", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = "图片",
                            // 水平边距仍走 textPadding，与上方作品标题对齐
                            // （网格本身是几乎贴边的，这两套边距不能混用）。
                            modifier = Modifier.padding(
                                start = textPadding,
                                end = textPadding,
                                top = Spacing.lg,
                                bottom = Spacing.md,
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
                        platform = platform,
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
 * 「相册式」排版：容器透明（不铺卡片底），圆角只留 [Radii.grid]，
 * 配合网格的 2dp 间隙让整组图片连成一片 —— 内容本身即版式，而不是被装进一个个卡片里。
 */
@Composable
private fun MediaCard(
    item: MediaItemDto,
    index: Int,
    state: ItemState,
    downloading: Boolean,
    platform: Platform?,
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
                .clip(Radii.grid)
                // 读屏用户否则既听不到"这是一张图"，也不知道点击会做什么
                .clickable(onClickLabel = "全屏预览", onClick = onClick),
            shape = Radii.grid,
            contentDescription = "第 ${index + 1} 张图片",
        )
            if (item.live) {
                // v1.8：Live 徽章从「主按钮蓝」改为「半透明表面底 + 平台彩点」——
                // 颜色只承担平台身份；「这是动图」的意思由文字 Live 表达
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.sm)
                        .clip(Radii.pill)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    platform?.let {
                        PlatformDot(it, size = 6.dp)
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    Text(
                        text = "Live",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // 动作位：与视频区共用同一套状态表现（见 MediaSaveAction）
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
 * 媒体保存动作位：把「当前状态 → 视觉 + 读屏文案 + 可点行为」这套映射集中在一处，
 * 供**图片网格**与**视频区**共用。
 *
 * 抽出来的直接原因：视频区此前**完全没有保存入口** —— 用户想只留这段视频，只能点底部
 * 「全部保存」，把同一条作品的其它媒体一并存下来。加视频的保存按钮时才发现这套状态映射
 * 原先写死在 MediaCard 内，必须先提出来才能复用，否则就是两份会各自漂移的重复逻辑。
 *
 * [labelBase] 只影响读屏文案：图片传「第 N 张」、视频传「视频」。
 * [live] 对视频恒为 false（Live 是图片的动图形态），因此 [onLiveChoice] 不会被调用。
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
            // 下载中：进度环
            MediaActionSlot(label = "${labelBase}保存中", align = align) {
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
            // 保存完成的反馈只保留这一处对勾。此前左下角还叠了一个「已保存」胶囊、
            // 底部常驻条又报了「已保存 N / M」，同一状态出现三层、同一张图上重复两遍。
            // Live 图：对勾本身可点，用来改存静态图；静态图与视频则只是完成标记
            MediaActionSlot(
                label = if (live) "已保存 Live 图，点击可再保存静态图" else "${labelBase}已保存",
                enabled = !downloading,
                onClick = if (live) onLiveChoice else null,
                align = align,
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
                label = if (live) "Live 图保存失败，点击选择方式" else "${labelBase}保存失败，点击重试",
                enabled = !downloading,
                onClick = if (live) onLiveChoice else onDownload,
                align = align,
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
                label = if (live) "保存$labelBase（Live 图）" else "保存$labelBase",
                enabled = !downloading,
                onClick = if (live) onLiveChoice else onDownload,
                align = align,
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

/**
 * 结果页媒体卡动作位的容器（位置 + 触控目标 + 语义）。
 *
 * 外层固定 48dp 作为触控目标（Material 建议的最小可点尺寸），内层由调用方决定视觉尺寸 ——
 * 这样"视觉 26/30/34dp 的圆点"与"手指可点区域"解耦，且位置不再随下载状态在左右角之间跳变。
 * 可访问性：动作文案挂在整块区域上，读屏会把"保存第 N 张"识别成一个整体动作。
 *
 * [align] 默认右下角；视频区传 `TopEnd` —— 视频的 ExoPlayer 控制条在**底部**，
 * 保存按钮若放右下角，会在控制条出现时被盖住、点不到。
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
                    Spacer(Modifier.height(Spacing.sm))
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
