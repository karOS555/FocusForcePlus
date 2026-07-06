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
object SettingsRoutes {
    const val SETTINGS = "settings"
}

object OnboardingRoutes {
    const val ONBOARDING = "onboarding"
}

object StatsRoutes {
    const val STATS = "stats"
}

object RoutineRoutes {
    const val LIST           = "routines"
    const val CREATE         = "routine/create"
    const val EDIT           = "routine/edit/{routineId}"
    const val ACTIVE         = "routine/active/{routineId}"

    fun edit(routineId: Long)   = "routine/edit/$routineId"
    fun active(routineId: Long) = "routine/active/$routineId"
}

object TodoRoutes {
    const val LIST   = "todos"
    const val CREATE = "todo/create"
    const val EDIT   = "todo/edit/{todoId}"

    fun edit(todoId: Long) = "todo/edit/$todoId"
}

object BlockerRoutes {
    const val LIST       = "blocker"
    const val SETTINGS   = "blocker/settings"
    const val DISCLOSURE = "blocker/disclosure/{type}"

    fun disclosure(type: String) = "blocker/disclosure/$type"
}

object FocusRoutes {
    const val LIST   = "focus"
    const val CREATE = "focus/create"
    const val EDIT   = "focus/edit/{sessionId}"
    const val ACTIVE = "focus/active/{sessionId}"

    fun edit(sessionId: Long)   = "focus/edit/$sessionId"
    fun active(sessionId: Long) = "focus/active/$sessionId"
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Routines,
    Screen.Todos,
    Screen.Blocker,
    Screen.Focus,
)
