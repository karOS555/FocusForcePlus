package com.focusforceplus.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the focus session that is currently running. */
data class ActiveFocusSession(
    val sessionId: Long,
    val name: String,
    /** Epoch millis when the session ends (drives countdowns outside the focus UI). */
    val endsAtMillis: Long,
    val blocksApps: Boolean,
    val invincible: Boolean,
    /** Non-null while paused: the frozen remaining seconds. */
    val pausedRemainingSeconds: Int? = null,
    val totalSeconds: Int = 0,
    /** Blocker groups this session blocks; null = all configured apps. */
    val blockedGroups: Set<String>? = null,
)

/**
 * Process-wide source of truth for "is a focus session currently running?".
 *
 * Mirrors [ActiveRoutineRegistry]: owned by the focus foreground service's lifecycle,
 * consulted by the app blocker (block-during-focus), the focus UI (IDLE/ACTIVE gating
 * of Invincible Mode per Golden Rule #11), and the home dashboard.
 */
@Singleton
class FocusSessionRegistry @Inject constructor() {

    private val _activeSession = MutableStateFlow<ActiveFocusSession?>(null)
    val activeSession: StateFlow<ActiveFocusSession?> = _activeSession.asStateFlow()

    fun markActive(session: ActiveFocusSession) {
        _activeSession.value = session
    }

    /** No-op unless [sessionId] matches — guards against stale service teardown. */
    fun markIdle(sessionId: Long) {
        if (_activeSession.value?.sessionId == sessionId) _activeSession.value = null
    }

    fun isActive(sessionId: Long): Boolean = _activeSession.value?.sessionId == sessionId

    fun isBlockingApps(): Boolean = _activeSession.value?.blocksApps == true

    fun isSessionInvincible(): Boolean = _activeSession.value?.invincible == true
}
