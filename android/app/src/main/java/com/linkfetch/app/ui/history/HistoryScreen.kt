package com.linkfetch.app.ui.history

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.data.db.HistoryEntity
import com.linkfetch.app.ui.components.EmptyState
import com.linkfetch.app.ui.components.PlatformBadge
import com.linkfetch.app.ui.components.ShimmerBox
import com.linkfetch.app.ui.components.TypeTag
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.platformAccent
import com.linkfetch.app.util.Platform
import com.linkfetch.app.util.formatHistoryTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    container: AppContainer,
    onOpenResult: () -> Unit,
    onGoHome: () -> Unit,
) {
    val context = LocalContext.current
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
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (viewModel.selectionMode) "已选 ${viewModel.selectedIds.size} 项" else "历史记录",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (viewModel.selectionMode) {
                IconButton(onClick = viewModel::requestDeleteSelected) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除所选",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = viewModel::clearSelection) {
                    Text("取消")
                }
            } else {
                IconButton(onClick = viewModel::requestClear, enabled = items.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, contentDescription = "清空历史")
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
                val accent = if (key == "all") {
                    MaterialTheme.colorScheme.primary
                } else {
                    platformAccent(Platform.fromKey(key)!!, isDark)
                }
                FilterChip(
                    selected = viewModel.filter == key,
                    onClick = { viewModel.onFilterChange(key) },
                    label = { Text(label) },
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))

        val visible = viewModel.visibleItems
        if (visible.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (items.isEmpty()) {
                    EmptyState(
                        title = "还没有下载记录",
                        message = "解析成功的内容会自动保存在这里",
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { entity ->
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
                        onReparse = { viewModel.reparse(entity) },
                        onDelete = { viewModel.delete(entity) },
                    )
                }
            }
        }
    }

    if (viewModel.confirmClear) {
        AlertDialog(
            onDismissRequest = { viewModel.handleClearConfirm(false) },
            shape = MaterialTheme.shapes.large,
            title = { Text("清空历史记录？") },
            text = { Text("清空后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.handleClearConfirm(true) }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.handleClearConfirm(false) }) {
                    Text("取消")
                }
            },
        )
    }

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
    onReparse: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = Radii.card,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                Spacer(Modifier.width(10.dp))
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                )
                if (entity.coverUrl != null) {
                    AsyncImage(
                        model = entity.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Platform.fromKey(entity.platform)?.let {
                            PlatformBadge(it, size = 26)
                        }
                    }
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
                Text(
                    text = buildString {
                        Platform.fromKey(entity.platform)?.let { append(it.label).append(" · ") }
                        append(formatHistoryTime(entity.createdAt))
                        if (entity.downloadedCount > 0) append(" · 已下载 ${entity.downloadedCount}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!selectionMode) {
                if (reParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onReparse) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "重新解析",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
