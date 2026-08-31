package com.linkfetch.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkfetch.app.ui.theme.Blue500
import com.linkfetch.app.ui.theme.Blue600
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.platformAccent
import com.linkfetch.app.util.Platform

// ---------- 品牌 ----------

/** App 内品牌位：蓝渐变圆角方块 + 链接图标 */
@Composable
fun BrandMark(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(Radii.card)
            .background(Brush.linearGradient(listOf(Blue600, Blue500))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Link,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

// ---------- 平台 / 类型标识 ----------

@Composable
fun PlatformBadge(
    platform: Platform,
    modifier: Modifier = Modifier,
    size: Int = 28,
) {
    val accent = platformAccent(platform, isSystemInDarkTheme())
    Box(
        modifier = modifier
            .size(size.dp)
            .background(
                Brush.linearGradient(listOf(lerp(accent, Color.White, 0.14f), accent)),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = platform.label.take(1),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.45f).sp,
        )
    }
}

@Composable
fun TypeTag(type: String, modifier: Modifier = Modifier) {
    val label = when (type) {
        "video" -> "视频"
        "image" -> "图片"
        "mixed" -> "视频 + 图片"
        else -> type
    }
    Text(
        text = label,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, Radii.pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------- 反馈 ----------

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------- 按钮（三级体系） ----------

enum class ButtonVariant { Primary, Tonal }

@Composable
fun LoadingButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Primary,
) {
    val colors = when (variant) {
        ButtonVariant.Primary -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        ButtonVariant.Tonal -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.extraLarge,
        colors = colors,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text)
    }
}

// ---------- 标题 / 间距 ----------

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(bottom = Spacing.xs),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun VerticalSpace(height: Int) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun VerticalSpace(height: Dp) {
    Spacer(Modifier.height(height))
}

// ---------- 骨架屏 ----------

/**
 * 微光扫过的骨架占位块。
 * 用法：加载前覆盖在目标区域上（图片、卡片、文字条均可）。
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = Radii.small) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -800f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "shimmerOffset",
    )
    val highlightAlpha = if (isSystemInDarkTheme()) 0.10f else 0.60f
    Box(modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = highlightAlpha),
                            Color.Transparent,
                        ),
                        start = Offset(offset - 260f, 0f),
                        end = Offset(offset, 260f),
                    ),
                ),
        )
    }
}

// ---------- 空状态 ----------

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.DownloadDone,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            LoadingButton(text = actionText, loading = false, onClick = onAction)
        }
    }
}

// ---------- 通用分组卡片 ----------

/** 通用分组卡片：浅色依赖柔和阴影，深色靠描边分层 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.card,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (isSystemInDarkTheme()) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), content = content)
    }
}
