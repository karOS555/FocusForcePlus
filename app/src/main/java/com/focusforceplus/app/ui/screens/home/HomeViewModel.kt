package com.focusforceplus.app.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.service.ActiveFocusSession
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.service.FocusSessionRegistry
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.TodoReminderEngine
import com.focusforceplus.app.util.UsageTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.focusforceplus.app.compliance.dayCodeFor
import com.focusforceplus.app.compliance.isRuleLocked
import com.focusforceplus.app.compliance.isSleepWindowActive
import com.focusforceplus.app.data.repository.SleepSchedule
import java.util.Calendar
import javax.inject.Inject

/** A routine as displayed on the dashboard, with its status for today. */
data class TodayRoutine(
    val routine: RoutineEntity,
    val status: RoutineTodayStatus,
    /** When today's run was finished, if it was. */
    val doneAt: Long? = null,
    val totalMinutes: Int = 0,
)

enum class RoutineTodayStatus { RUNNING, DONE_TODAY, UPCOMING, EARLIER_TODAY }

/** One thing on today's timeline, whatever module it comes from. */
sealed interface TodayItem {
    /** When it happens; what is overdue or missed sorts by its old time, so it comes first. */
    val at: Long

    data class Routine(val today: TodayRoutine, override val at: Long) : TodayItem
    data class Todo(val todo: TodoEntity, val overdue: Boolean, override val at: Long) : TodayItem
    data class Event(val event: com.focusforceplus.app.data.calendar.CalendarEvent, override val at: Long) : TodayItem
}

/** The day at a glance: what is left, what is next, how far along it is. */
data class TodayPlan(
    /** Still ahead today, overdue and missed things first. */
    val items: List<TodayItem> = emptyList(),
    /** The next thing with a time that has not started yet (or a routine running now). */
    val next: TodayItem? = null,
    /** Open todos without a date, most important first. */
    val anytime: List<TodoEntity> = emptyList(),
    val doneToday: Int = 0,
    /** Done today plus what is still planned for today (routines and todos). */
    val plannedToday: Int = 0,
)

data class UsageStats(
    val permissionGranted: Boolean = false,
    val totalScreenTimeMinutes: Int = 0,
    val topBlockedAppName: String? = null,
    val topBlockedAppMinutes: Int = 0,
)

data class HomeUiState(
    val todayRoutines: List<TodayRoutine> = emptyList(),
    val urgentTodos: List<TodoEntity> = emptyList(),
    val openTodoCount: Int = 0,
    val activeRoutineId: Long? = null,
    val activeFocusSession: ActiveFocusSession? = null,
    val blockerEnabled: Boolean = false,
    val blockedAppCount: Int = 0,
    val blockerGroupCount: Int = 0,
    /** False while the accessibility service is off — blocking cannot work yet. */
    val blockerReady: Boolean = true,
    val usage: UsageStats = UsageStats(),
    val sleep: SleepSchedule = SleepSchedule(),
    val sleepWindowActive: Boolean = false,
    val message: String? = null,
    // Added with the reworked dashboard
    val focusedMinutesToday: Int = 0,
    val lists: List<com.focusforceplus.app.ui.screens.lists.ListRowState> = emptyList(),
    val nav: com.focusforceplus.app.ui.navigation.NavConfig = com.focusforceplus.app.ui.navigation.NavConfig(),
    val guards: com.focusforceplus.app.compliance.GuardSettings = com.focusforceplus.app.compliance.GuardSettings(),
) {
    fun isOn(module: com.focusforceplus.app.ui.navigation.AppModule): Boolean = nav.isOn(module)
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    routineRepository: RoutineRepository,
    private val todoRepository: TodoRepository,
    private val blockerRepository: BlockerRepository,
    private val settingsRepository: com.focusforceplus.app.data.repository.SettingsRepository,
    private val sleepRepository: com.focusforceplus.app.data.repository.SleepRepository,
    routineRegistry: ActiveRoutineRegistry,
    focusRegistry: FocusSessionRegistry,
    private val usageTracker: UsageTracker,
    private val todoReminders: TodoReminderEngine,
    private val sleepAlarmHelper: com.focusforceplus.app.util.SleepAlarmHelper,
    private val tamperGuard: com.focusforceplus.app.compliance.TamperProtectionGuard,
    focusRepository: com.focusforceplus.app.data.repository.FocusRepository,
    listRepository: com.focusforceplus.app.data.repository.ListRepository,
    navRepository: com.focusforceplus.app.data.repository.NavRepository,
    private val calendarRepository: com.focusforceplus.app.data.calendar.CalendarRepository,
) : ViewModel() {

    /** Events still ahead today, without the todos FocusForce+ put into the calendar itself. */
    val calendarToday: StateFlow<List<com.focusforceplus.app.data.calendar.CalendarEvent>> = combine(
        settingsRepository.calendarOnHome,
        settingsRepository.calendarIds,
        todoRepository.observeCalendarCandidates(),
        calendarRepository.changes(),
        kotlinx.coroutines.flow.flow { while (true) { emit(Unit); kotlinx.coroutines.delay(60_000L) } },
    ) { onHome, ids, todos, _, _ ->
        if (!onHome || !calendarRepository.canRead()) return@combine emptyList()
        val own = todos.mapNotNull { it.calendarEventId }.toSet()
        val now = System.currentTimeMillis()
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        calendarRepository.events(now, endOfDay, ids).filter { it.eventId !in own }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _usage = MutableStateFlow(UsageStats())
    private val _message = MutableStateFlow<String?>(null)
    private val _blockerReady = MutableStateFlow(true)

    /** Blocker rules, groups and the master switch, bundled to stay under combine's arity. */
    private data class BlockerSnapshot(
        val ruleCount: Int,
        val groupCount: Int,
        val enabled: Boolean,
    )

    /** What the reworked dashboard shows on top of the original state. */
    private data class Extras(
        val focusedMinutes: Int,
        val lists: List<com.focusforceplus.app.ui.screens.lists.ListRowState>,
        val nav: com.focusforceplus.app.ui.navigation.NavConfig,
        val guards: com.focusforceplus.app.compliance.GuardSettings,
    )

    private val extras = combine(
        focusRepository.getCompletionsSince(startOfTodayMillis()),
        listRepository.getLists(),
        listRepository.getCounts(),
        navRepository.config,
        settingsRepository.guardSettings,
    ) { completions, lists, counts, nav, guards ->
        val byList = counts.associateBy { it.listId }
        Extras(
            focusedMinutes = completions.filter { it.completedAt >= startOfTodayMillis() }.sumOf { it.focusedMinutes },
            lists = lists.map { list ->
                val c = byList[list.id]
                com.focusforceplus.app.ui.screens.lists.ListRowState(list, c?.openCount ?: 0, c?.totalCount ?: 0)
            },
            nav = nav,
            guards = guards,
        )
    }

    private val base: kotlinx.coroutines.flow.Flow<HomeUiState> = combine(
        combine(
            routineRepository.getAllRoutines(),
            routineRegistry.activeRoutineId,
            routineRepository.getCompletionsSince(startOfTodayMillis()),
            routineRepository.getAllTasks(),
        ) { routines, activeId, completions, tasks ->
            val doneAt = completions
                .filter { it.completedAt >= startOfTodayMillis() }
                .groupBy { it.routineId }
                .mapValues { (_, list) -> list.maxOf { it.completedAt } }
            val minutes = tasks.groupBy { it.routineId }.mapValues { (_, list) -> list.sumOf { it.durationMinutes } }
            buildTodayRoutines(routines, activeId, doneAt, minutes) to activeId
        },
        combine(todoRepository.getUncompletedTodos(), focusRegistry.activeSession, ::Pair),
        combine(
            blockerRepository.getBlockedApps(),
            blockerRepository.getAllGroups(),
            settingsRepository.blockerEnabled,
        ) { blocked, groups, enabled -> BlockerSnapshot(blocked.size, groups.size, enabled) },
        combine(sleepRepository.schedule, _blockerReady, ::Pair),
        combine(_usage, _message, ::Pair),
    ) { (todayRoutines, activeId), todosAndFocus, blocker, sleepAndReady, usageAndMessage ->
        val (openTodos, focusSession) = todosAndFocus
        val (sleep, blockerReady) = sleepAndReady
        val (usage, message) = usageAndMessage
        HomeUiState(
            todayRoutines = todayRoutines,
            urgentTodos = pickUrgent(openTodos),
            openTodoCount = openTodos.size,
            activeRoutineId = activeId,
            activeFocusSession = focusSession,
            blockerEnabled = blocker.enabled,
            blockedAppCount = blocker.ruleCount,
            blockerGroupCount = blocker.groupCount,
            blockerReady = blockerReady,
            usage = usage,
            sleep = sleep,
            sleepWindowActive = sleepWindowActive(sleep),
            message = message,
        )
    }

    /** Routines, todos and calendar events of today, in one timeline. */
    val today: StateFlow<TodayPlan> by lazy {
        combine(
            uiState,
            todoRepository.getUncompletedTodos(),
            todoRepository.getCompletionTimesSince(startOfTodayMillis()),
            calendarToday,
            kotlinx.coroutines.flow.flow { while (true) { emit(Unit); kotlinx.coroutines.delay(60_000L) } },
        ) { state, open, completedTimes, events, _ ->
            planToday(state, open, completedTimes, events)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayPlan())
    }

    private fun planToday(
        state: HomeUiState,
        open: List<TodoEntity>,
        completedTimes: List<Long>,
        events: List<com.focusforceplus.app.data.calendar.CalendarEvent>,
    ): TodayPlan {
        val now = System.currentTimeMillis()
        val start = startOfTodayMillis()
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val routines = if (state.isOn(com.focusforceplus.app.ui.navigation.AppModule.ROUTINES)) state.todayRoutines else emptyList()
        val todosOn = state.isOn(com.focusforceplus.app.ui.navigation.AppModule.TODOS)

        val items = buildList {
            routines.filter { it.status != RoutineTodayStatus.DONE_TODAY }.forEach { today ->
                val at = start + (today.routine.startTimeHour * 60 + today.routine.startTimeMinute) * 60_000L
                add(TodayItem.Routine(today, if (today.status == RoutineTodayStatus.RUNNING) minOf(at, now) else at))
            }
            if (todosOn) {
                open.filter { it.dueDateTime != null && it.dueDateTime < end }.forEach { todo ->
                    val due = todo.dueDateTime!!
                    add(TodayItem.Todo(todo, overdue = due < now, at = due))
                }
            }
            events.forEach { add(TodayItem.Event(it, if (it.allDay) start else it.begin)) }
        }.sortedWith(compareBy({ !(it is TodayItem.Event && it.event.allDay) }, { it.at }))

        val next = items.firstOrNull { item ->
            when (item) {
                is TodayItem.Routine -> item.today.status == RoutineTodayStatus.RUNNING ||
                    item.today.status == RoutineTodayStatus.UPCOMING
                is TodayItem.Todo -> !item.overdue
                is TodayItem.Event -> !item.event.allDay && item.event.end > now
            }
        }
        val doneRoutines = routines.count { it.status == RoutineTodayStatus.DONE_TODAY }
        val doneTodos = if (todosOn) completedTimes.count { it >= start } else 0
        val leftToday = items.count { item ->
            when (item) {
                is TodayItem.Routine -> true
                is TodayItem.Todo -> item.todo.dueDateTime!! >= start
                is TodayItem.Event -> false
            }
        }
        return TodayPlan(
            items = items,
            next = next,
            anytime = if (todosOn) open.filter { it.dueDateTime == null }
                .sortedWith(compareByDescending<TodoEntity> { it.priority }.thenBy { it.createdAt })
                .take(3) else emptyList(),
            doneToday = doneRoutines + doneTodos,
            plannedToday = doneRoutines + doneTodos + leftToday,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(base, extras) { state, more ->
        state.copy(
            focusedMinutesToday = more.focusedMinutes,
            lists = more.lists,
            nav = more.nav,
            guards = more.guards,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refreshUsage()
    }

    fun clearMessage() = _message.update { null }

    // ── Mode switches ─────────────────────────────────────────────────────────

    /**
     * Master switch for the blocker, straight from the dashboard. Turning it OFF
     * follows the same rules as in blocker settings: refused while an Invincible
     * rule is actively blocking, and Tamper-Protection gated when invincible rules
     * exist at all (Golden Rule #11 — no side door around a lock).
     */
    fun setBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                val cal = Calendar.getInstance()
                val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val rules = blockerRepository.getAllAppsOnce()
                val lockedCount = rules.count { isRuleLocked(it, minutesOfDay) }
                if (lockedCount > 0) {
                    _message.value = "Cannot turn blocking off: $lockedCount rule(s) are " +
                        "locked by Invincible Mode right now."
                    return@launch
                }
                if (rules.any { it.invincibleMode && it.isBlocked }) {
                    val verdict = tamperGuard.checkProtectedChange()
                    if (!verdict.allowed) {
                        _message.value = verdict.message
                        return@launch
                    }
                }
            }
            settingsRepository.saveBlockerEnabled(enabled)
        }
    }

    /** Sleep schedule on/off. Locked while an invincible night is running. */
    fun setSleepEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = sleepRepository.schedule.first()
            if (current.enabled && !enabled && current.invincibleMode && sleepWindowActive(current)) {
                _message.value = "Sleep mode is locked by Invincible Mode until wake-up."
                return@launch
            }
            val updated = current.copy(enabled = enabled)
            sleepRepository.saveSchedule(updated)
            sleepAlarmHelper.scheduleAll(updated)
        }
    }

    private fun sleepWindowActive(schedule: SleepSchedule): Boolean {
        if (!schedule.enabled) return false
        val cal = Calendar.getInstance()
        val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return isSleepWindowActive(
            schedule.bedtimeMinutes, schedule.wakeMinutes, schedule.activeDaysCsv,
            minutesOfDay, dayCodeFor(cal.get(Calendar.DAY_OF_WEEK)),
        )
    }

    /** Called on screen resume — screen time and permissions change constantly. */
    fun refreshUsage() {
        viewModelScope.launch {
            _blockerReady.value = PermissionHelper.isAccessibilityServiceEnabled(context)
            val granted = PermissionHelper.hasUsageStatsPermission(context)
            if (!granted) {
                _usage.value = UsageStats(permissionGranted = false)
                return@launch
            }
            val usage = usageTracker.syncBlockedAppUsage()
            val total = usageTracker.totalScreenTimeMinutesToday()
            // Most-used app among those with an enabled blocking rule.
            val topBlocked = blockerRepository.getAllAppsOnce()
                .filter { it.isBlocked }
                .map { rule -> rule.appName to (usage[rule.packageName] ?: 0) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
            _usage.value = UsageStats(
                permissionGranted = true,
                totalScreenTimeMinutes = total,
                topBlockedAppName = topBlocked?.first,
                topBlockedAppMinutes = topBlocked?.second ?: 0,
            )
        }
    }

    /** Marks a todo done straight from the dashboard. */
    fun completeTodo(todo: TodoEntity) {
        viewModelScope.launch { todoReminders.complete(todo.id) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** German day key for today, matching RoutineEntity.activeDays storage. */
    private fun todayRoutineKey(): String = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "MO"
        Calendar.TUESDAY -> "DI"
        Calendar.WEDNESDAY -> "MI"
        Calendar.THURSDAY -> "DO"
        Calendar.FRIDAY -> "FR"
        Calendar.SATURDAY -> "SA"
        else -> "SO"
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun buildTodayRoutines(
        routines: List<RoutineEntity>,
        activeId: Long?,
        doneAt: Map<Long, Long>,
        minutes: Map<Long, Int>,
    ): List<TodayRoutine> {
        val todayKey = todayRoutineKey()
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return routines
            .filter { it.isActive && it.activeDays.split(",").contains(todayKey) }
            .map { routine ->
                val startMinutes = routine.startTimeHour * 60 + routine.startTimeMinute
                val status = when {
                    routine.id == activeId -> RoutineTodayStatus.RUNNING
                    routine.id in doneAt -> RoutineTodayStatus.DONE_TODAY
                    startMinutes >= nowMinutes -> RoutineTodayStatus.UPCOMING
                    else -> RoutineTodayStatus.EARLIER_TODAY
                }
                TodayRoutine(routine, status, doneAt[routine.id], minutes[routine.id] ?: 0)
            }
            .sortedWith(
                compareBy(
                    { it.status != RoutineTodayStatus.RUNNING },
                    { it.status == RoutineTodayStatus.DONE_TODAY }, // done last
                    { it.routine.startTimeHour * 60 + it.routine.startTimeMinute },
                )
            )
    }

    private fun pickUrgent(open: List<TodoEntity>): List<TodoEntity> {
        val now = System.currentTimeMillis()
        return open.sortedWith(
            compareBy(
                { todo ->
                    when {
                        todo.dueDateTime != null && todo.dueDateTime < now -> 0 // overdue first
                        todo.dueDateTime != null -> 1                          // dated next
                        else -> 2                                              // undated last
                    }
                },
                { it.dueDateTime ?: Long.MAX_VALUE },
                { -it.priority },
            )
        ).take(5)
    }
}
