package com.linkfetch.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.linkfetch.app.ui.theme.LocalDarkTheme
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.StrokeColors
import com.linkfetch.app.ui.theme.TextColors

/**
 * 全 App 共用的浮层菜单。
 *
 * 不用 M3 默认菜单的原因：默认样式是「灰色抬升面 + 48dp 大行 + 16sp 正文」，
 * 放在这套黑白灰、12dp 卡片、10sp 微标签的界面里会显得来自另一个 App。
 * 这里把菜单收成一张小纸卡：白底、1px 描边、轻阴影，选项是紧凑的图标行。
 */
@Composable
fun PaperMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.width(208.dp),
        // 与触发按钮留一点呼吸，但不要飘得太远
        offset = DpOffset(0.dp, 6.dp),
        shape = Radii.card,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        // 深色模式沿用全局规则：不靠阴影，靠描边分层
        shadowElevation = if (LocalDarkTheme.current) 0.dp else 10.dp,
        border = BorderStroke(1.dp, StrokeColors.card),
        content = content,
    )
}

/** 菜单顶部的系统注记，例如「原链接 / LINK」。 */
@Composable
fun PaperMenuLabel(text: String) {
    MicroLabel(
        text = text,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp),
    )
}

/**
 * 菜单选项：16dp 图标 + 14sp 标签。
 *
 * [destructive] 只用于删除这类不可逆动作，颜色取全局错误色。
 */
@Composable
fun PaperMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val labelColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // 和 Dock 的按压态同一套逻辑：不用 M3 涟漪，用 110ms 的浅底过渡
    val pressBackground by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        animationSpec = tween(110),
        label = "menuItemPress",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 内缩 6dp 后再裁圆角：按压底色不会顶到菜单边缘
            .padding(horizontal = 6.dp)
            .clip(Radii.small)
            .background(pressBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 42.dp)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) labelColor else TextColors.muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            // 大字号下允许折成两行，菜单不会把「在浏览器中打开」裁成省略号
            maxLines = 2,
        )
    }
}

/** 选项之间的细分隔线；用于把「删除」这类危险动作与普通动作分开。 */
@Composable
fun PaperMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
