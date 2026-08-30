package com.linkfetch.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
                NavigationBar {
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
        NavHost(
            navController = navController,
            startDestination = Route.HOME,
            modifier = Modifier.padding(innerPadding),
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

/** 底部 Tab：选中态品牌色 + 颜色过渡动画（NavigationBarItem 是 RowScope 扩展函数） */
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
        icon = { Icon(icon, contentDescription = null, tint = iconTint) },
        label = { Text(label, color = textTint) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = selectedColor,
            selectedTextColor = selectedColor,
            indicatorColor = indicatorColor,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
