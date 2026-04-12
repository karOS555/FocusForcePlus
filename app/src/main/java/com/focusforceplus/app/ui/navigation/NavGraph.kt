package com.focusforceplus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.focusforceplus.app.ui.screen.BlockerScreen
import com.focusforceplus.app.ui.screen.FocusScreen
import com.focusforceplus.app.ui.screen.HomeScreen
import com.focusforceplus.app.ui.screen.RoutinesScreen
import com.focusforceplus.app.ui.screen.TodosScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route)     { HomeScreen() }
        composable(Screen.Routines.route) { RoutinesScreen() }
        composable(Screen.Todos.route)    { TodosScreen() }
        composable(Screen.Blocker.route)  { BlockerScreen() }
        composable(Screen.Focus.route)    { FocusScreen() }
    }
}
