package com.focusforceplus.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.util.TodoAlarmHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val digestTimes: List<Pair<Int, Int>> = SettingsRepository.DEFAULT_DIGEST_TIMES,
    val defaultPriority: Int = 1,
    val autoDeleteDays: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val todoAlarmHelper: TodoAlarmHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.digestTimes.collect { times ->
                _uiState.update { it.copy(digestTimes = times) }
            }
        }
        viewModelScope.launch {
            settingsRepository.defaultPriority.collect { p ->
                _uiState.update { it.copy(defaultPriority = p) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoDeleteDays.collect { days ->
                _uiState.update { it.copy(autoDeleteDays = days) }
            }
        }
    }

    // ── Digest times ──────────────────────────────────────────────────────────

    fun addDigestTime(hour: Int, minute: Int) {
        val current = _uiState.value.digestTimes
        if (current.size >= TodoAlarmHelper.MAX_DIGEST_SLOTS) return
        persistTimes((current + (hour to minute)).sortedBy { it.first * 60 + it.second })
    }

    fun removeDigestTime(index: Int) {
        val current = _uiState.value.digestTimes.toMutableList()
        if (current.size <= 1) return
        current.removeAt(index)
        persistTimes(current)
    }

    fun updateDigestTime(index: Int, hour: Int, minute: Int) {
        val current = _uiState.value.digestTimes.toMutableList()
        if (index !in current.indices) return
        current[index] = hour to minute
        persistTimes(current.sortedBy { it.first * 60 + it.second })
    }

    private fun persistTimes(times: List<Pair<Int, Int>>) {
        viewModelScope.launch {
            settingsRepository.saveDigestTimes(times)
            todoAlarmHelper.scheduleDigestAlarms(times)
        }
    }

    // ── Default priority ──────────────────────────────────────────────────────

    fun setDefaultPriority(priority: Int) {
        viewModelScope.launch { settingsRepository.saveDefaultPriority(priority) }
    }

    // ── Auto-delete ───────────────────────────────────────────────────────────

    fun setAutoDeleteDays(days: Int) {
        viewModelScope.launch { settingsRepository.saveAutoDeleteDays(days) }
    }
}
