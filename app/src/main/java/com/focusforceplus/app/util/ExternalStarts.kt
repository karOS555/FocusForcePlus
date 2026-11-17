package com.focusforceplus.app.util

import android.content.Context
import android.net.Uri
import com.focusforceplus.app.compliance.clampTodoLimits
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.ListRepository
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.service.FocusForegroundService
import com.focusforceplus.app.service.FocusSessionRegistry
import com.focusforceplus.app.service.RoutineForegroundService
import com.focusforceplus.app.ui.navigation.AppModule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** What a start link asks for, parsed and checked, before anything happens. */
sealed interface StartLink {
    data class QuickFocus(val minutes: Int, val dnd: Boolean) : StartLink
    data class FocusSession(val name: String) : StartLink
    data class Routine(val name: String) : StartLink
    data class AddTodo(val title: String, val priority: Int) : StartLink
    data class AddToList(val list: String, val items: List<String>) : StartLink

    companion object {
        const val SCHEME = "focusforceplus"

        /**
         * Reads a link like `focusforceplus://focus?minutes=25`. Anything that would
         * stop, pause or cancel something is not part of the grammar at all: links
         * only start and add. Returns null for anything it does not understand.
         */
        fun parse(uri: Uri): StartLink? = parse(uri.scheme, uri.host, uri::getQueryParameter)

        fun parse(scheme: String?, host: String?, param: (String) -> String?): StartLink? {
            if (scheme?.lowercase() != SCHEME) return null
            fun text(name: String) = param(name)?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
            return when (host?.lowercase()) {
                "focus" -> text("session")?.let { FocusSession(it) }
                    ?: QuickFocus(
                        minutes = (text("minutes")?.toIntOrNull() ?: 25).coerceIn(1, 240),
                        dnd = text("dnd") in setOf("1", "true", "yes", "on"),
                    )
                "routine" -> text("name")?.let { Routine(it) }
                "todo" -> text("title")?.let { title ->
                    AddTodo(
                        title = title,
                        priority = when (text("priority")?.lowercase()) {
                            "low", "0" -> 0
                            "high", "2" -> 2
                            else -> 1
                        },
                    )
                }
                "list" -> {
                    val list = text("name")
                    val items = param("item")?.split(',', '\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    if (list != null && !items.isNullOrEmpty()) AddToList(list, items.take(50)) else null
                }
                else -> null
            }
        }
    }
}

/**
 * Carries out start links from other apps, automation tools and NFC tags.
 *
 * Two rules keep this from becoming a remote control for the user's phone: it is
 * off until the user switches it on, and it can only start or add things. A
 * session started this way ends like any other, in the app and behind its speed
 * bump.
 */
@Singleton
class ExternalStarts @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val navRepository: NavRepository,
    private val focusRepository: FocusRepository,
    private val routineRepository: RoutineRepository,
    private val listRepository: ListRepository,
    private val focusRegistry: FocusSessionRegistry,
    private val routineRegistry: ActiveRoutineRegistry,
    private val todoEngine: TodoReminderEngine,
) {
    sealed interface Result {
        data class Done(val message: String) : Result
        data class Refused(val message: String) : Result
        data object Disabled : Result
    }

    suspend fun allowed(): Boolean = settings.externalStartsAllowed.first()

    suspend fun run(link: StartLink): Result {
        if (!allowed()) return Result.Disabled
        return when (link) {
            is StartLink.QuickFocus -> startFocus {
                FocusForegroundService.start(
                    context = context,
                    sessionId = 0L,
                    name = "Quick focus",
                    durationMinutes = link.minutes,
                    enableDnd = link.dnd,
                    blockNotifications = false,
                    blocksApps = true,
                    invincible = false,
                )
                "Focus started for ${link.minutes} min."
            }
            is StartLink.FocusSession -> {
                val session = focusRepository.getAllSessions().first()
                    .firstOrNull { it.name.equals(link.name, ignoreCase = true) }
                    ?: return Result.Refused("No focus session called \"${link.name}\".")
                startFocus {
                    FocusForegroundService.start(
                        context = context,
                        sessionId = session.id,
                        name = session.name,
                        durationMinutes = session.durationMinutes,
                        enableDnd = session.enableDnd,
                        blockNotifications = session.blockNotifications,
                        blocksApps = session.appBlockerEnabled,
                        invincible = session.invincibleMode,
                        type = session.type,
                        blockedGroupsCsv = session.blockedGroupsCsv,
                        iconKey = session.iconKey,
                    )
                    "\"${session.name}\" started."
                }
            }
            is StartLink.Routine -> {
                if (!navRepository.isModuleOn(AppModule.ROUTINES)) return Result.Refused("Routines are switched off.")
                if (routineRegistry.activeSession.value != null) return Result.Refused("A routine is already running.")
                val routine = routineRepository.getAllRoutines().first()
                    .firstOrNull { it.name.equals(link.name, ignoreCase = true) }
                    ?: return Result.Refused("No routine called \"${link.name}\".")
                RoutineForegroundService.start(context, routine.id)
                Result.Done("\"${routine.name}\" started.")
            }
            is StartLink.AddTodo -> {
                if (!navRepository.isModuleOn(AppModule.TODOS)) return Result.Refused("Todos are switched off.")
                addTodo(link.title, null, link.priority)
                Result.Done("Todo added.")
            }
            is StartLink.AddToList -> {
                if (!navRepository.isModuleOn(AppModule.LISTS)) return Result.Refused("Lists are switched off.")
                val list = listRepository.getListsOnce().firstOrNull { it.name.equals(link.list, ignoreCase = true) }
                    ?: return Result.Refused("No list called \"${link.list}\".")
                link.items.forEach { listRepository.addItem(list.id, it) }
                Result.Done(if (link.items.size == 1) "Added to ${list.name}." else "${link.items.size} items added to ${list.name}.")
            }
        }
    }

    /** Also used by sharing, which needs no switch: the user picks the target there. */
    suspend fun addTodo(title: String, description: String?, priority: Int): Long {
        val limits = clampTodoLimits(priority, 2, 1)
        return todoEngine.save(
            TodoEntity(
                title = title.trim().take(200),
                description = description?.trim()?.ifBlank { null },
                priority = priority,
                maxSnoozeCount = limits.maxSnoozeCount,
                maxRescheduleCount = limits.maxRescheduleCount,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun startFocus(start: () -> String): Result {
        if (!navRepository.isModuleOn(AppModule.FOCUS)) return Result.Refused("Focus is switched off.")
        if (focusRegistry.activeSession.value != null) return Result.Refused("A focus session is already running.")
        return Result.Done(start())
    }
}
