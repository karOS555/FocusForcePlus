package com.focusforceplus.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

data class StatsUiState(
    val routinesPerDay: List<DayStat> = emptyList(),
    val focusMinutesPerDay: List<DayStat> = emptyList(),
    val totalRoutines7d: Int = 0,
    val totalFocusMinutes7d: Int = 0,
    val totalOvertimeMinutes7d: Int = 0,
    val focusSessions7d: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    routineRepository: RoutineRepository,
    focusRepository: FocusRepository,
) : ViewModel() {

    private val windowStart = startOfDayDaysAgo(6)

    val uiState: StateFlow<StatsUiState> = combine(
        routineRepository.getCompletionsSince(windowStart),
        focusRepository.getCompletionsSince(windowStart),
    ) { routineCompletions, focusCompletions ->
        val routinesPerDay = bucketByDay { day ->
            routineCompletions.count { isOnDay(it.completedAt, day) }
        }
        val focusPerDay = bucketByDay { day ->
            focusCompletions.filter { isOnDay(it.completedAt, day) }.sumOf { it.focusedMinutes }
        }
        StatsUiState(
            routinesPerDay = routinesPerDay,
            focusMinutesPerDay = focusPerDay,
            totalRoutines7d = routineCompletions.size,
            totalFocusMinutes7d = focusCompletions.sumOf { it.focusedMinutes },
            totalOvertimeMinutes7d = routineCompletions.sumOf { it.overtimeMinutes },
            focusSessions7d = focusCompletions.size,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    /** Builds the 7 buckets, oldest first, labelled with the weekday. */
    private fun bucketByDay(valueForDay: (Calendar) -> Int): List<DayStat> {
        val format = SimpleDateFormat("EE", Locale.ENGLISH)
        return (6 downTo 0).map { daysAgo ->
            val day = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            DayStat(
                label = format.format(Date(day.timeInMillis)),
                value = valueForDay(day),
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
}
