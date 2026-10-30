package com.linkfetch.app.ui.history

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 历史多选操作条的状态。
 *
 * 操作条渲染在导航层，与底部 Dock 共用同一个槽位；历史页只负责把当前全选状态
 * 和两个动作注册进来。这样底栏高度恒定，切换时列表不会重排。
 */
@Stable
class HistorySelectionBarState {
    var allSelected by mutableStateOf(false)
    var onSelectAllOrClear by mutableStateOf<() -> Unit>({})
    var onDelete by mutableStateOf<() -> Unit>({})
}
