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

/** One measure over the last seven days, with the seven before for comparison. */
data class Metric(
    /** The last seven days, oldest first, today last. */
    val days: List<DayStat> = emptyList(),
    val previousTotal: Int = 0,
    /** Days of the seven before that had something. */
    val previousCounted: Int = 0,
    /** Days in a row with something, up to today (or yesterday, while today is still empty). */
    val streak: Int = 0,
) {
    val total: Int get() = days.sumOf { it.value }
    val change: Int get() = total - previousTotal

    /**
     * Average per day over the days that have something; empty days before first
     * use do not count. Today is left out while there are finished days to go by:
     * it is not over yet, and counting it as a whole day pulls the average down
     * every morning. On the very first day it is all there is.
     */
    val average: Int
        get() {
            val finished = days.dropLast(1).filter { it.value > 0 }
            return if (finished.isEmpty()) days.lastOrNull()?.value ?: 0
            else finished.sumOf { it.value } / finished.size
        }

    val previousAverage: Int get() = if (previousCounted == 0) 0 else previousTotal / previousCounted
}

data class StatsUiState(
    val focus: Metric = Metric(),
    val routines: Metric = Metric(),
    val todos: Metric = Metric(),
    /** Null without usage access. */
    val screen: Metric? = null,
    val topApps: List<AppTime> = emptyList(),
    val overtimeMinutes: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    routineRepository: RoutineRepository,
    focusRepository: FocusRepository,
    todoRepository: TodoRepository,
    private val usageTracker: UsageTracker,
) : ViewModel() {

    /** Enough history for this week, the week before, and a streak of a few weeks. */
    private val historyStart = startOfDayDaysAgo(HISTORY_DAYS - 1)
    private val screen = MutableStateFlow<Pair<ScreenTime?, List<AppTime>>>(null to emptyList())

    val uiState: StateFlow<StatsUiState> = combine(
        routineRepository.getCompletionsSince(historyStart),
        focusRepository.getCompletionsSince(historyStart),
        todoRepository.getCompletionTimesSince(historyStart),
        screen,
    ) { routineCompletions, focusCompletions, todoTimes, (screenTime, apps) ->
        val weekStart = startOfDayDaysAgo(DAYS - 1)
        StatsUiState(
            focus = metric { day -> focusCompletions.filter { it.completedAt in day }.sumOf { it.focusedMinutes } },
            routines = metric { day -> routineCompletions.count { it.completedAt in day } },
            todos = metric { day -> todoTimes.count { it in day } },
            // perDayMinutes is oldest first, so today is its last entry.
            screen = screenTime?.perDayMinutes?.let { minutes ->
                metric(streaks = false) { day ->
                    minutes.getOrElse(minutes.size - 1 - day.daysAgo) { 0 }
                }
            },
            topApps = apps,
            overtimeMinutes = routineCompletions.filter { it.completedAt >= weekStart }.sumOf { it.overtimeMinutes },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** Called on resume: screen time keeps changing while the app is closed. */
    fun refresh() {
        viewModelScope.launch {
            val time = usageTracker.screenTime(DAYS * 2)
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

    /** One local day: its start, its end, and how many days ago it was. */
    private class Day(val daysAgo: Int, val start: Long, val end: Long) {
        operator fun contains(millis: Long) = millis in start until end
    }

    private fun day(daysAgo: Int): Day {
        val start = startOfDayDaysAgo(daysAgo)
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        return Day(daysAgo, start, end)
    }

    private fun metric(streaks: Boolean = true, valueOf: (Day) -> Int): Metric {
        val format = SimpleDateFormat("EE", Locale.ENGLISH)
        val days = (DAYS - 1 downTo 0).map { ago ->
            val d = day(ago)
            DayStat(label = format.format(Date(d.start)), value = valueOf(d), isToday = ago == 0)
        }
        val previousValues = (DAYS * 2 - 1 downTo DAYS).map { valueOf(day(it)) }
        val streak = if (!streaks) 0 else {
            // Today still counts as "not broken" while it is empty.
            val firstDay = if (valueOf(day(0)) > 0) 0 else 1
            (firstDay until HISTORY_DAYS).takeWhile { valueOf(day(it)) > 0 }.count()
        }
        return Metric(
            days = days,
            previousTotal = previousValues.sum(),
            previousCounted = previousValues.count { it > 0 },
            streak = streak,
        )
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
        const val HISTORY_DAYS = 60
        const val TOP_APPS = 5
    }
}
