package com.linkfetch.app.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.getValue
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

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute == Route.HOME || currentRoute == Route.HISTORY || currentRoute == Route.SETTINGS) {
                // 紧凑导航栏（M3 默认是 80dp + 系统栏 inset）。
                // 当前窗口不是 edge-to-edge，系统栏 inset 被系统消费为 0，所以 64dp 不会被挤压；
                // ⚠️ 升 targetSdk 35 时 Android 15 会强制 edge-to-edge，inset 变成真实值，
                //    64 - inset 会把 Tab 压扁 —— 届时需去掉这个固定高度并给四个页面补 inset 处理。
                NavigationBar(modifier = Modifier.height(64.dp)) {
                    NavTab(
                        navController = navController,
                        route = Route.HOME,
                        label = "首页",
                        icon = Icons.Filled.Home,
                        selected = currentRoute == Route.HOME,
                    )
                    NavTab(
                        navController = navController,
                        route = Route.HISTORY,
                        label = "历史",
                        icon = Icons.Filled.List,
                        selected = currentRoute == Route.HISTORY,
                    )
                    NavTab(
                        navController = navController,
                        route = Route.SETTINGS,
                        label = "设置",
                        icon = Icons.Filled.Settings,
                        selected = currentRoute == Route.SETTINGS,
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
                        onOpenSettings = { navController.navigate(Route.SETTINGS) },
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
                        onGoHome = { navController.navigate(Route.HOME) },
                    )
                }
                composable(Route.SETTINGS) {
                    SettingsScreen(container = container)
                }
            }
        }
    }
}

/** 底部 Tab：未选中只显示图标；选中时图标淡出、文字淡入（Crossfade），整体更紧凑。 */
@Composable
private fun RowScope.NavTab(
    navController: NavHostController,
    route: String,
    label: String,
    icon: ImageVector,
    selected: Boolean,
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
    val iconTint by animateColorAsState(
        targetValue = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "tabIcon",
    )
    val textTint by animateColorAsState(
        targetValue = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "tabText",
    )

    NavigationBarItem(
        selected = selected,
        onClick = {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = {
            // 最小 48dp 容器容纳图标与文字，避免 Crossfade 切换时宽度跳变。
            // 用 sizeIn 而不是 size：系统字体放大后"设置"两字可以撑开容器，不会被裁掉。
            Box(
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = selected,
                    animationSpec = tween(180),
                    label = "tabIconText",
                ) { isSelected ->
                    if (isSelected) {
                        Text(
                            text = label,
                            color = textTint,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    } else {
                        Icon(icon, contentDescription = label, tint = iconTint)
                    }
                }
            }
        },
        label = null,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = selectedColor,
            selectedTextColor = selectedColor,
            indicatorColor = indicatorColor,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
