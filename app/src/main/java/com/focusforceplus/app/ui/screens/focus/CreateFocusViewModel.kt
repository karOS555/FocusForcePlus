package com.focusforceplus.app.ui.screens.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.TamperProtectionGuard
import com.focusforceplus.app.compliance.validateFocusDuration
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.service.FocusSessionRegistry
import com.focusforceplus.app.util.FocusAlarmHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateFocusUiState(
    val name: String = "",
    val type: String = "CUSTOM",
    val durationMinutes: Int = 25,
    val enableDnd: Boolean = true,
    val blockNotifications: Boolean = false,
    val appBlockerEnabled: Boolean = true,
    val invincibleMode: Boolean = false,
    val scheduleEnabled: Boolean = false,
    val scheduledDays: Set<String> = setOf("MO", "TU", "WE", "TH", "FR"),
    val scheduledHour: Int = 9,
    val scheduledMinute: Int = 0,
    // App blocking scope: all configured apps, or specific groups.
    val availableGroups: List<String> = emptyList(),
    val blockAllApps: Boolean = true,
    val selectedGroups: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val nameError: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CreateFocusViewModel @Inject constructor(
    private val repository: FocusRepository,
    private val blockerRepository: BlockerRepository,
    private val focusAlarmHelper: FocusAlarmHelper,
    private val registry: FocusSessionRegistry,
    private val tamperGuard: TamperProtectionGuard,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editSessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L
    val isEditMode: Boolean get() = editSessionId != 0L

    /** Golden Rule #11: while the session runs, Invincible Mode must not be togglable. */
    val isCurrentlyRunning: Boolean
        get() = isEditMode && registry.isActive(editSessionId)

    private val _uiState = MutableStateFlow(CreateFocusUiState())
    val uiState: StateFlow<CreateFocusUiState> = _uiState.asStateFlow()

    private var originalCreatedAt: Long? = null

    // Baseline for the discard-changes warning: state as loaded (edit) or default (create).
    private var snapshotState: CreateFocusUiState? = null

    /** True when the user edited anything since opening the screen. */
    val hasUnsavedChanges: Boolean
        get() = snapshotState?.let { normalized(_uiState.value) != normalized(it) } ?: false

    private fun normalized(s: CreateFocusUiState) =
        s.copy(isSaving = false, nameError = false, error = null)

    init {
        viewModelScope.launch {
            val groups = blockerRepository.getAllGroupsOnce().map { it.name }
            _uiState.update { it.copy(availableGroups = groups) }
            if (!isEditMode) snapshotState = _uiState.value
        }
        if (isEditMode) loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            repository.getSessionById(editSessionId).first()?.let { s ->
                originalCreatedAt = s.createdAt
                _uiState.update {
                    it.copy(
                        name = s.name,
                        type = s.type,
                        durationMinutes = s.durationMinutes,
                        enableDnd = s.enableDnd,
                        blockNotifications = s.blockNotifications,
                        appBlockerEnabled = s.appBlockerEnabled,
                        invincibleMode = s.invincibleMode,
                        scheduleEnabled = s.scheduledDays != null && s.scheduledTimeHour != null,
                        scheduledDays = s.scheduledDays?.split(",")?.filter { d -> d.isNotBlank() }?.toSet()
                            ?: setOf("MO", "TU", "WE", "TH", "FR"),
                        scheduledHour = s.scheduledTimeHour ?: 9,
                        scheduledMinute = s.scheduledTimeMinute ?: 0,
                        blockAllApps = s.blockedGroupsCsv == null,
                        selectedGroups = s.blockedGroupsCsv
                            ?.split(",")?.filter { g -> g.isNotBlank() }?.toSet()
                            ?: emptySet(),
                    )
                }
            }
            snapshotState = _uiState.value
        }
    }

    fun updateName(v: String) = _uiState.update { it.copy(name = v, nameError = false) }

    /** Selecting a type applies its preset (duration + toggles); Custom changes nothing.
     *  Presets are suggestions — every field stays freely editable afterwards. */
    fun updateType(v: String) = _uiState.update { state ->
        val preset = FocusTypes.of(v).preset
        if (preset == null) {
            state.copy(type = v)
        } else {
            // Suggest only the groups that actually exist; if none match, keep "all apps".
            val matching = preset.suggestedGroups.filter { suggested ->
                state.availableGroups.any { it.equals(suggested, ignoreCase = true) }
            }.mapNotNull { suggested ->
                state.availableGroups.firstOrNull { it.equals(suggested, ignoreCase = true) }
            }.toSet()
            state.copy(
                type = v,
                durationMinutes = preset.durationMinutes,
                enableDnd = preset.enableDnd,
                blockNotifications = preset.blockNotifications,
                appBlockerEnabled = preset.appBlockerEnabled,
                blockAllApps = matching.isEmpty(),
                selectedGroups = matching,
            )
        }
    }
    fun updateDuration(v: Int) = _uiState.update { it.copy(durationMinutes = v.coerceIn(1, 240)) }
    fun updateEnableDnd(v: Boolean) = _uiState.update { it.copy(enableDnd = v) }
    fun updateBlockNotifications(v: Boolean) = _uiState.update { it.copy(blockNotifications = v) }
    fun updateAppBlocker(v: Boolean) = _uiState.update { it.copy(appBlockerEnabled = v) }
    fun updateInvincible(v: Boolean) {
        if (isCurrentlyRunning) return
        // Turning a persisted-on flag OFF is a protected change under Tamper
        // Protection (guide 2.2). Turning ON is always allowed.
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
    fun updateScheduleEnabled(v: Boolean) = _uiState.update { it.copy(scheduleEnabled = v) }
    fun toggleScheduledDay(day: String) = _uiState.update {
        val days = it.scheduledDays.toMutableSet()
        if (day in days && days.size > 1) days.remove(day) else days.add(day)
        it.copy(scheduledDays = days)
    }
    fun updateScheduledTime(hour: Int, minute: Int) =
        _uiState.update { it.copy(scheduledHour = hour, scheduledMinute = minute) }

    fun updateBlockAllApps(all: Boolean) = _uiState.update { it.copy(blockAllApps = all) }
    fun toggleBlockedGroup(group: String) = _uiState.update {
        val groups = it.selectedGroups.toMutableSet()
        if (group in groups) groups.remove(group) else groups.add(group)
        it.copy(selectedGroups = groups)
    }

    fun save(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        validateFocusDuration(state.durationMinutes)?.let { err ->
            _uiState.update { it.copy(error = err) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val entity = FocusSessionEntity(
                    id = if (isEditMode) editSessionId else 0L,
                    name = state.name.trim(),
                    type = state.type,
                    durationMinutes = state.durationMinutes,
                    enableDnd = state.enableDnd,
                    blockNotifications = state.blockNotifications,
                    appBlockerEnabled = state.appBlockerEnabled,
                    invincibleMode = state.invincibleMode,
                    // null = block all configured apps; else only the chosen groups.
                    blockedGroupsCsv = if (state.blockAllApps || state.selectedGroups.isEmpty()) null
                                       else state.selectedGroups.joinToString(","),
                    scheduledDays = if (state.scheduleEnabled) state.scheduledDays.joinToString(",") else null,
                    scheduledTimeHour = if (state.scheduleEnabled) state.scheduledHour else null,
                    scheduledTimeMinute = if (state.scheduleEnabled) state.scheduledMinute else null,
                    isActive = true,
                    createdAt = if (isEditMode) originalCreatedAt ?: System.currentTimeMillis()
                                else System.currentTimeMillis(),
                )
                val savedId = if (isEditMode) {
                    repository.updateSession(entity); editSessionId
                } else {
                    repository.insertSession(entity)
                }
                focusAlarmHelper.cancelSessionAlarms(savedId)
                if (state.scheduleEnabled) {
                    focusAlarmHelper.scheduleSessionAlarms(entity.copy(id = savedId))
                }
                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save session") }
            }
        }
    }
}
