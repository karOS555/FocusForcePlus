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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val usage: UsageStats = UsageStats(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    routineRepository: RoutineRepository,
    private val todoRepository: TodoRepository,
    private val blockerRepository: BlockerRepository,
    settingsRepository: com.focusforceplus.app.data.repository.SettingsRepository,
    routineRegistry: ActiveRoutineRegistry,
    focusRegistry: FocusSessionRegistry,
    private val usageTracker: UsageTracker,
    private val todoAlarmHelper: TodoAlarmHelper,
    private val todoNotifHelper: TodoNotificationHelper,
) : ViewModel() {

    private val _usage = MutableStateFlow(UsageStats())

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
        todoRepository.getUncompletedTodos(),
        focusRegistry.activeSession,
        combine(
            blockerRepository.getBlockedApps(),
            settingsRepository.blockerEnabled,
        ) { blocked, enabled -> blocked.size to enabled },
        _usage,
    ) { (todayRoutines, activeId), openTodos, focusSession, (blockedCount, blockerEnabled), usage ->
        HomeUiState(
            todayRoutines = todayRoutines,
            urgentTodos = pickUrgent(openTodos),
            openTodoCount = openTodos.size,
            activeRoutineId = activeId,
            activeFocusSession = focusSession,
            blockerEnabled = blockerEnabled,
            blockedAppCount = blockedCount,
            usage = usage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refreshUsage()
    }

    /** Called on screen resume — screen time changes constantly. */
    fun refreshUsage() {
        viewModelScope.launch {
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
