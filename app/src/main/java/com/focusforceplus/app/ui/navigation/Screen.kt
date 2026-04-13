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
        label = "Routines",
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
        label = "Focus",
        icon = Icons.Filled.SelfImprovement
    )
}

// Sub-screens (not in bottom nav)
object RoutineRoutes {
    const val LIST           = "routines"
    const val CREATE         = "routine/create"
    const val EDIT           = "routine/edit/{routineId}"
    const val ACTIVE         = "routine/active/{routineId}"

    fun edit(routineId: Long)   = "routine/edit/$routineId"
    fun active(routineId: Long) = "routine/active/$routineId"
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Routines,
    Screen.Todos,
    Screen.Blocker,
    Screen.Focus,
)
