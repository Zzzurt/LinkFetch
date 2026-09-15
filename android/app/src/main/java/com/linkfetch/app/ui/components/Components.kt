package com.linkfetch.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Slate300
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.platformAccent
import com.linkfetch.app.ui.theme.onPlatform
import com.linkfetch.app.util.Platform

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
            // 用 sizeIn 而不是 size：字号随系统放大时容器跟着长，单个汉字不会被圆标裁掉。
            // 极端字号下会退化成胶囊形，比切掉笔画好。
            .sizeIn(minWidth = size.dp, minHeight = size.dp)
            .background(
                Brush.linearGradient(listOf(lerp(accent, Color.White, 0.14f), accent)),
                CircleShape,
            )
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = platform.label.take(1),
            // 前景色随底色亮度自适应：橙/青亮底用深字、红/黑暗底用白字（AA 对比）
            color = onPlatform(accent),
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.45f).sp,
            maxLines = 1,
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
        // 深色下 onSurfaceVariant(#94A3B8) 压在 surfaceVariant(#273549) 上只有 4.84:1，
        // 12sp 小字没有余量，单独提亮一档（约 8.4:1）
        color = if (isSystemInDarkTheme()) Slate300 else MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * 页面标题栏：几个 Tab 页共用同一套字号、字重与对齐方式，
 * 避免切页时标题的位置与基线各跳各的（此前历史页和设置页各写一份 Row）。
 * 水平边距由调用方提供（设置页的外层 Column 已经有 padding）。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        actions()
    }
}

@Composable
fun VerticalSpace(height: Int) {
    Spacer(Modifier.height(height.dp))
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

/**
 * 带骨架屏的网络图片：只在「尚未加载完成」时显示 shimmer。
 *
 * 之前的写法是把 ShimmerBox 无条件垫在 AsyncImage 底下，图片加载完后 shimmer 只是被盖住，
 * 动画仍在逐帧跑 —— 列表里每张图挂一个无限动画，是滚动掉帧与耗电的直接来源。
 * 加载失败时退化为静态底色（不再循环动画），避免"加载失败的图永远在闪"。
 */
@Composable
fun ShimmerImage(
    model: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = Radii.small,
    contentDescription: String? = null,
) {
    val painter = rememberAsyncImagePainter(model)
    Box(modifier = modifier) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        when (painter.state) {
            is AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading,
            -> ShimmerBox(modifier = Modifier.fillMaxSize(), shape = shape)

            is AsyncImagePainter.State.Error ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )

            else -> Unit
        }
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

/**
 * 页面级入场：进入时整体淡入 180ms。
 *
 * navigation-compose 2.5.3 的 NavHost 没有声明式页面转场参数（2.7.0 才加入），
 * 用这个组件兜底，让四页切换不再硬切。返回栈 pop 回来的页面会重新组合，同样淡入，
 * 观感一致。只调透明度、不改测量，不会引起布局跳动。
 */
@Composable
fun ScreenFadeIn(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var entrance by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrance = true }
    val alpha by animateFloatAsState(
        targetValue = if (entrance) 1f else 0f,
        animationSpec = tween(180),
        label = "screenEntrance",
    )
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }, content = content)
}
