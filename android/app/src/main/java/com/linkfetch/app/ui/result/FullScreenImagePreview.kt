package com.linkfetch.app.ui.result

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** 全屏图片预览：左右滑动切换 + 双指缩放 + 双击复位 + 箭头切换按钮。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenImagePreview(
    urls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val safeIndex = initialIndex.coerceIn(0, urls.size - 1)
        val pagerState = rememberPagerState(initialPage = safeIndex)
        val scope = rememberCoroutineScope()
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                pageCount = urls.size,
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(url = urls[page], modifier = Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }
            }
            // 左右切换按钮：多图时居中显示，切换带动画
            if (urls.size > 1 && pagerState.currentPage > 0) {
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .size(48.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一张", tint = Color.White.copy(alpha = 0.8f))
                }
            }
            if (urls.size > 1 && pagerState.currentPage < urls.size - 1) {
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .size(48.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一张", tint = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/**
 * 可缩放图片。手势策略（与 pager 共存，仿系统相册）：
 * - 未放大单指：不消费事件 → pager 水平滑动切页
 * - 已放大单指：第一指按下即消费 → 拖动平移图片
 * - 双指捏合：接管事件 → 缩放 + 跟随质心移动
 * - 双击：放大到 2.5x / 已放大则复位
 */
@Composable
private fun ZoomableImage(url: String, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    var active = false
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 已放大：从第一指按下就接管，避免 pager 抢走拖动
                    if (scale > 1f) {
                        down.consume()
                        active = true
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        // 第二根手指落下 → 立即接管进入缩放
                        if (!active && pressed.size >= 2) active = true
                        if (active) {
                            // calculateZoom/Pan 返回相对上一事件的增量，直接使用
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + event.calculatePan() else Offset.Zero
                            pressed.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // 缩放走动画（双击放大/复位平滑；捏合持续更新 target 视觉跟手），平移用瞬态保证拖动跟手
        val animatedScale by animateFloatAsState(
            targetValue = scale,
            animationSpec = tween(160),
            label = "imgScale",
        )
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

