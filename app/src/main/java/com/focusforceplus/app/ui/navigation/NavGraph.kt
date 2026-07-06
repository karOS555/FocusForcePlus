package com.focusforceplus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.focusforceplus.app.ui.screens.blocker.BlockerListScreen
import com.focusforceplus.app.ui.screens.blocker.BlockerSettingsScreen
import com.focusforceplus.app.ui.screens.blocker.DisclosureScreen
import com.focusforceplus.app.ui.screens.focus.ActiveFocusScreen
import com.focusforceplus.app.ui.screens.focus.CreateFocusScreen
import com.focusforceplus.app.ui.screens.focus.FocusListScreen
import com.focusforceplus.app.ui.screens.home.HomeScreen
import com.focusforceplus.app.ui.screens.onboarding.OnboardingScreen
import com.focusforceplus.app.ui.screens.routine.ActiveRoutineScreen
import com.focusforceplus.app.ui.screens.routine.CreateRoutineScreen
import com.focusforceplus.app.ui.screens.routine.RoutineListScreen
import com.focusforceplus.app.ui.screens.settings.SettingsScreen
import com.focusforceplus.app.ui.screens.stats.StatsScreen
import com.focusforceplus.app.ui.screens.todo.CreateTodoScreen
import com.focusforceplus.app.ui.screens.todo.TodoListScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            fun goToTab(route: String) {
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            HomeScreen(
                onStartRoutine       = { id -> navController.navigate(RoutineRoutes.active(id)) },
                onOpenActiveRoutine  = { id -> navController.navigate(RoutineRoutes.active(id)) },
                onOpenActiveFocus    = { id -> navController.navigate(FocusRoutes.active(id)) },
                onGoToRoutines       = { goToTab(RoutineRoutes.LIST) },
                onGoToTodos          = { goToTab(TodoRoutes.LIST) },
                onGoToFocus          = { goToTab(FocusRoutes.LIST) },
                onGoToBlocker        = { goToTab(BlockerRoutes.LIST) },
                onNewTodo            = { navController.navigate(TodoRoutes.CREATE) },
                onNewRoutine         = { navController.navigate(RoutineRoutes.CREATE) },
                onOpenStats          = { navController.navigate(StatsRoutes.STATS) },
            )
        }

        // ── Statistics ────────────────────────────────────────────────────────
        composable(StatsRoutes.STATS) {
            StatsScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Onboarding (first launch only) ────────────────────────────────────
        composable(OnboardingRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinish = { createFirstRoutine ->
                    val target = if (createFirstRoutine) RoutineRoutes.CREATE else Screen.Home.route
                    navController.navigate(target) {
                        popUpTo(OnboardingRoutes.ONBOARDING) { inclusive = true }
                    }
                },
                onOpenDisclosure = { type ->
                    navController.navigate(BlockerRoutes.disclosure(type))
                },
            )
        }

        // ── Routines ──────────────────────────────────────────────────────────
        composable(RoutineRoutes.LIST) {
            RoutineListScreen(
                onCreateRoutine = { navController.navigate(RoutineRoutes.CREATE) },
                onEditRoutine   = { id -> navController.navigate(RoutineRoutes.edit(id)) },
                onStartRoutine  = { id -> navController.navigate(RoutineRoutes.active(id)) },
            )
        }

        composable(RoutineRoutes.CREATE) {
            CreateRoutineScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = RoutineRoutes.EDIT,
            arguments = listOf(navArgument("routineId") { type = NavType.LongType }),
        ) {
            CreateRoutineScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = RoutineRoutes.ACTIVE,
            arguments = listOf(navArgument("routineId") { type = NavType.LongType }),
        ) {
            ActiveRoutineScreen(
                onBack = {
                    navController.navigate(RoutineRoutes.LIST) {
                        popUpTo(RoutineRoutes.LIST) { inclusive = true }
                    }
                },
            )
        }

        // ── Todos ─────────────────────────────────────────────────────────────
        composable(TodoRoutes.LIST) {
            TodoListScreen(
                onCreateTodo = { navController.navigate(TodoRoutes.CREATE) },
                onEditTodo   = { id -> navController.navigate(TodoRoutes.edit(id)) },
            )
        }

        composable(TodoRoutes.CREATE) {
            CreateTodoScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = TodoRoutes.EDIT,
            arguments = listOf(navArgument("todoId") { type = NavType.LongType }),
        ) {
            CreateTodoScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── App Blocker ───────────────────────────────────────────────────────
        composable(BlockerRoutes.LIST) {
            BlockerListScreen(
                onOpenSettings   = { navController.navigate(BlockerRoutes.SETTINGS) },
                onOpenDisclosure = { type -> navController.navigate(BlockerRoutes.disclosure(type)) },
            )
        }

        composable(BlockerRoutes.SETTINGS) {
            BlockerSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = BlockerRoutes.DISCLOSURE,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { backStackEntry ->
            DisclosureScreen(
                type = backStackEntry.arguments?.getString("type") ?: "",
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── Focus Mode ────────────────────────────────────────────────────────
        composable(FocusRoutes.LIST) {
            FocusListScreen(
                onCreateSession = { navController.navigate(FocusRoutes.CREATE) },
                onEditSession   = { id -> navController.navigate(FocusRoutes.edit(id)) },
                onOpenActive    = { id -> navController.navigate(FocusRoutes.active(id)) },
            )
        }

        composable(FocusRoutes.CREATE) {
            CreateFocusScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = FocusRoutes.EDIT,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            CreateFocusScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = FocusRoutes.ACTIVE,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            ActiveFocusScreen(
                onBack = {
                    navController.navigate(FocusRoutes.LIST) {
                        popUpTo(FocusRoutes.LIST) { inclusive = true }
                    }
                },
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(SettingsRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenBlockerSettings = { navController.navigate(BlockerRoutes.SETTINGS) },
                onOpenDisclosure = { type -> navController.navigate(BlockerRoutes.disclosure(type)) },
                onReplayOnboarding = { navController.navigate(OnboardingRoutes.ONBOARDING) },
            )
        }
    }
}
