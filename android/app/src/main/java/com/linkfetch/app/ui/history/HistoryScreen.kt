package com.linkfetch.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.db.HistoryEntity
import com.linkfetch.app.ui.components.CoverPlaceholder
import com.linkfetch.app.ui.components.EmptyState
import com.linkfetch.app.ui.components.PageHeader
import com.linkfetch.app.ui.components.PlatformDot
import com.linkfetch.app.ui.components.ShimmerImage
import com.linkfetch.app.ui.components.TypeTag
import com.linkfetch.app.ui.components.ScreenFadeIn
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    container: AppContainer,
    onOpenResult: () -> Unit,
    onGoHome: () -> Unit,
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    dao = container.historyDao,
                    json = container.json,
                    localParseClient = container.localParseClient,
                    apiClient = container.apiClient,
                    parseModeProvider = { container.settingsRepository.settings.value.parseMode },
                )
            }
        },
    )
    val items by viewModel.items.collectAsStateWithLifecycle()
    // 各平台记录数：直接从已加载的 items 统计，不改 DAO、不给 ViewModel 加 Flow。
    // 显示数字的目的不是"信息更丰富"，而是让用户在**点击之前**就知道这个筛选是空的 ——
    // 省掉一次「点进去才发现没有」。因此 0 也要显示出来。
    val platformCounts = remember(items) { items.groupingBy { it.platform }.eachCount() }
    val snackbarHostState = remember { SnackbarHostState() }
    // 单条删除确认（与批量删除一致，防误删）
    var confirmDelete by remember { mutableStateOf<HistoryEntity?>(null) }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 用 Box 承载 Snackbar 浮层（消息反馈与其他页统一走 Snackbar，原先用 Toast 样式不一致）；
    // 整页包 ScreenFadeIn：进入时淡入，去掉页面硬切感
    ScreenFadeIn(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = if (viewModel.selectionMode) "已选 ${viewModel.selectedIds.size} 项" else "历史记录",
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
            ) {
                if (viewModel.selectionMode) {
                    TextButton(onClick = viewModel::clearSelection) {
                        Text("取消")
                    }
                } else if (items.isNotEmpty()) {
                    // 常驻的多选入口：长按虽然也能进多选，但界面上没有任何提示，多数用户发现不了。
                    // 这里只留「选择」一个动作 —— 进去之后可以全选再删，原先并排的「清空历史」
                    // 图标与它功能重叠（同一件事的两条路径），却让右上角长期挂着两个入口。
                    TextButton(onClick = viewModel::enterSelection) {
                        Text("选择")
                    }
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                listOf(
                    "all" to "全部",
                    "xhs" to "小红书",
                    "douyin" to "抖音",
                    "weibo" to "微博",
                    "x" to "X",
                ).forEach { (key, label) ->
                    val count = if (key == "all") items.size else platformCounts[key] ?: 0
                    FilterChip(
                        selected = viewModel.filter == key,
                        onClick = { viewModel.onFilterChange(key) },
                        label = { Text("$label $count") },
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        // 选中态统一走品牌容器色，不再用平台本体色。
                        // 平台色在别处（PlatformBadge）表示"这是哪个平台"，在这里却被拿去表示
                        // "已选中" —— 同一个颜色两种含义；而且橙/青底色亮、红/黑暗度高，
                        // 字色还得随选中项在深字/白字之间反复反转，同一行里文字颜色会变。
                        // 颜色只承担两种含义（品牌/状态、平台身份），"选中"交给容器层级表达。
                        //
                        // border 保持不传：M3 默认已是「未选中 1dp outline / 选中 0dp」
                        // （见 FilterChipTokens.FlatUnselectedOutlineWidth），形状差异本来就有，
                        // 不需要自己再补一层描边。
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
    
            val visible = viewModel.visibleItems
            val allSelected = visible.isNotEmpty() && visible.all { it.id in viewModel.selectedIds }
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (items.isEmpty()) {
                        EmptyState(
                            title = "还没有解析记录",
                            message = "解析成功的内容会自动记录在这里",
                            actionText = "去解析",
                            onAction = onGoHome,
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
                    contentPadding = PaddingValues(bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    grouped.forEach { (label, group) ->
                        // 分组标题吸顶：长列表滚到中段时仍能看到这一段属于哪天。
                        // 此前标题会随内容滚走，看到一张卡片无法判断它是今天还是上周的。
                        stickyHeader(key = "header-$label") {
                            // v1.8：分组标题从「黑字条」改为「小字 + 引线」（沿用 SectionHeader 语言）——
                            // 吸顶时仍保持「这段属于哪天」的分组信息，但不再像一块标题栏那样压着列表。
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 吸顶的两个硬要求不变：不透明底色 + 铺满屏宽
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = Spacing.screen)
                                    .padding(top = Spacing.md, bottom = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(Spacing.md))
                                Divider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                        items(group, key = { it.id }) { entity ->
                            Box(modifier = Modifier.padding(horizontal = Spacing.screen)) {
                                HistoryCard(
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
    
            // 多选模式：底部操作条（全选 / 删除），拇指可达
            if (viewModel.selectionMode) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = viewModel::selectAllOrClear) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::requestDeleteSelected) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            // 多选时把浮层抬到底部操作条之上
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (viewModel.selectionMode) 56.dp else 0.dp),
        )
    }

    // 「清空历史」入口已移除：它与「选择 → 全选 → 删除」是同一件事的两条路径。
    // 现在删除统一走多选流程，因此这里也不再需要单独的二次确认弹窗。

    if (viewModel.confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { viewModel.handleDeleteSelected(false) },
            shape = MaterialTheme.shapes.large,
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
            shape = MaterialTheme.shapes.large,
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
            val label = when (date) {
                today -> "今天"
                today.minusDays(1) -> "昨天"
                else -> "${date.monthValue}月${date.dayOfMonth}日"
            }
            label to list
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
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
    // 每张卡片各自持有一个下拉菜单的展开状态
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selectionMode) {
                    // 多选态：用 checkbox 语义，读屏才能读出「已选中 / 未选中」并给出切换动作；
                    // 原先只有 combinedClickable，读屏完全感知不到选中状态
                    Modifier.toggleable(
                        value = selected,
                        role = Role.Checkbox,
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
            ),
        shape = Radii.card,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        // 描边两套主题统一走 StrokeColors.card：此前浅色侧没有描边，白卡只靠 1dp 阴影
        // 压在 Slate50 页面上（约 1.045:1），一屏卡片边界几乎不可见。
        border = BorderStroke(1.dp, StrokeColors.card),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "已选",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.md))
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(Radii.small),
            ) {
                val platform = Platform.fromKey(entity.platform)
                if (entity.coverUrl != null) {
                    // 加载失败也走同一个占位：封面字段可能存的是视频地址（旧记录尤其常见，
                    // 见 HomeViewModel.saveHistory 的封面选取），那种情况 Coil 必然失败，
                    // 不该显示成一块看不出所以然的灰。
                    ShimmerImage(
                        model = entity.coverUrl,
                        modifier = Modifier.fillMaxSize(),
                        shape = Radii.small,
                        onError = { CoverPlaceholder(platform, badgeSize = 26) },
                    )
                } else {
                    CoverPlaceholder(platform, badgeSize = 26)
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entity.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    TypeTag(entity.type)
                }
                Spacer(Modifier.height(4.dp))
                // v1.8：平台身份改用彩点 + 时间/保存数文字。
                // 平台名从 meta 行拿掉（TypeTag 与封面仍在表达类型与内容），
                // 行内只留「彩点(身份) + 相对时间 + 已保存数」，信息密度更整。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Platform.fromKey(entity.platform)?.let { platform ->
                        PlatformDot(platform, size = 6.dp)
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    Text(
                        text = buildString {
                            append(formatHistoryTime(entity.createdAt))
                            if (entity.downloadedCount > 0) append(" · 已保存 ${entity.downloadedCount} 张")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!selectionMode) {
                if (reParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    // 「重新解析」与「删除」收进一个下拉菜单。
                    // 原先两个同色同权的图标按钮常驻在每条卡片右侧 —— 一屏七八条就是十几个
                    // 可点区域，其中还有一个破坏性操作，既吵又容易误触。
                    // 收进菜单后卡片右侧只剩一个入口，删除也不再与安全操作平起平坐。
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "更多操作",
                                tint = TextColors.muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("重新解析") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onReparse()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
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
}
