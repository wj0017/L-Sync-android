package com.lsync.app.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lsync.app.ui.auth.AuthViewModel
import com.lsync.app.ui.auth.LoginScreen
import com.lsync.app.ui.bible.BibleScreen
import com.lsync.app.ui.finance.FinanceScreen
import com.lsync.app.ui.home.HomeScreen
import com.lsync.app.ui.report.ReportScreen
import com.lsync.app.ui.schedule.ScheduleScreen
import com.lsync.app.ui.search.SearchScreen
import com.lsync.app.ui.theme.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home     : Screen("home",     "홈",    Icons.Outlined.Home)
    data object Schedule : Screen("schedule", "일정",  Icons.Outlined.CalendarMonth)
    data object Finance  : Screen("finance",  "가계부", Icons.Outlined.AccountBalanceWallet)
    data object Bible    : Screen("bible",    "성경",   Icons.Outlined.MenuBook)
}

private val bottomNavItems = listOf(Screen.Home, Screen.Schedule, Screen.Finance, Screen.Bible)

@Composable
fun NavGraph(
    authViewModel: AuthViewModel = hiltViewModel(),
    navTarget: String? = null,
    onTargetConsumed: () -> Unit = {},
) {
    val authState by authViewModel.uiState.collectAsState()

    if (!authState.isSignedIn) {
        LoginScreen(viewModel = authViewModel)
        return
    }

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
        // 외부 딥링크 타깃 탭으로 이동. 로그인 완료(authState 변경) 시점에 트리거되므로
        // 콜드 스타트/미로그인에서도 target이 보존됐다가 로그인 후 소비된다.
        LaunchedEffect(authState.isSignedIn, navTarget) {
            if (!authState.isSignedIn) return@LaunchedEffect
            // 리포트는 하단 탭이 아닌 별도 라우트라 탭 이동 로직(popUpTo/restoreState)을 태우지 않는다.
            // 홈 헤더 진입과 동일하게 현재 백스택 위로 쌓아 뒤로가기로 돌아오게 한다.
            if (navTarget == "report") {
                navController.navigate("report")
                onTargetConsumed()
                return@LaunchedEffect
            }
            if (navTarget == null || bottomNavItems.none { it.route == navTarget }) return@LaunchedEffect
            navController.navigate(navTarget) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onTargetConsumed()
        }
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToSchedule = {
                        navController.navigate(Screen.Schedule.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToBible = {
                        navController.navigate(Screen.Bible.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSearch = { navController.navigate("search") },
                    onNavigateToReport = { navController.navigate("report") },
                    authViewModel = authViewModel,
                )
            }
            composable(Screen.Schedule.route) { ScheduleScreen() }
            composable(Screen.Finance.route)  { FinanceScreen() }
            composable(Screen.Bible.route)    { BibleScreen() }
            composable("report") {
                ReportScreen(onBack = { navController.popBackStack() })
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToSchedule = {
                        navController.navigate(Screen.Schedule.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToFinance = {
                        navController.navigate(Screen.Finance.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}
