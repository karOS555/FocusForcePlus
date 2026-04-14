package com.focusforceplus.app.ui.screens.routine

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.util.AlarmHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TaskUiItem(
    val tempId: String = UUID.randomUUID().toString(),
    val id: Long = 0L,
    val name: String = "",
    val durationMinutes: Int = 5,
    val notes: String = "",
    val iconKey: String? = null,
    val reminderBeforeEndMinutes: Int = 3,
    val overtimeReminderIntervalMinutes: Int = 5,
)

data class RoutineUiState(
    val name: String = "",
    val description: String = "",
    val iconKey: String? = null,
    val startTimeHour: Int = 8,
    val startTimeMinute: Int = 0,
    val activeDays: Set<String> = emptySet(),
    val invincibleMode: Boolean = false,
    val appBlockerEnabled: Boolean = true,
    val maxSnoozeCount: Int = 2,
    val maxRescheduleCount: Int = 1,
    val isSaving: Boolean = false,
    val nameError: Boolean = false,
    val daysError: Boolean = false,
    val tasksError: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val alarmHelper: AlarmHelper,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editRoutineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L
    val isEditMode: Boolean get() = editRoutineId != 0L

    private val _uiState = MutableStateFlow(RoutineUiState())
    val uiState: StateFlow<RoutineUiState> = _uiState.asStateFlow()

    val tasks = mutableStateListOf<TaskUiItem>()

    init {
        if (isEditMode) loadRoutine(editRoutineId)
    }

    private fun loadRoutine(id: Long) {
        viewModelScope.launch {
            repository.getRoutineById(id).first()?.let { e ->
                _uiState.update {
                    it.copy(
                        name = e.name,
                        description = e.description ?: "",
                        iconKey = e.iconKey,
                        startTimeHour = e.startTimeHour,
                        startTimeMinute = e.startTimeMinute,
                        activeDays = e.activeDays.split(",").filter(String::isNotBlank).toSet(),
                        invincibleMode = e.invincibleMode,
                        appBlockerEnabled = e.appBlockerEnabled,
                        maxSnoozeCount = e.maxSnoozeCount,
                        maxRescheduleCount = e.maxRescheduleCount,
                    )
                }
            }
            repository.getTasksForRoutine(id).first().also { entities ->
                tasks.clear()
                tasks.addAll(entities.map { t ->
                    TaskUiItem(
                        id = t.id,
                        name = t.name,
                        durationMinutes = t.durationMinutes,
                        notes = t.description ?: "",
                        iconKey = t.iconKey,
                        reminderBeforeEndMinutes = t.reminderBeforeEndMinutes,
                        overtimeReminderIntervalMinutes = t.overtimeReminderIntervalMinutes,
                    )
                })
            }
        }
    }

    fun updateName(v: String) = _uiState.update { it.copy(name = v, nameError = false) }
    fun updateDescription(v: String) = _uiState.update { it.copy(description = v) }
    fun updateIconKey(key: String?) = _uiState.update { it.copy(iconKey = key) }
    fun updateStartTime(hour: Int, minute: Int) =
        _uiState.update { it.copy(startTimeHour = hour, startTimeMinute = minute) }
    fun toggleDay(day: String) = _uiState.update {
        val days = it.activeDays.toMutableSet()
        if (day in days) days.remove(day) else days.add(day)
        it.copy(activeDays = days, daysError = false)
    }
    fun updateInvincibleMode(v: Boolean) = _uiState.update { it.copy(invincibleMode = v) }
    fun updateAppBlocker(v: Boolean) = _uiState.update { it.copy(appBlockerEnabled = v) }
    fun updateMaxSnooze(v: Int) = _uiState.update { it.copy(maxSnoozeCount = v) }
    fun updateMaxReschedule(v: Int) = _uiState.update { it.copy(maxRescheduleCount = v) }

    fun addTask() {
        tasks.add(TaskUiItem())
        _uiState.update { it.copy(tasksError = false) }
    }

    fun updateTask(updated: TaskUiItem) {
        val i = tasks.indexOfFirst { it.tempId == updated.tempId }
        if (i >= 0) tasks[i] = updated
    }

    fun deleteTask(tempId: String) = tasks.removeAll { it.tempId == tempId }

    fun swapTasks(from: Int, to: Int) {
        if (from !in tasks.indices || to !in tasks.indices) return
        val item = tasks.removeAt(from)
        tasks.add(to, item)
    }

    fun saveRoutine(onSuccess: () -> Unit) {
        val state = _uiState.value
        var hasError = false
        if (state.name.isBlank()) { _uiState.update { it.copy(nameError = true) }; hasError = true }
        if (state.activeDays.isEmpty()) { _uiState.update { it.copy(daysError = true) }; hasError = true }
        if (tasks.isEmpty()) { _uiState.update { it.copy(tasksError = true) }; hasError = true }
        if (hasError) return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val entity = RoutineEntity(
                    id = if (isEditMode) editRoutineId else 0L,
                    name = state.name.trim(),
                    description = state.description.trim().ifBlank { null },
                    iconKey = state.iconKey,
                    startTimeHour = state.startTimeHour,
                    startTimeMinute = state.startTimeMinute,
                    activeDays = state.activeDays.joinToString(","),
                    invincibleMode = state.invincibleMode,
                    appBlockerEnabled = state.appBlockerEnabled,
                    maxSnoozeCount = state.maxSnoozeCount,
                    maxRescheduleCount = state.maxRescheduleCount,
                    createdAt = System.currentTimeMillis(),
                )
                val savedId = if (isEditMode) {
                    repository.updateRoutine(entity); editRoutineId
                } else {
                    repository.insertRoutine(entity)
                }
                repository.replaceTasksForRoutine(
                    savedId,
                    tasks.mapIndexed { i, t ->
                        RoutineTaskEntity(
                            id = if (t.id != 0L) t.id else 0L,
                            routineId = savedId,
                            name = t.name.trim().ifBlank { "Task ${i + 1}" },
                            description = t.notes.trim().ifBlank { null },
                            durationMinutes = t.durationMinutes,
                            sortOrder = i,
                            iconKey = t.iconKey,
                            reminderBeforeEndMinutes = t.reminderBeforeEndMinutes,
                            overtimeReminderIntervalMinutes = t.overtimeReminderIntervalMinutes,
                        )
                    },
                )
                // Cancel old alarms then reschedule with potentially updated time/days.
                alarmHelper.cancelRoutineAlarms(savedId)
                val savedRoutine = repository.getRoutineById(savedId).first()
                if (savedRoutine != null) alarmHelper.scheduleRoutineAlarms(savedRoutine)
                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save routine") }
            }
        }
    }

    fun deleteRoutine(onSuccess: () -> Unit) {
        if (!isEditMode) return
        viewModelScope.launch {
            try {
                repository.getRoutineById(editRoutineId).first()?.let { entity ->
                    alarmHelper.cancelRoutineAlarms(entity.id)
                    repository.deleteRoutine(entity)
                    onSuccess()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete routine") }
            }
        }
    }
}
