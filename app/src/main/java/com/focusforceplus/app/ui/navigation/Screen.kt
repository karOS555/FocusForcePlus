package com.focusforceplus.app.ui.navigation

/**
 * Route strings of every screen that is not a top-level module. The top-level
 * routes themselves live on [AppModule].
 */
object SettingsRoutes {
    const val SETTINGS = "settings"
    const val SPEED_BUMPS = "settings/speed-bumps"
    const val INTEGRATIONS = "settings/integrations"
    const val AUTO_BACKUP = "settings/backup"
}

object OnboardingRoutes {
    const val ONBOARDING = "onboarding"
}

object StatsRoutes {
    const val STATS = "stats"
}

object SleepRoutes {
    const val SLEEP = "sleep"

    /** Create a routine from the sleep screen and link it as bedtime/morning. */
    const val CREATE_LINKED_ROUTINE = "sleep/routine/create/{linkTarget}"

    fun createLinkedRoutine(linkTarget: String) = "sleep/routine/create/$linkTarget"
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
    const val BLOCK      = "blocker/block?name={name}"

    fun disclosure(type: String) = "blocker/disclosure/$type"

    /** The block editor; no name means a new block. */
    fun block(name: String? = null) =
        if (name == null) "blocker/block" else "blocker/block?name=${android.net.Uri.encode(name)}"
}

object FocusRoutes {
    const val LIST   = "focus"
    const val CREATE = "focus/create"
    const val EDIT   = "focus/edit/{sessionId}"
    const val ACTIVE = "focus/active/{sessionId}"

    fun edit(sessionId: Long)   = "focus/edit/$sessionId"
    fun active(sessionId: Long) = "focus/active/$sessionId"
}


object ListRoutes {
    const val LIST = "lists"
    const val DETAIL = "list/{listId}"

    fun detail(listId: Long) = "list/$listId"
}

object TrashRoutes {
    const val TRASH = "trash"
}

/**
 * Screens that take over the whole display: forms and first-run flows. The
 * navigation bar is hidden on them so the action at the bottom has no competition.
 */
fun isFocusedRoute(route: String?): Boolean {
    if (route == null) return false
    return route == OnboardingRoutes.ONBOARDING ||
        route.startsWith("blocker/disclosure") ||
        route.startsWith("blocker/block") ||
        route == RoutineRoutes.CREATE || route.startsWith("routine/edit") ||
        route == TodoRoutes.CREATE || route.startsWith("todo/edit") ||
        route == FocusRoutes.CREATE || route.startsWith("focus/edit") ||
        // Running sessions get the whole screen too: the timer, nothing else.
        route.startsWith("routine/active") || route.startsWith("focus/active") ||
        // A list is typed into; the keyboard needs the room the bar would take.
        route.startsWith("list/") ||
        route.startsWith("sleep/routine/create")
}
