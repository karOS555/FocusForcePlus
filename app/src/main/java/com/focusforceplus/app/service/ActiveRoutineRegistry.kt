package com.focusforceplus.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the routine session that is currently running. */
data class ActiveRoutineSession(
    val routineId: Long,
    /** The routine's appBlockerEnabled flag — the app blocker consults this. */
    val blocksApps: Boolean,
    /** Whether the running routine is invincible (gates the block-screen exception). */
    val invincible: Boolean,
)

/**
 * Process-wide source of truth for "is a routine currently running?".
 *
 * Owned by [RoutineForegroundService]'s lifecycle: the service marks a routine active on
 * `ACTION_START` and idle on `onDestroy`. Other components (ViewModels, repositories,
 * UI, the app blocker service) inject the registry to gate compliance-relevant actions —
 * toggling Invincible Mode, deleting a routine, mutating critical settings — depending
 * on whether the routine sits in IDLE or ACTIVE state.
 *
 * Background: `.claude/PLAY_STORE_COMPLIANCE.md` section 2.1 (State-Machine) and
 * `.claude/routine-invincible-review.md` (R1).
 *
 * Only one routine can be active at a time, which matches the current product design.
 * If multi-session ever ships, swap the backing field for a Set.
 */
@Singleton
class ActiveRoutineRegistry @Inject constructor() {

    private val _activeSession = MutableStateFlow<ActiveRoutineSession?>(null)
    val activeSession: StateFlow<ActiveRoutineSession?> = _activeSession.asStateFlow()

    private val _activeRoutineId = MutableStateFlow<Long?>(null)

    /** Live id of the running routine, or null — kept for existing call sites. */
    val activeRoutineId: StateFlow<Long?> = _activeRoutineId.asStateFlow()

    fun markActive(id: Long, blocksApps: Boolean = false, invincible: Boolean = false) {
        _activeSession.value = ActiveRoutineSession(id, blocksApps, invincible)
        _activeRoutineId.value = id
    }

    /** No-op unless [id] is the routine currently marked active — prevents stale stop
     *  callbacks (e.g. delayed `onDestroy` after a new routine has already started)
     *  from clobbering a freshly-active session. */
    fun markIdle(id: Long) {
        if (_activeRoutineId.value == id) {
            _activeSession.value = null
            _activeRoutineId.value = null
        }
    }

    fun isActive(id: Long): Boolean = _activeRoutineId.value == id

    /** True when a routine with app blocking enabled is running right now. */
    fun isBlockingApps(): Boolean = _activeSession.value?.blocksApps == true

    /** True when the running routine (if any) is invincible. */
    fun isSessionInvincible(): Boolean = _activeSession.value?.invincible == true
}
