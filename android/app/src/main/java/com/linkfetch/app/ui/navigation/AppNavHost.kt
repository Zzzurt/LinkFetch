package com.linkfetch.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.linkfetch.app.ui.theme.Blue300
import com.linkfetch.app.ui.theme.Blue50
import com.linkfetch.app.ui.theme.Blue600
import com.linkfetch.app.ui.theme.Blue700
import com.linkfetch.app.ui.theme.PageMaxWidth

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
    // 必须在 NavTab 的 onClick 里先行记录，而不是靠 currentRoute 变化后的副作用 ——
    // 副作用要等新页面组合完成才会跑，那时方向已经被读走了。
    var lastTabIndex by remember { mutableStateOf(0) }
    val recordCurrentTab: () -> Unit = { lastTabIndex = tabIndexOf(currentRoute) }
    val enterFromLeft = tabIndexOf(currentRoute) < lastTabIndex

    Scaffold(
        bottomBar = {
            if (currentRoute == Route.HOME || currentRoute == Route.HISTORY || currentRoute == Route.SETTINGS) {
                // 高度用 heightIn(min) 而不是 height：M3 默认 80dp，压缩到 72dp 是为了手机竖屏
                // 多留一点内容高度；但固定高度在大字体（fontScale 2.0）下会把图标与文字压扁，
                // 所以给一个下限、允许 M3 按内容撑开。
                // 当前窗口不是 edge-to-edge，系统栏 inset 被系统消费为 0，72dp 不会被挤压；
                // ⚠️ 升 targetSdk 35 时 Android 15 会强制 edge-to-edge，inset 变成真实值 ——
                //    届时需去掉这个下限并给四个页面补 inset 处理。
                NavigationBar(modifier = Modifier.heightIn(min = 72.dp)) {
                    NavTab(
                        navController = navController,
                        route = Route.HOME,
                        label = "首页",
                        icon = Icons.Filled.Home,
                        selected = currentRoute == Route.HOME,
                        onBeforeNavigate = recordCurrentTab,
                    )
                    NavTab(
                        navController = navController,
                        route = Route.HISTORY,
                        label = "历史",
                        icon = Icons.Filled.List,
                        selected = currentRoute == Route.HISTORY,
                        onBeforeNavigate = recordCurrentTab,
                    )
                    NavTab(
                        navController = navController,
                        route = Route.SETTINGS,
                        label = "设置",
                        icon = Icons.Filled.Settings,
                        selected = currentRoute == Route.SETTINGS,
                        onBeforeNavigate = recordCurrentTab,
                    )
                }
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
 * 底部 Tab：图标与文字常驻。
 *
 * 早期版本做成「未选中只显示图标、选中才淡入文字」（Crossfade 图标↔文字 + label = null）。
 * 视觉上确实更紧凑，但代价是两个真实问题：未选中项的图标语义只能靠猜；label = null 让
 * M3 指示器的宽度改由自定义内容盒子决定，切页时胶囊宽度会跟着跳变。
 * 现在把 label 交回 NavigationBarItem，选中态只由指示器与颜色表达，文字恒定可见。
 */
@Composable
private fun RowScope.NavTab(
    navController: NavHostController,
    route: String,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    // 导航前回调：记录「从哪个 Tab 出发」，供新页面决定入场位移的方向
    onBeforeNavigate: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val selectedColor = if (isDark) Blue300 else Blue600
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            if (isDark) Blue700.copy(alpha = 0.55f) else Blue50
        } else {
            Color.Transparent
        },
        animationSpec = tween(220),
        label = "tabIndicator",
    )
    // 图标与文字共用同一个颜色动画，避免过渡中间帧两者出现色差
    val contentColor by animateColorAsState(
        targetValue = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "tabContent",
    )

    NavigationBarItem(
        selected = selected,
        onClick = {
            onBeforeNavigate()
            navController.navigateToTab(route)
        },
        icon = {
            // contentDescription = null：下面的文字标签已经承担了可访问名称，
            // 两边都设会让读屏把同一件事念两遍。
            Icon(icon, contentDescription = null)
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = contentColor,
            selectedTextColor = contentColor,
            indicatorColor = indicatorColor,
            unselectedIconColor = contentColor,
            unselectedTextColor = contentColor,
        ),
    )
}
