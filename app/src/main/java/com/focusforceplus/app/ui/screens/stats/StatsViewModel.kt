package com.focusforceplus.app.ui.screens.stats

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.ScreenTime
import com.focusforceplus.app.util.UsageTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** One bar in a weekly chart. */
data class DayStat(
    val label: String,
    val value: Int,
    val isToday: Boolean,
)

/** An app in today's screen time. */
data class AppTime(val packageName: String, val name: String, val minutes: Int)

data class StatsUiState(
    val routinesPerDay: List<DayStat> = emptyList(),
    val focusMinutesPerDay: List<DayStat> = emptyList(),
    val todosPerDay: List<DayStat> = emptyList(),
    /** Null without Usage Access. */
    val screenPerDay: List<DayStat>? = null,
    val topApps: List<AppTime> = emptyList(),
    val totalOvertimeMinutes7d: Int = 0,
    val isLoading: Boolean = true,
) {
    val screenToday: Int? get() = screenPerDay?.lastOrNull()?.value
    val focusToday: Int get() = focusMinutesPerDay.lastOrNull()?.value ?: 0
    val todosToday: Int get() = todosPerDay.lastOrNull()?.value ?: 0
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    routineRepository: RoutineRepository,
    focusRepository: FocusRepository,
    todoRepository: TodoRepository,
    private val usageTracker: UsageTracker,
) : ViewModel() {

    private val windowStart = startOfDayDaysAgo(DAYS - 1)
    private val screen = MutableStateFlow<Pair<ScreenTime?, List<AppTime>>>(null to emptyList())

    val uiState: StateFlow<StatsUiState> = combine(
        routineRepository.getCompletionsSince(windowStart),
        focusRepository.getCompletionsSince(windowStart),
        todoRepository.getCompletionTimesSince(windowStart),
        screen,
    ) { routineCompletions, focusCompletions, todoTimes, (screenTime, apps) ->
        StatsUiState(
            routinesPerDay = bucketByDay { _, day -> routineCompletions.count { isOnDay(it.completedAt, day) } },
            focusMinutesPerDay = bucketByDay { _, day ->
                focusCompletions.filter { isOnDay(it.completedAt, day) }.sumOf { it.focusedMinutes }
            },
            todosPerDay = bucketByDay { _, day -> todoTimes.count { isOnDay(it, day) } },
            // perDayMinutes is oldest first, so today is its last entry.
            screenPerDay = screenTime?.perDayMinutes?.let { minutes ->
                bucketByDay { daysAgo, _ -> minutes.getOrElse(minutes.size - 1 - daysAgo) { 0 } }
            },
            topApps = apps,
            totalOvertimeMinutes7d = routineCompletions.sumOf { it.overtimeMinutes },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** Called on resume: screen time keeps changing while the app is closed. */
    fun refresh() {
        viewModelScope.launch {
            val time = usageTracker.screenTime(DAYS)
            val apps = time?.todayByApp?.take(TOP_APPS)?.map { (pkg, minutes) ->
                AppTime(pkg, appName(pkg), minutes)
            }.orEmpty()
            screen.value = time to apps
        }
    }

    suspend fun appIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    private suspend fun appName(packageName: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
        }.getOrDefault(packageName)
    }

    /** Builds the buckets, oldest first, labelled with the weekday. */
    private fun bucketByDay(valueForDay: (daysAgo: Int, day: Calendar) -> Int): List<DayStat> {
        val format = SimpleDateFormat("EE", Locale.ENGLISH)
        return (DAYS - 1 downTo 0).map { daysAgo ->
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            DayStat(
                label = format.format(Date(day.timeInMillis)),
                value = valueForDay(daysAgo, day),
                isToday = daysAgo == 0,
            )
        }
    }

    private fun isOnDay(millis: Long, day: Calendar): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.YEAR) == day.get(Calendar.YEAR) &&
            c.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
    }

    private fun startOfDayDaysAgo(days: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -days)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val DAYS = 7
        const val TOP_APPS = 5
    }
}
