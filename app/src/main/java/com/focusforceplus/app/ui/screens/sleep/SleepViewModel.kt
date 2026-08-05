package com.focusforceplus.app.ui.screens.sleep

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.TamperProtectionGuard
import com.focusforceplus.app.compliance.blockWindowLengthMinutes
import com.focusforceplus.app.compliance.dayCodeFor
import com.focusforceplus.app.compliance.isSleepWindowActive
import com.focusforceplus.app.compliance.validateBlockWindow
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.data.repository.SleepSchedule
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.SleepAlarmHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class SleepUiState(
    /** Editable draft; persisted on Save. Routine links persist immediately instead. */
    val draft: SleepSchedule = SleepSchedule(),
    /** The persisted schedule (drives the routine-link rows and the status line). */
    val saved: SleepSchedule = SleepSchedule(),
    val routines: List<RoutineEntity> = emptyList(),
    val groupNames: List<String> = emptyList(),
    val blockerMasterEnabled: Boolean = false,
    val dndAccessGranted: Boolean = false,
    val windowActiveNow: Boolean = false,
    val message: String? = null,
    val isLoading: Boolean = true,
) {
    fun routineName(id: Long): String? = routines.firstOrNull { it.id == id }?.name
}

/**
 * Sleep mode settings. The invincible state machine matches the rest of the app:
 * while the sleep window is running and invincible, weakening changes are refused;
 * turning invincibility off (in idle) is Tamper-Protection gated.
 */
@HiltViewModel
class SleepViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sleepRepository: SleepRepository,
    private val routineRepository: RoutineRepository,
    private val blockerRepository: BlockerRepository,
    private val settingsRepository: SettingsRepository,
    private val sleepAlarmHelper: SleepAlarmHelper,
    private val tamperGuard: TamperProtectionGuard,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    private var draftSeeded = false

    init {
        viewModelScope.launch {
            sleepRepository.schedule.collect { saved ->
                _uiState.update {
                    it.copy(
                        saved = saved,
                        draft = if (draftSeeded) it.draft.copy(
                            bedtimeRoutineId = saved.bedtimeRoutineId,
                            morningRoutineId = saved.morningRoutineId,
                        ) else saved,
                        windowActiveNow = windowActive(saved),
                        isLoading = false,
                    )
                }
                draftSeeded = true
            }
        }
        viewModelScope.launch {
            routineRepository.getAllRoutines().collect { routines ->
                _uiState.update { it.copy(routines = routines) }
            }
        }
        viewModelScope.launch {
            blockerRepository.getAllGroups().collect { groups ->
                _uiState.update { it.copy(groupNames = groups.map { g -> g.name }) }
            }
        }
        viewModelScope.launch {
            settingsRepository.blockerEnabled.collect { enabled ->
                _uiState.update { it.copy(blockerMasterEnabled = enabled) }
            }
        }
        refreshPermissions()
    }

    fun refreshPermissions() {
        _uiState.update { it.copy(dndAccessGranted = PermissionHelper.hasDndAccess(context)) }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun updateDraft(transform: (SleepSchedule) -> SleepSchedule) {
        _uiState.update { it.copy(draft = transform(it.draft)) }
    }

    /** Routine links apply immediately so the create-and-link flow works too. */
    fun setBedtimeRoutine(routineId: Long) {
        viewModelScope.launch { sleepRepository.setBedtimeRoutineId(routineId) }
    }

    fun setMorningRoutine(routineId: Long) {
        viewModelScope.launch { sleepRepository.setMorningRoutineId(routineId) }
    }

    fun save() {
        viewModelScope.launch {
            val s = _uiState.value
            val current = s.saved
            val desired = s.draft

            // While the window runs and the schedule is invincible, weakening is
            // locked until wake-up (Golden Rule #11).
            if (current.invincibleMode && current.enabled && windowActive(current) &&
                weakensSleep(current, desired)
            ) {
                _uiState.update {
                    it.copy(message = "Sleep mode is locked by Invincible Mode until wake-up. " +
                        "You can still strengthen it.")
                }
                return@launch
            }
            // Turning invincibility off (idle) is a protected change.
            if (current.invincibleMode && !desired.invincibleMode) {
                val verdict = tamperGuard.checkProtectedChange()
                if (!verdict.allowed) {
                    _uiState.update { it.copy(message = verdict.message) }
                    return@launch
                }
            }
            // Blocking during sleep is bounded like every block window (12h cap).
            if (desired.enabled && desired.blockedGroups.isNotEmpty()) {
                validateBlockWindow(desired.bedtimeMinutes, desired.wakeMinutes)?.let {
                    _uiState.update { st -> st.copy(message = it) }
                    return@launch
                }
            }

            sleepRepository.saveSchedule(desired)
            sleepAlarmHelper.scheduleAll(desired)
            _uiState.update { it.copy(message = "Sleep schedule saved.") }
        }
    }

    private fun windowActive(schedule: SleepSchedule): Boolean {
        if (!schedule.enabled) return false
        val cal = Calendar.getInstance()
        val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return isSleepWindowActive(
            schedule.bedtimeMinutes, schedule.wakeMinutes, schedule.activeDaysCsv,
            minutesOfDay, dayCodeFor(cal.get(Calendar.DAY_OF_WEEK)),
        )
    }

    /** Changes that loosen the commitment while the window is running. */
    private fun weakensSleep(old: SleepSchedule, new: SleepSchedule): Boolean {
        if (!new.enabled) return true
        if (!new.invincibleMode) return true
        if (!new.blockedGroups.containsAll(old.blockedGroups)) return true
        val oldLen = blockWindowLengthMinutes(old.bedtimeMinutes, old.wakeMinutes)
        val newLen = blockWindowLengthMinutes(new.bedtimeMinutes, new.wakeMinutes)
        return newLen < oldLen
    }
}
