package com.focusforceplus.app.ui.screens.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.service.ActiveFocusSession
import com.focusforceplus.app.service.FocusForegroundService
import com.focusforceplus.app.service.FocusSessionRegistry
import com.focusforceplus.app.util.FocusAlarmHelper
import com.focusforceplus.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FocusListUiState(
    val sessions: List<FocusSessionEntity> = emptyList(),
    val activeSession: ActiveFocusSession? = null,
    val dndAccessGranted: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class FocusListViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: FocusRepository,
    private val registry: FocusSessionRegistry,
    private val focusAlarmHelper: FocusAlarmHelper,
) : ViewModel() {

    private val _dndAccess = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FocusListUiState> = combine(
        repository.getAllSessions(),
        registry.activeSession,
        _dndAccess,
        _message,
    ) { sessions, active, dnd, message ->
        FocusListUiState(
            sessions = sessions,
            activeSession = active,
            dndAccessGranted = dnd,
            message = message,
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
