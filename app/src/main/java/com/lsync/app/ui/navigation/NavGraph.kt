package com.lsync.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lsync.app.ui.bible.BibleScreen
import com.lsync.app.ui.calendar.CalendarScreen
import com.lsync.app.ui.finance.FinanceScreen
import com.lsync.app.ui.theme.*
import com.lsync.app.ui.todo.TodoScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Calendar : Screen("calendar", "캘린더", Icons.Outlined.CalendarMonth)
    data object Todo     : Screen("todo",     "할 일",  Icons.Outlined.CheckCircle)
    data object Finance  : Screen("finance",  "가계부", Icons.Outlined.AccountBalanceWallet)
    data object Bible    : Screen("bible",    "성경",   Icons.Outlined.MenuBook)
}

private val bottomNavItems = listOf(Screen.Calendar, Screen.Todo, Screen.Finance, Screen.Bible)

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        containerColor = BgPrimary,
        bottomBar = {
            NavigationBar(containerColor = BgSecondary) {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = {
                            Text(
                                screen.label,
                                fontFamily = Pretendard,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 0.06.em,
                            )
                        },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FgPrimary,
                            selectedTextColor = FgPrimary,
                            indicatorColor = AccentBlue20,
                            unselectedIconColor = FgDisabled,
                            unselectedTextColor = FgDisabled,
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calendar.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Calendar.route) { CalendarScreen() }
            composable(Screen.Todo.route)     { TodoScreen() }
            composable(Screen.Finance.route)  { FinanceScreen() }
            composable(Screen.Bible.route)    { BibleScreen() }
        }
    }
}
