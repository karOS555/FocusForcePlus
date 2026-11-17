package com.focusforceplus.app.ui.screens.blocker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.GuardSettings
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.service.FocusSessionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockerSettingsUiState(
    val blockDuringRoutines: Boolean = true,
    val blockDuringFocus: Boolean = true,
    val message: String? = null,
)

/**
 * Settings that apply to every block. The master switch lives on the blocker
 * screen itself, next to the blocks it pauses, with its lock checks there.
 */
@HiltViewModel
class BlockerSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val routineRegistry: ActiveRoutineRegistry,
    private val focusRegistry: FocusSessionRegistry,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BlockerSettingsUiState> = combine(
        settingsRepository.blockDuringRoutines,
        settingsRepository.blockDuringFocus,
        _message,
    ) { routines, focus, message ->
        BlockerSettingsUiState(blockDuringRoutines = routines, blockDuringFocus = focus, message = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockerSettingsUiState())

    /** Speed bump defaults: switching blocking off during sessions counts as pausing. */
    val guards: StateFlow<GuardSettings> = settingsRepository.guardSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GuardSettings())

    fun clearMessage() = _message.update { null }

    // A running invincible session keeps its blocking: switching it off here would
    // be a side door around the lock (Golden Rule #11). It frees up when it ends.
    fun setBlockDuringRoutines(enabled: Boolean) {
        if (!enabled && routineRegistry.activeSession.value?.let { it.invincible && it.blocksApps } == true) {
            _message.value = "An invincible routine is running. This can change once it is over."
            return
        }
        viewModelScope.launch { settingsRepository.saveBlockDuringRoutines(enabled) }
    }

    fun setBlockDuringFocus(enabled: Boolean) {
        if (!enabled && focusRegistry.activeSession.value?.let { it.invincible && it.blocksApps } == true) {
            _message.value = "An invincible focus session is running. This can change once it is over."
            return
        }
        viewModelScope.launch { settingsRepository.saveBlockDuringFocus(enabled) }
    }
}
