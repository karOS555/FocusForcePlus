package com.focusforceplus.app.ui.screens.focus

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.service.FocusForegroundService
import com.focusforceplus.app.service.FocusSessionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActiveFocusUiState(
    val sessionName: String = "",
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 1,
    val paused: Boolean = false,
    val invincible: Boolean = false,
    /** True while the session runs; false once it ended (completed or cancelled). */
    val isRunning: Boolean = true,
    /** True when the session reached its natural end while this screen was open. */
    val showCompleted: Boolean = false,
)

/**
 * Pure presenter over [FocusSessionRegistry] — the foreground service owns the real
 * timer; this view model just mirrors it for the UI at 1s resolution.
 */
@HiltViewModel
class ActiveFocusViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val registry: FocusSessionRegistry,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    private val _uiState = MutableStateFlow(ActiveFocusUiState())
    val uiState: StateFlow<ActiveFocusUiState> = _uiState.asStateFlow()

    private var sawSessionRunning = false

    init {
        viewModelScope.launch {
            while (true) {
                val active = registry.activeSession.value
                if (active != null && active.sessionId == sessionId) {
                    sawSessionRunning = true
                    val remaining = active.pausedRemainingSeconds
                        ?: ((active.endsAtMillis - System.currentTimeMillis()) / 1_000L)
                            .toInt()
                            .coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            sessionName = active.name,
                            remainingSeconds = remaining,
                            totalSeconds = active.totalSeconds.coerceAtLeast(1),
                            paused = active.pausedRemainingSeconds != null,
                            invincible = active.invincible,
                            isRunning = true,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            // Natural end while watching -> celebrate; otherwise stay neutral.
                            showCompleted = sawSessionRunning && it.remainingSeconds <= 1,
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    fun pause() = FocusForegroundService.pause(context)
    fun resume() = FocusForegroundService.resume(context)

    /** The service ignores this for invincible sessions — defense in depth. */
    fun endSession() = FocusForegroundService.end(context)
}
