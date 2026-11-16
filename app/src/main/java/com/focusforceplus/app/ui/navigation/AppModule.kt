package com.focusforceplus.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Everything that can sit in the navigation bar.
 *
 * [key] is persisted (bar order, hidden entries, modules that are switched off),
 * so it must never change once shipped.
 *
 * [isFeature] entries are real modules: besides being hidden from the bar they can
 * be turned off entirely, which also takes them off the home screen and pauses
 * their reminders. Home is always there; Stats and Settings are plain shortcuts to
 * screens that already have a button in the top bar, so they start out hidden.
 */
enum class AppModule(
    val key: String,
    val label: String,
    val route: String,
    val icon: ImageVector,
    val isFeature: Boolean,
    val shownByDefault: Boolean,
) {
    HOME("home", "Home", "home", Icons.Filled.Home, isFeature = false, shownByDefault = true),
    ROUTINES("routines", "Routines", "routines", Icons.Filled.ViewDay, isFeature = true, shownByDefault = true),
    TODOS("todos", "Todos", "todos", Icons.Filled.CheckCircle, isFeature = true, shownByDefault = true),
    LISTS("lists", "Lists", "lists", Icons.AutoMirrored.Filled.FormatListBulleted, isFeature = true, shownByDefault = true),
    BLOCKER("blocker", "Blocker", "blocker", Icons.Filled.Block, isFeature = true, shownByDefault = true),
    FOCUS("focus", "Focus", "focus", Icons.Filled.SelfImprovement, isFeature = true, shownByDefault = true),
    SLEEP("sleep", "Sleep", "sleep", Icons.Filled.Bedtime, isFeature = true, shownByDefault = true),
    STATS("stats", "Stats", "stats", Icons.Filled.BarChart, isFeature = false, shownByDefault = false),
    SETTINGS("settings", "Settings", "settings", Icons.Filled.Settings, isFeature = false, shownByDefault = false);

    /** Home can be moved, but never hidden: there has to be a way back. */
    val canHide: Boolean get() = this != HOME

    companion object {
        fun fromKey(key: String): AppModule? = entries.firstOrNull { it.key == key }

        fun fromRoute(route: String?): AppModule? = entries.firstOrNull { it.route == route }

        /** Top-level screens that share the app bar with the title in it. */
        val topLevel: Set<AppModule> = setOf(HOME, ROUTINES, TODOS, LISTS, BLOCKER, FOCUS, SLEEP)
    }
}

enum class ModuleState { SHOWN, HIDDEN, OFF }

/** The user's navigation setup: order of the bar, what is hidden, what is off. */
data class NavConfig(
    val order: List<AppModule> = AppModule.entries,
    val hidden: Set<AppModule> = AppModule.entries.filterNot { it.shownByDefault }.toSet(),
    val off: Set<AppModule> = emptySet(),
) {
    /** Entries of the bar, left to right. */
    val dock: List<AppModule> get() = order.filter { it !in hidden && it !in off }

    fun isOn(module: AppModule): Boolean = module !in off

    fun state(module: AppModule): ModuleState = when (module) {
        in off -> ModuleState.OFF
        in hidden -> ModuleState.HIDDEN
        else -> ModuleState.SHOWN
    }
}
