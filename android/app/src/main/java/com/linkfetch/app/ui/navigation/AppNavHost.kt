package com.linkfetch.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.ui.components.LocalScreenEnterFromLeft
import com.linkfetch.app.ui.history.HistoryScreen
import com.linkfetch.app.ui.home.HomeScreen
import com.linkfetch.app.ui.result.ResultScreen
import com.linkfetch.app.ui.settings.SettingsScreen
import com.linkfetch.app.ui.theme.InkColors
import com.linkfetch.app.ui.theme.PageMaxWidth
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.StrokeColors
import com.linkfetch.app.ui.theme.TextColors

object Route {
    const val HOME = "home"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

/** 底部 Tab 的先后顺序，只用于判断页面入场位移的方向（见 ScreenFadeIn） */
private fun tabIndexOf(route: String?): Int = when (route) {
    Route.HOME -> 0
    Route.HISTORY -> 1
    Route.SETTINGS -> 2
    else -> 0
}

/**
 * Tab 页之间的统一导航：pop 回 visit 根(首页) + 保存/恢复各 Tab 状态 + singleTop。
 *
 * 为什么必须是唯一入口：此前底部 Tab 用这套 flags，而首页右上角设置图标 / 历史页空状态
 * 「去解析」用的是裸 navigate —— 同一个 Tab 页有两种入口、两套状态模型。裸入口会把
 * 设置页直接叠进返回栈（[home, settings]），之后从设置页点「首页」Tab 时，
 * popUpTo + restoreState 的状态恢复行为会与之前裸压栈的记录互相干扰，表现为
 * 「点了首页没反应」。收敛到这一个函数后，所有 Tab 页切换路径完全等价。
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 上一次停留的底部 Tab 序号：只用来给页面入场位移定方向。
    // 必须在 DockItem 的 onClick 里先行记录，而不是靠 currentRoute 变化后的副作用 ——
    // 副作用要等新页面组合完成才会跑，那时方向已经被读走了。
    var lastTabIndex by remember { mutableStateOf(0) }
    val recordCurrentTab: () -> Unit = { lastTabIndex = tabIndexOf(currentRoute) }
    val enterFromLeft = tabIndexOf(currentRoute) < lastTabIndex
    val showDock = currentRoute == Route.HOME ||
        currentRoute == Route.HISTORY ||
        currentRoute == Route.SETTINGS

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showDock) {
                FloatingDock(
                    currentRoute = currentRoute,
                    onBeforeNavigate = recordCurrentTab,
                    onNavigate = { route -> navController.navigateToTab(route) },
                )
            }
        },
    ) { innerPadding ->
        // 平板 / 折叠屏展开 / 横屏：内容统一限宽并居中。
        // 铺满时正文单行会超过 ~600dp，阅读要来回扫；图片网格也会被撑成巨幅。
        // 放在导航层是为了让四个页面共用同一约束，避免各页各写一套。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 入场方向：目标 Tab 在当前 Tab 左侧时从左边进入（详见 ScreenFadeIn）。
            // 用 CompositionLocal 下发而不是逐页传参 —— 四个页面的 ScreenFadeIn 调用点
            // 就不必各自感知导航顺序这件事。
            CompositionLocalProvider(LocalScreenEnterFromLeft provides enterFromLeft) {
                NavHost(
                    navController = navController,
                    startDestination = Route.HOME,
                    modifier = Modifier
                        .widthIn(max = PageMaxWidth)
                        .fillMaxHeight(),
                ) {
                    composable(Route.HOME) {
                        HomeScreen(
                            container = container,
                            onOpenResult = { navController.navigate(Route.RESULT) },
                            onOpenHistory = { navController.navigateToTab(Route.HISTORY) },
                            // 与底部 Tab 走同一套导航（navigateToTab）
                            onOpenSettings = { navController.navigateToTab(Route.SETTINGS) },
                        )
                    }
                    composable(Route.RESULT) {
                        ResultScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Route.HISTORY) {
                        HistoryScreen(
                            container = container,
                            onOpenResult = { navController.navigate(Route.RESULT) },
                            onGoHome = { navController.navigateToTab(Route.HOME) },
                        )
                    }
                    composable(Route.SETTINGS) {
                        SettingsScreen(container = container)
                    }
                }
            }
        }
    }
}

/**
 * 悬浮 Dock：一条白色胶囊托住三个 Tab。
 *
 * 为什么不再用 M3 NavigationBar：它的通栏底色会把页面切成「内容 + 一条杠」，
 * 而这一版希望导航像浮在纸面上的一件东西 —— 外边距、圆角、细边线，选中项是
 * 一枚黑色胶囊。视觉上退后一档，但点击面积仍然是整格（48dp 高）。
 */
@Composable
private fun FloatingDock(
    currentRoute: String?,
    onBeforeNavigate: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = Radii.dock,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.12f),
                )
                .clip(Radii.dock)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, StrokeColors.card, Radii.dock)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DockItem(
                modifier = Modifier.weight(1f),
                label = "首页",
                icon = Icons.Outlined.Home,
                selected = currentRoute == Route.HOME,
                onClick = {
                    onBeforeNavigate()
                    onNavigate(Route.HOME)
                },
            )
            DockItem(
                modifier = Modifier.weight(1f),
                label = "历史",
                icon = Icons.Outlined.Schedule,
                selected = currentRoute == Route.HISTORY,
                onClick = {
                    onBeforeNavigate()
                    onNavigate(Route.HISTORY)
                },
            )
            DockItem(
                modifier = Modifier.weight(1f),
                label = "设置",
                icon = Icons.Outlined.Tune,
                selected = currentRoute == Route.SETTINGS,
                onClick = {
                    onBeforeNavigate()
                    onNavigate(Route.SETTINGS)
                },
            )
        }
    }
}

private val DockItemShape = RoundedCornerShape(11.dp)

@Composable
private fun DockItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) InkColors.inverseSurface else Color.Transparent,
        animationSpec = tween(200),
        label = "dockItemBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) InkColors.inverse else TextColors.muted,
        animationSpec = tween(200),
        label = "dockItemContent",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(110),
        label = "dockPressScale",
    )

    Row(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(DockItemShape)
            .background(background)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
        )
    }
}
