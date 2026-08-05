package com.focusforceplus.app.ui.screens.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.dayCodeFor
import com.focusforceplus.app.compliance.isSleepWindowActive
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.data.repository.SleepSchedule
import com.focusforceplus.app.service.ActiveFocusSession
import com.focusforceplus.app.service.FocusForegroundService
import com.focusforceplus.app.service.FocusSessionRegistry
import com.focusforceplus.app.util.FocusAlarmHelper
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.SleepAlarmHelper
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
import java.util.Calendar
import javax.inject.Inject

data class FocusListUiState(
    val sessions: List<FocusSessionEntity> = emptyList(),
    val activeSession: ActiveFocusSession? = null,
    val dndAccessGranted: Boolean = false,
    val message: String? = null,
    // Sleep mode shares this tab — see SleepScreen for the full editor.
    val sleep: SleepSchedule = SleepSchedule(),
    val sleepWindowActive: Boolean = false,
    val routines: List<RoutineEntity> = emptyList(),
) {
    fun routineName(id: Long): String? = routines.firstOrNull { it.id == id }?.name
}

@HiltViewModel
class FocusListViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: FocusRepository,
    private val registry: FocusSessionRegistry,
    private val focusAlarmHelper: FocusAlarmHelper,
    private val sleepRepository: SleepRepository,
    private val sleepAlarmHelper: SleepAlarmHelper,
    routineRepository: RoutineRepository,
) : ViewModel() {

    private val _dndAccess = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FocusListUiState> = combine(
        combine(repository.getAllSessions(), registry.activeSession, ::Pair),
        combine(sleepRepository.schedule, routineRepository.getAllRoutines(), ::Pair),
        _dndAccess,
        _message,
    ) { (sessions, active), (sleep, routines), dnd, message ->
        FocusListUiState(
            sessions = sessions,
            activeSession = active,
            dndAccessGranted = dnd,
            message = message,
            sleep = sleep,
            sleepWindowActive = sleepWindowActive(sleep),
            routines = routines,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FocusListUiState(),
    )

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        _dndAccess.value = PermissionHelper.hasDndAccess(context)
    }

    // ── Sleep mode (quick controls; full editor lives in SleepScreen) ─────────

    /**
     * Enables/disables the whole schedule from the tab. Refused while an
     * invincible sleep window is running — that lock releases at wake-up.
     */
    fun toggleSleepEnabled() {
        viewModelScope.launch {
            val current = sleepRepository.schedule.first()
            if (current.enabled && current.invincibleMode && sleepWindowActive(current)) {
                _message.value = "Sleep mode is locked by Invincible Mode until wake-up."
                return@launch
            }
            val updated = current.copy(enabled = !current.enabled)
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

    fun clearMessage() = _message.update { null }

    /** Starts a saved session. Returns false when another session is already running. */
    fun startSession(session: FocusSessionEntity): Boolean {
        if (registry.activeSession.value != null) {
            _message.value = "Another focus session is already running."
            return false
        }
        FocusForegroundService.start(
            context = context,
            sessionId = session.id,
            name = session.name,
            durationMinutes = session.durationMinutes,
            enableDnd = session.enableDnd,
            blockNotifications = session.blockNotifications,
            blocksApps = session.appBlockerEnabled,
            invincible = session.invincibleMode,
            type = session.type,
            blockedGroupsCsv = session.blockedGroupsCsv,
        )
        return true
    }

    /** Quick start: an ad-hoc, non-invincible session that is not persisted. */
    fun quickStart(durationMinutes: Int, enableDnd: Boolean): Boolean {
        if (registry.activeSession.value != null) {
            _message.value = "Another focus session is already running."
            return false
        }
        FocusForegroundService.start(
            context = context,
            sessionId = 0L,
            name = "Quick focus",
            durationMinutes = durationMinutes,
            enableDnd = enableDnd,
            blockNotifications = false,
            blocksApps = true,
            invincible = false,
        )
        return true
    }

    fun deleteSession(session: FocusSessionEntity) {
        if (registry.isActive(session.id)) {
            _message.value = "Cannot delete a running session. End it first."
            return
        }
        viewModelScope.launch {
            focusAlarmHelper.cancelSessionAlarms(session.id)
            repository.deleteSession(session)
        }
    }

    fun toggleScheduleActive(session: FocusSessionEntity) {
        viewModelScope.launch {
            val updated = session.copy(isActive = !session.isActive)
            repository.updateSession(updated)
            if (updated.isActive) focusAlarmHelper.scheduleSessionAlarms(updated)
            else focusAlarmHelper.cancelSessionAlarms(updated.id)
        }
    }
}
