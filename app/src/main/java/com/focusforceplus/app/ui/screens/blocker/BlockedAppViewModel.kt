package com.focusforceplus.app.ui.screens.blocker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.BLOCK_EXCEPTION_DURATION_MILLIS
import com.focusforceplus.app.compliance.BlockReason
import com.focusforceplus.app.compliance.MAX_BLOCK_EXCEPTIONS_PER_DAY
import com.focusforceplus.app.compliance.canGrantException
import com.focusforceplus.app.compliance.isGroupLocked
import com.focusforceplus.app.data.repository.BlockerRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class BlockedAppUiState(
    val appName: String = "",
    val packageName: String = "",
    val reason: BlockReason = BlockReason.MANUAL,
    val dailyLimitMinutes: Int? = null,
    val usedTodayMinutes: Int = 0,
    /** Minutes until the daily limit resets (midnight) — shown for DAILY_LIMIT. */
    val minutesUntilMidnight: Int = 0,
    /** Minutes until the blocking window ends — shown for TIME_WINDOW. */
    val minutesUntilWindowEnd: Int? = null,
    val canUseException: Boolean = false,
    val exceptionsLeft: Int = 0,
    val isLoading: Boolean = true,
    // Group-limit context (GROUP_LIMIT reason)
    val groupName: String? = null,
    val groupLimitMinutes: Int? = null,
    val groupUsedMinutes: Int = 0,
)

@HiltViewModel
class BlockedAppViewModel @Inject constructor(
    private val repository: BlockerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val packageName: String = savedStateHandle.get<String>(BlockedAppActivity.EXTRA_PACKAGE) ?: ""
    private val appName: String = savedStateHandle.get<String>(BlockedAppActivity.EXTRA_APP_NAME) ?: ""
    private val reason: BlockReason = runCatching {
        BlockReason.valueOf(savedStateHandle.get<String>(BlockedAppActivity.EXTRA_REASON) ?: "")
    }.getOrDefault(BlockReason.MANUAL)
    private val sessionInvincible: Boolean =
        savedStateHandle.get<Boolean>(BlockedAppActivity.EXTRA_SESSION_INVINCIBLE) ?: false
    private val extraGroupName: String? = savedStateHandle.get<String>(BlockedAppActivity.EXTRA_GROUP_NAME)
    private val extraGroupLimit: Int? = savedStateHandle.get<Int>(BlockedAppActivity.EXTRA_GROUP_LIMIT)
    private val extraGroupUsed: Int = savedStateHandle.get<Int>(BlockedAppActivity.EXTRA_GROUP_USED) ?: 0

    private val _uiState = MutableStateFlow(BlockedAppUiState(appName = appName, packageName = packageName, reason = reason))
    val uiState: StateFlow<BlockedAppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val rule = repository.getByPackageName(packageName)
            val nowCal = Calendar.getInstance()
            val minutesOfDay = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
            val untilMidnight = 24 * 60 - minutesOfDay

            val windowEnd = rule?.windowEndMinutes
            val untilWindowEnd = if (reason == BlockReason.TIME_WINDOW && windowEnd != null) {
                // Wrap-around aware: overnight windows end on the next day.
                ((windowEnd - minutesOfDay) + 24 * 60) % (24 * 60)
            } else null

            val groupLocked = groupIsLocked()

            _uiState.update {
                it.copy(
                    appName = rule?.appName ?: appName,
                    dailyLimitMinutes = rule?.dailyLimitMinutes,
                    usedTodayMinutes = rule?.usedTodayMinutes ?: 0,
                    minutesUntilMidnight = untilMidnight,
                    minutesUntilWindowEnd = untilWindowEnd,
                    canUseException = rule != null &&
                        canGrantException(rule, reason, minutesOfDay, sessionInvincible, groupLocked),
                    exceptionsLeft = rule?.let {
                        (MAX_BLOCK_EXCEPTIONS_PER_DAY - it.exceptionsUsedToday).coerceAtLeast(0)
                    } ?: 0,
                    isLoading = false,
                    groupName = extraGroupName,
                    groupLimitMinutes = extraGroupLimit,
                    groupUsedMinutes = extraGroupUsed,
                )
            }
        }
    }

    /** Live check whether the app's group is invincible-locked right now. */
    private suspend fun groupIsLocked(): Boolean {
        val name = extraGroupName ?: return false
        val group = repository.getGroupByName(name) ?: return false
        val usedSum = repository.getAllApps().first()
            .filter { it.groupName == name }
            .sumOf { it.usedTodayMinutes }
        return isGroupLocked(group, usedSum)
    }

    /** Grants a 5-minute exception (max 2/day) and reports success via [onGranted]. */
    fun useException(onGranted: () -> Unit) {
        viewModelScope.launch {
            val rule = repository.getByPackageName(packageName) ?: return@launch
            val nowCal = Calendar.getInstance()
            val minutesOfDay = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
            if (!canGrantException(rule, reason, minutesOfDay, sessionInvincible, groupIsLocked())) return@launch
            repository.grantException(
                id = rule.id,
                untilMillis = System.currentTimeMillis() + BLOCK_EXCEPTION_DURATION_MILLIS,
                usedToday = rule.exceptionsUsedToday + 1,
            )
            onGranted()
        }
    }
}
