package com.focusforceplus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.focusforceplus.app.ui.screen.BlockerScreen
import com.focusforceplus.app.ui.screen.FocusScreen
import com.focusforceplus.app.ui.screen.HomeScreen
import com.focusforceplus.app.ui.screen.TodosScreen
import com.focusforceplus.app.ui.screens.routine.ActiveRoutineScreen
import com.focusforceplus.app.ui.screens.routine.CreateRoutineScreen
import com.focusforceplus.app.ui.screens.routine.RoutineListScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) { HomeScreen() }

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

        // ── Other tabs ────────────────────────────────────────────────────────
        composable(Screen.Todos.route)   { TodosScreen() }
        composable(Screen.Blocker.route) { BlockerScreen() }
        composable(Screen.Focus.route)   { FocusScreen() }
    }
}
