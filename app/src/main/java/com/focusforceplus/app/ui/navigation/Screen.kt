package com.focusforceplus.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        label = "Home",
        icon = Icons.Filled.Home
    )
    data object Routines : Screen(
        route = "routines",
        label = "Routinen",
        icon = Icons.Filled.ViewDay
    )
    data object Todos : Screen(
        route = "todos",
        label = "Todos",
        icon = Icons.Filled.CheckCircle
    )
    data object Blocker : Screen(
        route = "blocker",
        label = "Blocker",
        icon = Icons.Filled.Block
    )
    data object Focus : Screen(
        route = "focus",
        label = "Fokus",
        icon = Icons.Filled.SelfImprovement
    )
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Routines,
    Screen.Todos,
    Screen.Blocker,
    Screen.Focus,
)
