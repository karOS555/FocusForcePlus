package com.focusforceplus.app.ui.screens.routine

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.Guard
import com.focusforceplus.app.compliance.GuardAction
import com.focusforceplus.app.compliance.GuardOverrides
import com.focusforceplus.app.compliance.GuardSettings
import com.focusforceplus.app.compliance.GuardSlot
import com.focusforceplus.app.compliance.TamperProtectionGuard
import com.focusforceplus.app.compliance.validateRoutineDuration
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.ui.navigation.AppModule
import com.focusforceplus.app.util.AlarmHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val listId: Long? = null,
)

/** A step that exists in some routine, offered while typing a new one. */
data class TaskTemplate(
    val name: String,
    val durationMinutes: Int,
    val iconKey: String?,
    val notes: String?,
)

data class RoutineUiState(
    val name: String = "",
    val description: String = "",
    val iconKey: String? = null,
    val startTimeHour: Int = 8,
    val startTimeMinute: Int = 0,
    val activeDays: Set<String> = emptySet(),
    /** False pauses the schedule; the routine can still be started by hand. */
    val isActive: Boolean = true,
    val invincibleMode: Boolean = false,
    val appBlockerEnabled: Boolean = true,
    val enableDnd: Boolean = false,
    val preAlarmMinutes: Int = 15,
    /** False = only [selectedGroups] are blocked while it runs. */
    val blockAllApps: Boolean = true,
    val selectedGroups: Set<String> = emptySet(),
    val maxSnoozeCount: Int = 2,
    val maxRescheduleCount: Int = 1,
    val guardOverrides: GuardOverrides = GuardOverrides(),
    val guardDefaults: GuardSettings = GuardSettings(),
    val isSaving: Boolean = false,
    val nameError: Boolean = false,
    val daysError: Boolean = false,
    val tasksError: Boolean = false,
    val error: String? = null,
) {
    fun defaultGuard(action: GuardAction): Guard = when (action) {
        GuardAction.SNOOZE -> guardDefaults[GuardSlot.ROUTINE_SNOOZE]
        GuardAction.RESCHEDULE -> guardDefaults[GuardSlot.ROUTINE_RESCHEDULE]
        GuardAction.DONE -> Guard.NONE
    }

    fun guardFor(action: GuardAction): Guard =
        if (!guardDefaults.enabled) Guard.NONE else guardOverrides[action] ?: defaultGuard(action)
}

@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val alarmHelper: AlarmHelper,
    private val activeRegistry: ActiveRoutineRegistry,
    private val tamperGuard: TamperProtectionGuard,
    private val settingsRepository: SettingsRepository,
    private val navRepository: NavRepository,
    listRepository: com.focusforceplus.app.data.repository.ListRepository,
    blockerRepository: com.focusforceplus.app.data.repository.BlockerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** The blocker's group names, to block only some of them while it runs. */
    val blockGroups: StateFlow<List<String>> = blockerRepository.getAllGroups()
        .map { groups -> groups.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lists a step can carry, e.g. a packing list for "Pack the bag". */
    val lists: StateFlow<List<com.focusforceplus.app.data.db.entity.ListEntity>> = listRepository.getLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val editRoutineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L
    val isEditMode: Boolean get() = editRoutineId != 0L

    /** True when the routine being edited is currently running.
     *  Golden Rule #11: while it runs, Invincible Mode must not be togglable. */
    val isCurrentlyRunning: Boolean
        get() = isEditMode && activeRegistry.isActive(editRoutineId)

    private val _uiState = MutableStateFlow(RoutineUiState())
    val uiState: StateFlow<RoutineUiState> = _uiState.asStateFlow()

    val tasks = mutableStateListOf<TaskUiItem>()

    /** Every step from every routine, one per name, most recent first. */
    val taskTemplates: StateFlow<List<TaskTemplate>> = repository.getAllTasks()
        .map { all ->
            all.distinctBy { it.name.trim().lowercase() }
                .filter { it.name.isNotBlank() }
                .map { TaskTemplate(it.name.trim(), it.durationMinutes, it.iconKey, it.description) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Routines whose steps can be copied into this one. */
    val otherRoutines: StateFlow<List<Pair<RoutineEntity, Int>>> = combine(
        repository.getAllRoutines(),
        repository.getAllTasks(),
    ) { routines, all ->
        val counts = all.groupingBy { it.routineId }.eachCount()
        routines.filter { it.id != editRoutineId && (counts[it.id] ?: 0) > 0 }
            .map { it to (counts[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The stored routine, so fields this screen does not show survive an edit.
    private var original: RoutineEntity? = null

    // Baseline for the discard-changes warning: state as loaded (edit) or default (create).
    private var snapshotState: RoutineUiState? = null
    private var snapshotTasks: List<TaskUiItem> = emptyList()

    /** True when the user edited anything since opening the screen. */
    val hasUnsavedChanges: Boolean
        get() = snapshotState?.let { snap ->
            normalized(_uiState.value) != normalized(snap) || tasks.toList() != snapshotTasks
        } ?: false

    private fun normalized(s: RoutineUiState) = s.copy(
        isSaving = false, nameError = false, daysError = false, tasksError = false, error = null,
        guardDefaults = GuardSettings(),
    )

    private fun takeSnapshot() {
        snapshotState = _uiState.value
        snapshotTasks = tasks.toList()
    }

    init {
        viewModelScope.launch {
            val guards = settingsRepository.guardSettings.first()
            _uiState.update { it.copy(guardDefaults = guards) }
            if (isEditMode) loadRoutine(editRoutineId) else takeSnapshot()
        }
    }

    private suspend fun loadRoutine(id: Long) {
        repository.getRoutineById(id).first()?.let { e ->
            original = e
            _uiState.update {
                it.copy(
                    name = e.name,
                    description = e.description ?: "",
                    iconKey = e.iconKey,
                    startTimeHour = e.startTimeHour,
                    startTimeMinute = e.startTimeMinute,
                    activeDays = e.activeDays.split(",").filter(String::isNotBlank).toSet(),
                    isActive = e.isActive,
                    invincibleMode = e.invincibleMode,
                    appBlockerEnabled = e.appBlockerEnabled,
                    enableDnd = e.enableDnd,
                    preAlarmMinutes = e.preAlarmMinutes,
                    blockAllApps = e.blockedGroups == null,
                    selectedGroups = e.blockedGroups.orEmpty(),
                    maxSnoozeCount = e.maxSnoozeCount,
                    maxRescheduleCount = e.maxRescheduleCount,
                    guardOverrides = GuardOverrides.decode(e.guards),
                )
            }
        }
        repository.getTasksForRoutine(id).first().also { entities ->
            tasks.clear()
            tasks.addAll(entities.map { it.toUiItem() })
        }
        takeSnapshot()
    }

    private fun RoutineTaskEntity.toUiItem() = TaskUiItem(
        id = id,
        name = name,
        durationMinutes = durationMinutes,
        notes = description ?: "",
        iconKey = iconKey,
        reminderBeforeEndMinutes = reminderBeforeEndMinutes,
        overtimeReminderIntervalMinutes = overtimeReminderIntervalMinutes,
        listId = listId,
    )

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
    fun updateActive(v: Boolean) = _uiState.update { it.copy(isActive = v) }

    fun updateInvincibleMode(v: Boolean) {
        if (isCurrentlyRunning) return
        // Turning a stored "on" OFF is a protected change under Tamper Protection
        // (guide 2.2). Turning it ON is always allowed.
        val persistedOn = snapshotState?.invincibleMode == true
        if (!v && persistedOn) {
            viewModelScope.launch {
                val verdict = tamperGuard.checkProtectedChange()
                if (verdict.allowed) {
                    _uiState.update { it.copy(invincibleMode = false) }
                } else {
                    _uiState.update { it.copy(error = verdict.message) }
                }
            }
        } else {
            _uiState.update { it.copy(invincibleMode = v) }
        }
    }

    fun updateAppBlocker(v: Boolean) = _uiState.update { it.copy(appBlockerEnabled = v) }
    fun updateDnd(v: Boolean) = _uiState.update { it.copy(enableDnd = v) }
    fun updatePreAlarm(minutes: Int) = _uiState.update { it.copy(preAlarmMinutes = minutes) }
    fun updateBlockAllApps(all: Boolean) = _uiState.update { it.copy(blockAllApps = all) }
    fun toggleBlockedGroup(group: String) = _uiState.update {
        it.copy(selectedGroups = if (group in it.selectedGroups) it.selectedGroups - group else it.selectedGroups + group)
    }

    fun updateMaxSnooze(v: Int) {
        // The cap must not be raised mid-run: the running session read it at its
        // start, and changing it here would lift the limit through a side door.
        if (isCurrentlyRunning) return
        _uiState.update { it.copy(maxSnoozeCount = v) }
    }

    fun updateMaxReschedule(v: Int) {
        if (isCurrentlyRunning) return
        _uiState.update { it.copy(maxRescheduleCount = v) }
    }

    /** null hands the action back to the default. */
    fun updateGuard(action: GuardAction, guard: Guard?) =
        _uiState.update { it.copy(guardOverrides = it.guardOverrides.with(action, guard)) }

    // ── Steps ─────────────────────────────────────────────────────────────────

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

    /** Fills a step from a template: name, length, icon and note. */
    fun applyTemplate(tempId: String, template: TaskTemplate) {
        val i = tasks.indexOfFirst { it.tempId == tempId }
        if (i < 0) return
        tasks[i] = tasks[i].copy(
            name = template.name,
            durationMinutes = template.durationMinutes,
            iconKey = template.iconKey ?: tasks[i].iconKey,
            notes = template.notes ?: tasks[i].notes,
        )
    }

    /** Appends copies of every step of another routine. */
    fun copyStepsFrom(routineId: Long) {
        viewModelScope.launch {
            val steps = repository.getTasksForRoutine(routineId).first()
            tasks.addAll(steps.map { it.toUiItem().copy(tempId = UUID.randomUUID().toString(), id = 0L) })
            if (steps.isNotEmpty()) _uiState.update { it.copy(tasksError = false) }
        }
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    /** [onSuccess] receives the saved routine's id, so callers can link to it. */
    fun saveRoutine(onSuccess: (Long) -> Unit) {
        val state = _uiState.value
        var hasError = false
        if (state.name.isBlank()) { _uiState.update { it.copy(nameError = true) }; hasError = true }
        if (state.activeDays.isEmpty()) { _uiState.update { it.copy(daysError = true) }; hasError = true }
        if (tasks.isEmpty()) { _uiState.update { it.copy(tasksError = true) }; hasError = true }
        if (hasError) {
            _uiState.update { it.copy(error = "Some things are missing; they are marked above.") }
            return
        }

        val durationError = validateRoutineDuration(tasks.sumOf { it.durationMinutes })
        if (durationError != null) {
            _uiState.update { it.copy(error = durationError) }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val base = original ?: RoutineEntity(
                    name = "",
                    startTimeHour = 0,
                    startTimeMinute = 0,
                    activeDays = "",
                    createdAt = System.currentTimeMillis(),
                )
                val entity = base.copy(
                    id = if (isEditMode) editRoutineId else 0L,
                    name = state.name.trim(),
                    description = state.description.trim().ifBlank { null },
                    iconKey = state.iconKey,
                    startTimeHour = state.startTimeHour,
                    startTimeMinute = state.startTimeMinute,
                    activeDays = state.activeDays.joinToString(","),
                    isActive = state.isActive,
                    invincibleMode = state.invincibleMode,
                    appBlockerEnabled = state.appBlockerEnabled,
                    enableDnd = state.enableDnd,
                    preAlarmMinutes = state.preAlarmMinutes,
                    blockedGroupsCsv = if (state.blockAllApps || state.selectedGroups.isEmpty()) null
                                       else state.selectedGroups.joinToString(","),
                    maxSnoozeCount = state.maxSnoozeCount,
                    maxRescheduleCount = state.maxRescheduleCount,
                    guards = state.guardOverrides.encode(),
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
                            name = t.name.trim().ifBlank { "Step ${i + 1}" },
                            description = t.notes.trim().ifBlank { null },
                            durationMinutes = t.durationMinutes,
                            sortOrder = i,
                            iconKey = t.iconKey,
                            reminderBeforeEndMinutes = t.reminderBeforeEndMinutes,
                            overtimeReminderIntervalMinutes = t.overtimeReminderIntervalMinutes,
                            listId = t.listId,
                        )
                    },
                )
                // Old alarms out, new ones in (time, days or the switch may have changed).
                alarmHelper.cancelRoutineAlarms(savedId)
                val saved = repository.getRoutineById(savedId).first()
                if (saved != null && navRepository.isModuleOn(AppModule.ROUTINES)) {
                    alarmHelper.scheduleRoutineAlarms(saved)
                }
                _uiState.update { it.copy(isSaving = false) }
                onSuccess(savedId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Could not save the routine") }
            }
        }
    }

    fun deleteRoutine(onSuccess: () -> Unit) {
        if (!isEditMode) return
        if (isCurrentlyRunning) {
            _uiState.update {
                it.copy(error = "A running routine cannot be deleted. Finish or move it first.")
            }
            return
        }
        viewModelScope.launch {
            try {
                repository.getRoutineById(editRoutineId).first()?.let { entity ->
                    alarmHelper.cancelRoutineAlarms(entity.id)
                    repository.moveToTrash(entity.id)
                    onSuccess()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Could not delete the routine") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
