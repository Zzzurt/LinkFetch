package com.linkfetch.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import com.linkfetch.app.ui.theme.InkColors
import com.linkfetch.app.ui.theme.LocalDarkTheme
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.StrokeColors
import com.linkfetch.app.ui.theme.TabularNums
import com.linkfetch.app.ui.theme.TextColors
import com.linkfetch.app.ui.theme.TintedSurfaceColors
import com.linkfetch.app.ui.theme.onPlatform
import com.linkfetch.app.ui.theme.platformAccent
import com.linkfetch.app.util.Platform

// ---------- 平台 / 类型标识 ----------

/**
 * 无封面 / 封面加载失败时的占位：浅灰底 + 平台代号。
 *
 * 为什么需要这一档：封面拿不到的原因不止一种（数据里就没这个字段、封面存的是视频地址解不了、
 * 图床防盗链拒绝），而此前的失败态是一块纯中性灰 —— 用户看到的是"这里坏了"，而不是
 * "这条记录属于哪个平台、只是没有封面可显示"。留下平台代号至少留住了身份信息，
 * 也让列表里的空位看起来是**有意为之**而不是渲染失败。
 *
 * 「本来就没有封面」与「封面加载失败」两条路径共用这个组件，避免两处各写一份参数后漂移。
 */
@Composable
fun BoxScope.CoverPlaceholder(platform: Platform?, badgeSize: Int = 26) {
    // 黑白灰体系里占位不再借用平台色：一块浅灰底 + 平台代号，颜色只留给用户取回的图
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        platform?.let {
            Text(
                text = when (it) {
                    Platform.XHS -> "XHS"
                    Platform.DOUYIN -> "DY"
                    Platform.WEIBO -> "WB"
                    Platform.X -> "X"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.4.sp,
                    // badgeSize 决定占位里那几字代号的视觉重量：列表 22、首页小格 20。
                    fontSize = (badgeSize * 0.42f).sp,
                ),
                color = TextColors.muted,
            )
        }
    }
}

// ---------- 反馈 ----------

/**
 * 错误提示卡。
 *
 * [suggestion] 是「下一步做什么」。加这一行的原因：失败恰恰是用户最需要知道下一步的时刻，
 * 而此前卡片只给一句现象描述 —— 底下那个「复制原始响应」对普通用户既无法理解也无法使用。
 *
 * ⚠️ 传 suggestion 之前必须先确认它与 [message] 互补、而非复述：部分 message 自身已经带
 * 行动指引（例如各平台 rate_limited 的文案里已提示「可在设置中配置 Cookie」），
 * 这种情况不要再补一遍。裁决逻辑统一在 errorAdvice() 里，调用方不要就地拼文案。
 */
@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    suggestion: String? = null,
) {
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
            // 两行时图标与首行文字对齐；单行时与文字顶部齐平，两种状态观感一致
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                suggestion?.let {
                    Spacer(Modifier.height(Spacing.xs))
                    // 只靠字号区分层级、不叠加透明度：errorContainer 是浅色系底，
                    // 次级文字再叠 alpha 会掉到 AA 线以下。
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
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
    /**
     * 覆盖按钮配色。默认按 [variant] 计算；仅在需要脱离体系（如蓝底面板上的
     * 白色主按钮、语义色按钮等）时显式传入。
     */
    colors: ButtonColors? = null,
) {
    // 禁用态统一用「中性底 + 可读的次级文字」，而不是把品牌色降透明度：
    // primary.copy(alpha = 0.4f) 的浅蓝底配 M3 默认的 onSurface@38% 文字，实测对比度只有约 2.2:1
    // —— 首页输入框为空时「开始解析」四个字几乎读不出来，而它偏偏是屏上最大的一块颜色。
    // 换成中性底之后禁用语义更清楚，也不再把品牌色花在一个当下不可用的动作上
    // （每屏只有一个填充色元素，那个位置应该留给"可用"的状态）。
    val disabledContainer = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val disabledContent = MaterialTheme.colorScheme.onSurfaceVariant
    val variantColors = when (variant) {
        ButtonVariant.Primary -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent,
        )
        ButtonVariant.Tonal -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent,
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled && !loading,
        // 黑白灰体系里按钮是矩形收角（8dp），不是胶囊 —— 胶囊太「消费级」，
        // 与「工具」的气质相冲。
        shape = Radii.field,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = colors ?: variantColors,
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
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(TextColors.muted),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextColors.muted,
                    )
                }
            }
        }
        actions()
    }
}

/**
 * 内容区块标题。
 *
 * 形态：标签（`titleSmall` + SemiBold + `onSurface`）+ 可选数量（次级色 + 等宽数字）
 * + 一条从标签之后延伸到内容右缘的**引线**（leader rule）。
 *
 * 为什么用引线，而不是「标签下方的满宽分隔线」：
 * 满宽线压在标签下面，会把这个标签变成一条"表格行 / 表单字段"的标题 —— 一条贯穿整页的横线
 * 把版面切成上下两半，读出来是文书感，不是内容感（尤其当右侧还有一大段空白线时，
 * 视觉重心会被拉到那根线上）。引线则停在文字的光学中线上、只占右侧原本空着的部分：
 * 既收住了这一行、给了标签一个依托，又没有制造页级的分界。层级交给字号字重与留白，
 * 分隔线只负责"收边"，不负责"切页"。
 *
 * 与顶部 header 的分工：**满宽分隔线 = 区块结束；标签后引线 = 页内的分区标签**。
 * 两种强度对应两种语义，不是不一致。
 *
 * 调用方负责外部间距：区块之间 24dp（Spacing.xl），标题到内容 12dp（Spacing.md）。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                // 编号格式：两位、等宽、大字距 —— 像印刷品上的页码
                text = count.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = TabularNums,
                    letterSpacing = 1.4.sp,
                ),
                color = TextColors.muted,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
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
    val highlightAlpha = if (LocalDarkTheme.current) 0.10f else 0.60f
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
    /** 加载失败时的替代内容；不传则退回一块中性底色 */
    onError: (@Composable BoxScope.() -> Unit)? = null,
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

            // 失败态交给调用方决定：拿不到封面未必是"有图但坏了"，也可能这条记录本来就
            // 没有可用的封面图（例如封面字段返回的是视频地址）。有 onError 就用它给一个
            // 可辨识的占位，没有才退回中性灰。
            is AsyncImagePainter.State.Error ->
                if (onError != null) {
                    onError()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }

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
    icon: ImageVector = Icons.Outlined.Image,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 空状态插画：三张错位的「空卡片」，右下角一颗实心方块 —— 不用彩色圆底，
        // 它和整页的黑白灰是一套语言。
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(150.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(148.dp)
                    .height(104.dp)
                    .graphicsLayer { rotationZ = -7f }
                    .clip(Radii.card)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, StrokeColors.card, Radii.card),
            )
            Box(
                modifier = Modifier
                    .width(148.dp)
                    .height(104.dp)
                    .graphicsLayer { rotationZ = 4f }
                    .clip(Radii.card)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, StrokeColors.card, Radii.card),
            )
            Box(
                modifier = Modifier
                    .width(156.dp)
                    .height(110.dp)
                    .clip(Radii.card)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, StrokeColors.card, Radii.card),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = TextColors.muted,
                    modifier = Modifier.align(Alignment.Center).size(26.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(13.dp)
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 22.sp),
            color = TextColors.muted,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xl))
            InkButton(text = actionText, onClick = onAction, trailingArrow = true)
        }
    }
}

// ---------------------------------------------------------------------------
// 黑白灰体系的通用零件
// ---------------------------------------------------------------------------

/** 微标签：9.5sp + 大字距，用于「链接 / LINK」「改动要点 / 05」这类系统注记。 */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextColors.muted,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
        color = color,
    )
}

/** 幽灵胶囊：白底 + 1px 描边，用于页头右侧的「选择 / 保存 / 粘贴」这类次级动作。 */
@Composable
fun GhostPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(Radii.pill)
            .background(
                if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            )
            .border(
                1.dp,
                if (enabled) StrokeColors.card else StrokeColors.card.copy(alpha = 0.6f),
                Radii.pill,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                TextColors.muted.copy(alpha = 0.55f)
            },
        )
    }
}

/** 反相方块：黑色圆角小方块 + check，用于「已保存 / 已选中」标记。 */
@Composable
fun SquareCheck(
    modifier: Modifier = Modifier,
    size: Dp = 17.dp,
    checked: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (checked) {
                    InkColors.inverseSurface
                } else {
                    Color.Transparent
                },
            )
            .then(
                if (checked) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = InkColors.inverse,
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}

/** 黑色实心主按钮（直角化）：首页「解析」、结果页「保存全部」。 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingArrow: Boolean = false,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .height(if (compact) 44.dp else 48.dp)
            .clip(Radii.field)
            .background(
                if (enabled && !loading) {
                    InkColors.inverseSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = InkColors.inverse,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.5.sp),
            color = if (enabled && !loading) InkColors.inverse else TextColors.muted,
        )
        if (trailingArrow) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = InkColors.inverse,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

// ---------- 通用分组卡片 ----------

/**
 * 通用分组卡片。
 *
 * 描边两套主题统一走 [StrokeColors.card]：此前浅色侧不画描边、只靠 1dp 阴影压在
 * Slate50 页面上（色差约 1.045:1），深色侧则一直有描边 —— 同一个组件两套分层逻辑。
 * 补齐浅色描边后，卡片边界的建立方式在两种主题下一致。
 * 阴影保留：它负责"浮起"的观感，描边负责"边界"的可辨，两者分工不同、不冲突。
 */
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
        // 黑白灰体系不用阴影：层级由 1px 描边与留白建立（投影会在浅灰纸上留下脏边）
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, StrokeColors.card),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), content = content)
    }
}

/**
 * 页面入场方位，由导航层按底部 Tab 的顺序提供（见 [ScreenFadeIn]）。
 *
 * 默认 false（从右侧进入）。非 Tab 页（结果页）也走默认值 —— 位移只有 4dp，
 * 方向不准确时观感差异可以忽略，不值得为此再引一条跨层依赖。
 */
val LocalScreenEnterFromLeft = compositionLocalOf { false }

/**
 * 页面级入场：淡入 180ms + 4dp 水平位移。
 *
 * navigation-compose 2.5.3 的 NavHost 没有声明式页面转场参数（2.7.0 才加入），
 * 用这个组件兜底，让四页切换不再硬切。返回栈 pop 回来的页面会重新组合，同样淡入，
 * 观感一致。
 *
 * [fromLeft] 决定位移方向：目标 Tab 在当前 Tab 左侧时从左边进入，反之从右边。
 * 位移刻意只有 4dp —— 它是「方向提示」而不是「滑动转场」。再大就会变成一段需要等待的
 * 运动，与「过路式工具」的定位冲突：用户是来办事的，不是来欣赏转场的。
 *
 * 透明度与位移都只作用在 graphicsLayer 上，不参与测量，因此不会引起布局跳动。
 * 只调透明度、不改测量，不会引起布局跳动。
 */
@Composable
fun ScreenFadeIn(
    modifier: Modifier = Modifier,
    fromLeft: Boolean = LocalScreenEnterFromLeft.current,
    content: @Composable BoxScope.() -> Unit,
) {
    var entrance by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrance = true }
    val progress by animateFloatAsState(
        targetValue = if (entrance) 1f else 0f,
        animationSpec = tween(180),
        label = "screenEntrance",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            val offsetPx = 4.dp.toPx()
            translationX = (1f - progress) * (if (fromLeft) -offsetPx else offsetPx)
        },
        content = content,
    )
}
