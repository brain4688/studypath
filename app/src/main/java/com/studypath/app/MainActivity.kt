package com.studypath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.studypath.app.ui.chat.ChatScreen
import com.studypath.app.ui.create.CreatePlanScreen
import com.studypath.app.ui.home.HomeScreen
import com.studypath.app.ui.mine.MineScreen
import com.studypath.app.ui.plan.PlanDetailScreen
import com.studypath.app.ui.settings.SettingsScreen
import com.studypath.app.ui.theme.StudyPathTheme
import com.studypath.app.ui.today.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyPathTheme { StudyPathApp() }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val TODAY = "today"
    const val MINE = "mine"
    const val CHAT = "chat"
    const val CREATE = "create"
    const val SETTINGS = "settings"
    const val PLAN_DETAIL = "plan/{planId}"
    fun planDetail(planId: Long) = "plan/$planId"
}

private data class BottomDest(
    val route: String,
    val label: String,
    val filled: androidx.compose.ui.graphics.vector.ImageVector,
    val outlined: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomDests = listOf(
    BottomDest(Routes.HOME, "主页", Icons.Filled.Home, Icons.Outlined.Home),
    BottomDest(Routes.TODAY, "今日任务", Icons.Filled.EventNote, Icons.Outlined.EventNote),
    BottomDest(Routes.MINE, "我的", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun StudyPathApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = (context.applicationContext as StudyApp).container
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in bottomDests.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDests.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) dest.filled else dest.outlined,
                                    contentDescription = dest.label,
                                )
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                val vm: com.studypath.app.ui.home.HomeViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        initializer = { com.studypath.app.ui.home.HomeViewModel(container.repository) },
                    )
                HomeScreen(
                    viewModel = vm,
                    onNewPlan = { navController.navigate(Routes.CHAT) },
                    onCreatePlan = { navController.navigate(Routes.CREATE) },
                    onOpenPlan = { navController.navigate(Routes.planDetail(it)) },
                )
            }
            composable(Routes.TODAY) {
                val vm: com.studypath.app.ui.today.TodayViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        initializer = { com.studypath.app.ui.today.TodayViewModel(container.repository, container.aiClient) },
                    )
                TodayScreen(
                    viewModel = vm,
                    onOpenPlan = { navController.navigate(Routes.planDetail(it)) },
                )
            }
            composable(Routes.MINE) {
                val vm: com.studypath.app.ui.mine.MineViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(initializer = { com.studypath.app.ui.mine.MineViewModel() })
                MineScreen(
                    viewModel = vm,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CHAT) {
                val vm: com.studypath.app.ui.chat.ChatViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        initializer = { com.studypath.app.ui.chat.ChatViewModel(container.repository, container.aiClient) },
                    )
                ChatScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onCreated = { planId ->
                        navController.popBackStack()
                        navController.navigate(Routes.planDetail(planId))
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CREATE) {
                val vm: com.studypath.app.ui.create.CreatePlanViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        initializer = { com.studypath.app.ui.create.CreatePlanViewModel(container.repository) },
                    )
                CreatePlanScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onCreated = { planId ->
                        navController.popBackStack()
                        navController.navigate(Routes.planDetail(planId))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                val vm: com.studypath.app.ui.settings.SettingsViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        initializer = { com.studypath.app.ui.settings.SettingsViewModel(container.repository, container.aiClient) },
                    )
                SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(
                Routes.PLAN_DETAIL,
                arguments = listOf(navArgument("planId") { type = NavType.LongType }),
            ) { entry ->
                val planId = entry.arguments?.getLong("planId") ?: 0L
                val vm: com.studypath.app.ui.plan.PlanDetailViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        key = "plan_$planId",
                        initializer = {
                            com.studypath.app.ui.plan.PlanDetailViewModel(planId, container.repository, container.aiClient)
                        },
                    )
                PlanDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }
        }
    }
}
