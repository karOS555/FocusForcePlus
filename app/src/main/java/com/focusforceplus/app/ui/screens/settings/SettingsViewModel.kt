package com.focusforceplus.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.TamperProtectionConfig
import com.focusforceplus.app.compliance.TamperProtectionGuard
import com.focusforceplus.app.compliance.isInTpWindow
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.util.BackupManager
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.TodoAlarmHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsPermissionStates(
    val notifications: Boolean = false,
    val exactAlarms: Boolean = false,
    val overlay: Boolean = false,
    val fullScreenIntent: Boolean = false,
    val accessibility: Boolean = false,
    val usageStats: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val dndAccess: Boolean = false,
)

data class SettingsUiState(
    val digestTimes: List<Pair<Int, Int>> = SettingsRepository.DEFAULT_DIGEST_TIMES,
    val defaultPriority: Int = 1,
    val autoDeleteDays: Int = 0,
    val alarmSoundEnabled: Boolean = true,
    val alarmVibrationEnabled: Boolean = true,
    val permissions: SettingsPermissionStates = SettingsPermissionStates(),
    val appVersion: String = "",
    val message: String? = null,
    // Tamper Protection
    val tpEnabled: Boolean = false,
    val tpWindowStartMinutes: Int = 8 * 60,
    val tpWindowDurationMinutes: Int = 30,
    val tpWindowOpenNow: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val blockerRepository: BlockerRepository,
    private val todoAlarmHelper: TodoAlarmHelper,
    private val backupManager: BackupManager,
    private val tamperGuard: TamperProtectionGuard,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(appVersion = readVersionName()))
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
        viewModelScope.launch {
            settingsRepository.alarmSoundEnabled.collect { enabled ->
                _uiState.update { it.copy(alarmSoundEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.alarmVibrationEnabled.collect { enabled ->
                _uiState.update { it.copy(alarmVibrationEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.tpEnabled.collect { enabled ->
                _uiState.update { it.copy(tpEnabled = enabled) }
                refreshTpWindowState()
            }
        }
        viewModelScope.launch {
            settingsRepository.tpWindowStartMinutes.collect { start ->
                _uiState.update { it.copy(tpWindowStartMinutes = start) }
                refreshTpWindowState()
            }
        }
        viewModelScope.launch {
            settingsRepository.tpWindowDurationMinutes.collect { duration ->
                _uiState.update { it.copy(tpWindowDurationMinutes = duration) }
                refreshTpWindowState()
            }
        }
        refreshPermissions()
    }

    private fun readVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    /** Recomputes "is the TP change window open right now" (also on screen resume). */
    fun refreshTpWindowState() {
        val s = _uiState.value
        val cal = java.util.Calendar.getInstance()
        val minutesOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val open = isInTpWindow(
            TamperProtectionConfig(true, s.tpWindowStartMinutes, s.tpWindowDurationMinutes),
            minutesOfDay,
        )
        _uiState.update { it.copy(tpWindowOpenNow = open) }
    }

    fun refreshPermissions() {
        refreshTpWindowState()
        _uiState.update {
            it.copy(
                permissions = SettingsPermissionStates(
                    notifications = PermissionHelper.canPostNotifications(context),
                    exactAlarms = PermissionHelper.canScheduleExactAlarms(context),
                    overlay = PermissionHelper.canDrawOverlays(context),
                    fullScreenIntent = PermissionHelper.canUseFullScreenIntent(context),
                    accessibility = PermissionHelper.isAccessibilityServiceEnabled(context),
                    usageStats = PermissionHelper.hasUsageStatsPermission(context),
                    batteryUnrestricted = PermissionHelper.isIgnoringBatteryOptimizations(context),
                    dndAccess = PermissionHelper.hasDndAccess(context),
                )
            )
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

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

    // ── Alarm sound / vibration ───────────────────────────────────────────────

    fun setAlarmSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveAlarmSoundEnabled(enabled) }
    }

    fun setAlarmVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveAlarmVibrationEnabled(enabled) }
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    /** Resets today's blocker usage counters and exception budget. */
    fun resetUsageStatistics() {
        viewModelScope.launch {
            blockerRepository.resetDailyUsage()
            _uiState.update { it.copy(message = "Usage statistics reset.") }
        }
    }

    // ── Tamper Protection ─────────────────────────────────────────────────────

    fun toggleTamperProtection() {
        viewModelScope.launch {
            val target = !_uiState.value.tpEnabled
            val verdict = tamperGuard.trySetEnabled(target)
            _uiState.update {
                it.copy(
                    message = verdict.message ?: if (verdict.allowed) {
                        if (target) "Tamper Protection enabled." else "Tamper Protection disabled."
                    } else null,
                )
            }
            refreshTpWindowState()
        }
    }

    fun setTpWindow(startMinutes: Int, durationMinutes: Int) {
        viewModelScope.launch {
            val verdict = tamperGuard.trySetWindow(startMinutes, durationMinutes)
            if (!verdict.allowed) {
                _uiState.update { it.copy(message = verdict.message) }
            }
            refreshTpWindowState()
        }
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val message = runCatching { backupManager.exportTo(uri) }
                .getOrElse { "Export failed: ${it.message}" }
            _uiState.update { it.copy(message = message) }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val message = runCatching { backupManager.importFrom(uri) }
                .getOrElse { "Import failed: ${it.message} — your existing data is unchanged." }
            _uiState.update { it.copy(message = message) }
        }
    }
}
