package com.focusforceplus.app.ui.screens.blocker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.TamperProtectionGuard
import com.focusforceplus.app.compliance.isRuleLocked
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class BlockerSettingsUiState(
    val blockerEnabled: Boolean = false,
    val blockDuringRoutines: Boolean = true,
    val blockDuringFocus: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class BlockerSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val blockerRepository: BlockerRepository,
    private val tamperGuard: TamperProtectionGuard,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BlockerSettingsUiState> = combine(
        settingsRepository.blockerEnabled,
        settingsRepository.blockDuringRoutines,
        settingsRepository.blockDuringFocus,
        _message,
    ) { enabled, routines, focus, message ->
        BlockerSettingsUiState(
            blockerEnabled = enabled,
            blockDuringRoutines = routines,
            blockDuringFocus = focus,
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BlockerSettingsUiState(),
    )

    fun clearMessage() = _message.update { null }

    /**
     * The master switch would be a side door around per-rule Invincible locks, so
     * turning it OFF is refused while any rule is currently locked (Golden Rule #11).
     */
    fun setBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                val cal = Calendar.getInstance()
                val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val rules = blockerRepository.getAllAppsOnce()
                val lockedCount = rules.count { isRuleLocked(it, minutesOfDay) }
                if (lockedCount > 0) {
                    _message.value = "Cannot turn blocking off: $lockedCount rule(s) are " +
                        "locked by Invincible Mode right now. They release at midnight or " +
                        "their window end."
                    return@launch
                }
                // The master switch would neutralize every invincible rule at once —
                // with any such rule present it is a protected change (TP window only).
                if (rules.any { it.invincibleMode && it.isBlocked }) {
                    val verdict = tamperGuard.checkProtectedChange()
                    if (!verdict.allowed) {
                        _message.value = verdict.message
                        return@launch
                    }
                }
            }
            settingsRepository.saveBlockerEnabled(enabled)
        }
    }

    fun setBlockDuringRoutines(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveBlockDuringRoutines(enabled) }
    }

    fun setBlockDuringFocus(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveBlockDuringFocus(enabled) }
    }
}
