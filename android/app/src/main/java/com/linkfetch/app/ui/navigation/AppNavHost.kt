package com.linkfetch.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.ui.history.HistoryScreen
import com.linkfetch.app.ui.home.HomeScreen
import com.linkfetch.app.ui.result.ResultScreen
import com.linkfetch.app.ui.settings.SettingsScreen

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
                    NavigationBarItem(
                        selected = currentRoute == Route.HOME,
                        onClick = {
                            navController.navigate(Route.HOME) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("首页") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Route.HISTORY,
                        onClick = {
                            navController.navigate(Route.HISTORY) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.List, contentDescription = null) },
                        label = { Text("历史") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Route.SETTINGS,
                        onClick = {
                            navController.navigate(Route.SETTINGS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("设置") },
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
                )
            }
            composable(Route.SETTINGS) {
                SettingsScreen(container = container)
            }
        }
    }
}

