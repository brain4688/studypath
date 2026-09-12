package com.studypath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.studypath.app.ui.chat.ChatScreen
import com.studypath.app.ui.chat.ChatViewModel
import com.studypath.app.ui.home.HomeScreen
import com.studypath.app.ui.home.HomeViewModel
import com.studypath.app.ui.plan.PlanDetailScreen
import com.studypath.app.ui.plan.PlanDetailViewModel
import com.studypath.app.ui.settings.SettingsScreen
import com.studypath.app.ui.settings.SettingsViewModel
import com.studypath.app.ui.theme.StudyPathTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyPathTheme { AppNavGraph() }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PLAN_DETAIL = "plan/{planId}"
    fun planDetail(planId: Long) = "plan/$planId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(container: AppContainer = (LocalContext.current.applicationContext as StudyApp).container) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showTopBar = currentRoute in setOf(Routes.HOME, Routes.SETTINGS, Routes.CHAT)

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            when (currentRoute) {
                                Routes.SETTINGS -> "模型设置"
                                Routes.CHAT -> "AI 学习规划师"
                                else -> "StudyPath 智学规划"
                            }
                        )
                    },
                    navigationIcon = {
                        if (currentRoute != Routes.HOME) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(initializer = { HomeViewModel(container.repository) })
                HomeScreen(
                    viewModel = vm,
                    onNewPlan = { navController.navigate(Routes.CHAT) },
                    onOpenPlan = { navController.navigate(Routes.planDetail(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CHAT) {
                val vm: ChatViewModel = viewModel(initializer = {
                    ChatViewModel(container.repository, container.aiClient)
                })
                ChatScreen(
                    viewModel = vm,
                    onCreated = { planId ->
                        navController.popBackStack()
                        navController.navigate(Routes.planDetail(planId))
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                Routes.PLAN_DETAIL,
                arguments = listOf(navArgument("planId") { type = androidx.navigation.NavType.LongType }),
            ) { entry ->
                val planId = entry.arguments?.getLong("planId") ?: 0L
                val vm: PlanDetailViewModel = viewModel(
                    key = "plan_$planId",
                    initializer = { PlanDetailViewModel(planId, container.repository, container.aiClient) },
                )
                PlanDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(initializer = {
                    SettingsViewModel(container.repository, container.aiClient)
                })
                SettingsScreen(viewModel = vm)
            }
        }
    }
}
