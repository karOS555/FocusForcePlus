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
import com.focusforceplus.app.service.TodoAlarmForegroundService
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.TodoAlarmHelper
import com.focusforceplus.app.util.TodoNotificationHelper
import com.focusforceplus.app.util.UsageTracker
import com.focusforceplus.app.util.nextOccurrenceMillis
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
)

enum class RoutineTodayStatus { RUNNING, DONE_TODAY, UPCOMING, EARLIER_TODAY }

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
)

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
    private val todoAlarmHelper: TodoAlarmHelper,
    private val todoNotifHelper: TodoNotificationHelper,
    private val sleepAlarmHelper: com.focusforceplus.app.util.SleepAlarmHelper,
    private val tamperGuard: com.focusforceplus.app.compliance.TamperProtectionGuard,
) : ViewModel() {

    private val _usage = MutableStateFlow(UsageStats())
    private val _message = MutableStateFlow<String?>(null)
    private val _blockerReady = MutableStateFlow(true)

    /** Blocker rules, groups and the master switch, bundled to stay under combine's arity. */
    private data class BlockerSnapshot(
        val ruleCount: Int,
        val groupCount: Int,
        val enabled: Boolean,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            routineRepository.getAllRoutines(),
            routineRegistry.activeRoutineId,
            routineRepository.getCompletionsSince(startOfTodayMillis()),
        ) { routines, activeId, completions ->
            val doneToday = completions
                .filter { it.completedAt >= startOfTodayMillis() }
                .map { it.routineId }
                .toSet()
            buildTodayRoutines(routines, activeId, doneToday) to activeId
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

    /** Marks a todo done straight from the dashboard (same side effects as the list). */
    fun completeTodo(todo: TodoEntity) {
        viewModelScope.launch {
            todoRepository.markCompleted(todo.id, System.currentTimeMillis())
            todoAlarmHelper.cancelTodoAlarm(todo.id)
            todoAlarmHelper.cancelTodoSnoozeAlarm(todo.id)
            runCatching {
                context.startService(TodoAlarmForegroundService.stopIntent(context))
            }
            todoNotifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(todo.id))
            todoNotifHelper.cancelNotification(TodoNotificationHelper.todoDigestNotificationId(todo.id))
            if (todo.isRecurring && todo.recurringPattern != null && todo.dueDateTime != null) {
                val next = todo.copy(
                    id = 0L,
                    dueDateTime = nextOccurrenceMillis(todo.dueDateTime, todo.recurringPattern),
                    isCompleted = false,
                    completedAt = null,
                    snoozeCount = 0,
                    rescheduleCount = 0,
                    postponedTo = null,
                )
                val newId = todoRepository.insertTodo(next)
                todoAlarmHelper.scheduleTodoAlarm(next.copy(id = newId))
            }
        }
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
        doneTodayIds: Set<Long>,
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
                    routine.id in doneTodayIds -> RoutineTodayStatus.DONE_TODAY
                    startMinutes >= nowMinutes -> RoutineTodayStatus.UPCOMING
                    else -> RoutineTodayStatus.EARLIER_TODAY
                }
                TodayRoutine(routine, status)
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
