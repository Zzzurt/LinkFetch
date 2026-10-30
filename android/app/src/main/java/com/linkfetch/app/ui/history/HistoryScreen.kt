package com.linkfetch.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.db.HistoryEntity
import com.linkfetch.app.ui.components.CoverPlaceholder
import com.linkfetch.app.ui.components.EmptyState
import com.linkfetch.app.ui.components.GhostPill
import com.linkfetch.app.ui.components.PageHeader
import com.linkfetch.app.ui.components.PaperMenu
import com.linkfetch.app.ui.components.PaperMenuDivider
import com.linkfetch.app.ui.components.PaperMenuItem
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.ShimmerImage
import com.linkfetch.app.ui.components.SquareCheck
import com.linkfetch.app.ui.components.sharedCover
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.StrokeColors
import com.linkfetch.app.ui.theme.TextColors
import com.linkfetch.app.util.Platform
import com.linkfetch.app.util.formatHistoryTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ExperimentalFoundationApi：用到的是 LazyListScope.stickyHeader（分组标题吸顶）。
// 注意它是 LazyListScope 的**接口成员**、不是顶层扩展函数，所以在 LazyColumn 的 scope 里
// 直接可用，不需要也不能 import（写过一次 import，编译器报 Unresolved reference）。
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    container: AppContainer,
    onOpenResult: () -> Unit,
    onGoHome: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit = {},
    selectionBarState: HistorySelectionBarState,
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    dao = container.historyDao,
                    repository = container.historyRepository,
                    json = container.json,
                    parseService = container.parseService,
                )
            }
        },
    )
    val items by viewModel.items.collectAsStateWithLifecycle()
    // 各平台记录数：直接从已加载的 items 统计，不改 DAO、不给 ViewModel 加 Flow。
    // 显示数字的目的不是"信息更丰富"，而是让用户在**点击之前**就知道这个筛选是空的 ——
    // 省掉一次「点进去才发现没有」。因此 0 也要显示出来。
    val platformCounts = remember(items) { items.groupingBy { it.platform }.eachCount() }
    val savedTotal = remember(items) { items.sumOf { it.downloadedCount } }
    val snackbarHostState = remember { SnackbarHostState() }
    // 单条删除确认（与批量删除一致，防误删）
    var confirmDelete by remember { mutableStateOf<HistoryEntity?>(null) }

    // 多选期间底部换成「全选 / 删除」操作条，Dock 要让位；离页时恢复 Dock。
    // 用 SideEffect 而不是 LaunchedEffect：状态变化当帧就通知导航层，避免操作条晚一拍出现。
    SideEffect {
        onSelectionModeChange(viewModel.selectionMode)
    }
    DisposableEffect(Unit) {
        onDispose {
            onSelectionModeChange(false)
            // 操作条由导航层持有；离页时清掉回调，避免它继续引用本页的 ViewModel
            selectionBarState.allSelected = false
            selectionBarState.onSelectAllOrClear = {}
            selectionBarState.onDelete = {}
        }
    }

    // 多选态下系统返回键先退出多选，而不是直接退出应用
    BackHandler(enabled = viewModel.selectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(viewModel.message) {
        val text = viewModel.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(text)
    }

    // 用 Box 承载 Snackbar 浮层（消息反馈与其他页统一走 Snackbar，原先用 Toast 样式不一致）；
    // 整页包 ScreenFadeIn：进入时淡入，去掉页面硬切感
    ScreenFadeIn(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = if (viewModel.selectionMode) "已选 ${viewModel.selectedIds.size} 项" else "历史",
                subtitle = if (viewModel.selectionMode) {
                    "点选条目后可批量删除"
                } else {
                    "${items.size} 条记录 · 已存 $savedTotal 个文件"
                },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                actions = {
                    if (viewModel.selectionMode) {
                        GhostPill(text = "取消", onClick = viewModel::clearSelection)
                    } else if (items.isNotEmpty()) {
                        SearchToggle(active = viewModel.searchOpen, onClick = viewModel::toggleSearch)
                        Spacer(Modifier.width(8.dp))
                        // 常驻的多选入口：长按虽然也能进多选，但界面上没有任何提示，多数用户发现不了。
                        // 这里只留「选择」一个动作 —— 进去之后可以全选再删。
                        GhostPill(text = "选择", onClick = viewModel::enterSelection)
                    }
                },
            )

            Spacer(Modifier.height(14.dp))

            // 平台筛选：一排细边小胶囊，选中是实心墨块；数字压缩成上标式小字，
            // 五个筛选项刚好排满一行，不再折行。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "all" to "全部",
                    "xhs" to "小红书",
                    "douyin" to "抖音",
                    "weibo" to "微博",
                    "x" to "X",
                ).forEach { (key, label) ->
                    val count = if (key == "all") items.size else platformCounts[key] ?: 0
                    HistoryChip(
                        label = label,
                        count = count,
                        selected = viewModel.filter == key,
                        onClick = { viewModel.onFilterChange(key) },
                    )
                }
            }

            if (viewModel.searchOpen) {
                Spacer(Modifier.height(10.dp))
                HistorySearchField(
                    value = viewModel.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            Spacer(Modifier.height(6.dp))

            val visible = viewModel.visibleItems
            val allSelected = visible.isNotEmpty() && visible.all { it.id in viewModel.selectedIds }
            // 操作条在导航层渲染；这里只把当前状态和动作交出去，不参与底栏布局。
            val selectAllOrClear = remember(viewModel) { viewModel::selectAllOrClear }
            val requestDeleteSelected = remember(viewModel) { viewModel::requestDeleteSelected }
            SideEffect {
                selectionBarState.allSelected = allSelected
                selectionBarState.onSelectAllOrClear = selectAllOrClear
                selectionBarState.onDelete = requestDeleteSelected
            }
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (items.isEmpty()) {
                        EmptyState(
                            title = "还没有取回记录",
                            message = "在首页粘贴一条分享链接，\n取回的内容会按天归档在这里。",
                            actionText = "去首页粘贴",
                            onAction = onGoHome,
                        )
                    } else if (viewModel.query.isNotBlank()) {
                        EmptyState(
                            title = "没有匹配的记录",
                            message = "标题和作者里都没有「${viewModel.query.trim()}」",
                            icon = Icons.Outlined.SearchOff,
                            actionText = "清除搜索",
                            onAction = { viewModel.onQueryChange("") },
                        )
                    } else {
                        EmptyState(
                            title = "该平台暂无记录",
                            message = "换个平台筛选试试",
                            icon = Icons.Outlined.SearchOff,
                        )
                    }
                }
            } else {
                val grouped = remember(visible) { groupByDay(visible) }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    // 水平内边距从 contentPadding 下移到各 item 内部：吸顶分组头必须铺满屏宽，
                    // 否则吸顶时左右各 16dp 会露出正在滚动的卡片。
                    contentPadding = PaddingValues(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    grouped.forEach { (label, group) ->
                        // 分组标题吸顶：长列表滚到中段时仍能看到这一段属于哪天。
                        stickyHeader(key = "header-$label") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 吸顶的两个硬要求不变：不透明底色 + 铺满屏宽
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 14.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.8.sp,
                                    ),
                                    color = TextColors.muted,
                                )
                            }
                        }
                        items(group, key = { it.id }) { entity ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                HistoryRow(
                                    entity = entity,
                                    selectionMode = viewModel.selectionMode,
                                    selected = entity.id in viewModel.selectedIds,
                                    reParsing = viewModel.reParsingId == entity.id,
                                    onClick = {
                                        if (viewModel.selectionMode) {
                                            viewModel.toggleSelect(entity.id)
                                        } else if (viewModel.open(entity)) {
                                            onOpenResult()
                                        }
                                    },
                                    onLongPress = { viewModel.longPress(entity.id) },
                                    onToggle = { viewModel.toggleSelect(entity.id) },
                                    onReparse = { viewModel.reparse(entity) },
                                    onDelete = { confirmDelete = entity },
                                )
                            }
                        }
                    }
                }
            }

        }

        SnackbarHost(
            hostState = snackbarHostState,
            // 操作条现在是导航层 bottomBar 的一部分，Scaffold 的内边距已经把它让开，
            // 这里不再手动抬高浮层。
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // 删除统一走多选流程，因此这里也不再需要单独的「清空历史」确认弹窗。

    if (viewModel.confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { viewModel.handleDeleteSelected(false) },
            shape = Radii.card,
            title = { Text("删除所选记录？") },
            text = { Text("将删除 ${viewModel.selectedIds.size} 条记录，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.handleDeleteSelected(true) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.handleDeleteSelected(false) }) {
                    Text("取消")
                }
            },
        )
    }

    confirmDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            shape = Radii.card,
            title = { Text("删除这条记录？") },
            text = {
                Text(
                    text = "「${entity.title}」删除后无法恢复。",
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = null
                        viewModel.delete(entity)
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/** 筛选胶囊：细边 + 小字 + 计数，选中态反成黑底白字 */
@Composable
private fun HistoryChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(Radii.chip)
            .background(
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else StrokeColors.card,
                shape = Radii.chip,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = count.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                letterSpacing = 0.6.sp,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
            } else {
                TextColors.muted
            },
        )
    }
}

/** 与 GhostPill 同高的小圆钮：展开/收起搜索。 */
@Composable
private fun SearchToggle(
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, StrokeColors.card, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (active) Icons.Filled.Close else Icons.Filled.Search,
            contentDescription = if (active) "关闭搜索" else "搜索",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 历史搜索框：标题与作者的前端过滤，展开即聚焦。 */
@Composable
private fun HistorySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(Radii.field)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, StrokeColors.card, Radii.field)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = TextColors.muted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "搜索标题或作者",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = TextColors.muted,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = "清除搜索",
                tint = TextColors.muted,
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onValueChange("") },
            )
        }
    }
}

/** 按日期分组：今天 / 昨天 / M月d日，组内保持时间倒序 */
private fun groupByDay(items: List<HistoryEntity>): List<Pair<String, List<HistoryEntity>>> {
    val today = LocalDate.now()
    return items
        .groupBy { item ->
            Instant.ofEpochMilli(item.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        .entries
        .sortedByDescending { it.key }
        .map { (date, list) ->
            val dateLabel = "${date.monthValue}月${date.dayOfMonth}日"
            val label = when (date) {
                today -> "$dateLabel · 今天"
                today.minusDays(1) -> "$dateLabel · 昨天"
                else -> dateLabel
            }
            label to list
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entity: HistoryEntity,
    selectionMode: Boolean,
    selected: Boolean,
    reParsing: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: () -> Unit,
    onReparse: () -> Unit,
    onDelete: () -> Unit,
) {
    // 每行各自持有一个下拉菜单的展开状态
    var menuOpen by remember { mutableStateOf(false) }
    val platform = Platform.fromKey(entity.platform)
    val mediaCount = remember(entity.mediaJson) { entity.mediaCount() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(1.dp, StrokeColors.card, Radii.card)
            .then(
                if (selectionMode) {
                    // 多选态：用 checkbox 语义，读屏才能读出「已选中 / 未选中」并给出切换动作
                    Modifier.toggleable(
                        value = selected,
                        role = androidx.compose.ui.semantics.Role.Checkbox,
                        onValueChange = { onToggle() },
                    )
                } else {
                    Modifier.combinedClickable(
                        onClickLabel = "打开结果页",
                        onLongClickLabel = "选择",
                        onClick = onClick,
                        onLongClick = onLongPress,
                    )
                },
            )
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(modifier = Modifier.padding(start = 1.dp, end = 11.dp)) {
                SquareCheck(checked = selected, size = 18.dp)
            }
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(Radii.small)
                .sharedCover("history-cover-${entity.id}"),
        ) {
            if (entity.coverUrl != null) {
                // 加载失败也走同一个占位：封面字段可能存的是视频地址（旧记录尤其常见），
                // 那种情况 Coil 必然失败，不该显示成一块看不出所以然的灰。
                ShimmerImage(
                    model = entity.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = Radii.small,
                    onError = { CoverPlaceholder(platform, badgeSize = 22) },
                )
            } else {
                CoverPlaceholder(platform, badgeSize = 22)
            }
            if (entity.downloadedCount > 0) {
                SquareCheck(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
                    size = 14.dp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = buildString {
                        platform?.let { append(it.label).append(" · ") }
                        if (entity.type == "video") {
                            append("视频")
                        } else {
                            if (mediaCount > 0) append("$mediaCount 张") else append("媒体")
                        }
                        if (entity.downloadedCount > 0) {
                            append(" · 已存 ${entity.downloadedCount}")
                        }
                        append(" · ")
                        append(formatHistoryTime(entity.createdAt).substringAfterLast(" "))
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextColors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!selectionMode) {
            if (reParsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "更多操作",
                            tint = TextColors.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    PaperMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        PaperMenuItem(
                            icon = Icons.Filled.Refresh,
                            label = "重新解析",
                            onClick = {
                                menuOpen = false
                                onReparse()
                            },
                        )
                        PaperMenuDivider()
                        PaperMenuItem(
                            icon = Icons.Filled.Delete,
                            label = "删除",
                            destructive = true,
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 媒体数量：只用于列表里的一行说明，不做业务判断 */
private fun HistoryEntity.mediaCount(): Int = runCatching {
    org.json.JSONObject(mediaJson).optJSONArray("medias")?.length() ?: 0
}.getOrDefault(0)
