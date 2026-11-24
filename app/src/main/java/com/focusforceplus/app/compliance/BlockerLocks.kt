package com.focusforceplus.app.compliance

import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.data.repository.SleepSchedule
import com.focusforceplus.app.service.ActiveFocusSession
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.service.ActiveRoutineSession
import com.focusforceplus.app.service.FocusSessionRegistry
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that decides whether blocking is locked at one moment. Gathered once,
 * so the rules below are plain functions of it and can be tested without Android.
 */
data class LockState(
    val rules: List<BlockedAppEntity>,
    val groups: List<BlockerGroupEntity>,
    val minutesOfDay: Int,
    val dayCode: String,
    val routine: ActiveRoutineSession? = null,
    val focus: ActiveFocusSession? = null,
    val sleep: SleepSchedule = SleepSchedule(),
    val blockDuringRoutines: Boolean = true,
    val blockDuringFocus: Boolean = true,
) {
    private val groupsByName = groups.associateBy { it.name }

    private fun group(rule: BlockedAppEntity): BlockerGroupEntity? = rule.groupName?.let { groupsByName[it] }

    /** A block is locked while its shared limit is used up or its schedule is running. */
    fun isLocked(group: BlockerGroupEntity): Boolean = isGroupLocked(
        group,
        rules.filter { it.groupName == group.name }.sumOf { it.usedTodayMinutes },
        isGroupScheduleActive(group, minutesOfDay, dayCode),
    )

    val sleepRunning: Boolean
        get() = sleep.enabled && isSleepWindowActive(
            sleep.bedtimeMinutes, sleep.wakeMinutes, sleep.activeDaysCsv, minutesOfDay, dayCode,
        )

    /**
     * What keeps this rule on right now, in a few words, or null when it is free to
     * be switched off, removed or taken out of its block.
     *
     * Three things can hold a rule (compliance guide 2.1): its own Invincible Mode
     * while its limit is used up or its window is running, its block's Invincible
     * Mode while that block is locked, and a routine, focus session or night with
     * Invincible Mode that is running and blocks this app. Freeing the rule would
     * be a way around each of them.
     */
    fun holdOn(rule: BlockedAppEntity): String? {
        if (!rule.isBlocked) return null
        if (isRuleLocked(rule, minutesOfDay)) return "its own limit or schedule"
        val group = group(rule)
        if (group != null && isLocked(group)) return "the block \"${group.name}\""

        val duringRoutines = group?.blockDuringRoutines ?: rule.blockDuringRoutines
        if (routine != null && routine.invincible && routine.blocksApps && blockDuringRoutines && duringRoutines &&
            (routine.blockedGroups == null || rule.groupName in routine.blockedGroups)
        ) return "the routine that is running"

        val duringFocus = group?.blockDuringFocus ?: rule.blockDuringFocus
        if (focus != null && focus.invincible && focus.blocksApps && blockDuringFocus && duringFocus &&
            (focus.blockedGroups == null || rule.groupName in focus.blockedGroups)
        ) return "the focus session that is running"

        if (sleepRunning && sleep.invincibleMode && sleep.blocks(rule.groupName)) {
            return "sleep mode"
        }
        return null
    }

    /** How many rules are held right now, see [holdOn]. */
    val heldRules: Int get() = rules.count { holdOn(it) != null }

    /** Invincible Mode is switched on somewhere in the blocker, locked right now or not. */
    val hasInvincibleBlocking: Boolean
        get() = rules.any { it.invincibleMode && it.isBlocked } ||
            groups.any { g -> g.invincibleMode && rules.any { it.groupName == g.name && it.isBlocked } }
}

/**
 * Answers "may this be switched off right now?" for the app blocker, in one place.
 *
 * Invincible Mode means a block cannot be weakened while it is active (Golden Rule
 * #11), and with Tamper Protection on, not outside the daily window either. There
 * are many switches that weaken blocking: a rule's own, a block's, the master
 * switch on three screens, the module switch, and putting a backup back. Each of
 * them asks here, so none can be the side door around the others.
 */
@Singleton
class BlockerLocks @Inject constructor(
    private val blockerRepository: BlockerRepository,
    private val settingsRepository: SettingsRepository,
    private val sleepRepository: SleepRepository,
    private val routineRegistry: ActiveRoutineRegistry,
    private val focusRegistry: FocusSessionRegistry,
    private val tamperGuard: TamperProtectionGuard,
) {
    suspend fun state(): LockState {
        val cal = Calendar.getInstance()
        return LockState(
            rules = blockerRepository.getAllAppsOnce(),
            groups = blockerRepository.getAllGroupsOnce(),
            minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE),
            dayCode = dayCodeFor(cal.get(Calendar.DAY_OF_WEEK)),
            routine = routineRegistry.activeSession.value,
            focus = focusRegistry.activeSession.value,
            sleep = sleepRepository.schedule.first(),
            blockDuringRoutines = settingsRepository.blockDuringRoutines.first(),
            blockDuringFocus = settingsRepository.blockDuringFocus.first(),
        )
    }

    /**
     * Why blocking as a whole cannot be switched off right now, or null when it can:
     * for the master switch and for switching the Blocker module off.
     */
    suspend fun refusalToStopBlocking(): String? {
        val state = state()
        val held = state.heldRules
        if (held > 0) {
            return "$held app(s) are locked by Invincible Mode right now. They are free again at " +
                "midnight, at the end of their schedule, or when the running session ends."
        }
        if (state.hasInvincibleBlocking) {
            val verdict = tamperGuard.checkProtectedChange()
            if (!verdict.allowed) return verdict.message
        }
        return null
    }

    /**
     * Why this one rule cannot be switched off, removed or taken out of its block
     * right now, or null when it can.
     */
    suspend fun refusalToFree(rule: BlockedAppEntity): String? {
        val state = state()
        // The stored rule decides, not the copy a screen happens to hold.
        val stored = state.rules.firstOrNull { it.packageName == rule.packageName } ?: return null
        val hold = state.holdOn(stored)
        if (hold != null) {
            return "${stored.appName} is locked by Invincible Mode right now ($hold). " +
                "It is free again at its natural end."
        }
        val group = stored.groupName?.let { name -> state.groups.firstOrNull { it.name == name } }
        if (stored.isBlocked && (stored.invincibleMode || group?.invincibleMode == true)) {
            val verdict = tamperGuard.checkProtectedChange()
            if (!verdict.allowed) return verdict.message
        }
        return null
    }

    /** The same for a whole block: its switch, deleting it, or removing members. */
    suspend fun refusalToFreeGroup(groupName: String): String? {
        val state = state()
        val members = state.rules.filter { it.groupName == groupName && it.isBlocked }
        val held = members.count { state.holdOn(it) != null }
        if (held > 0) {
            return "This block is locked by Invincible Mode right now. It is free again at midnight, " +
                "at the end of its schedule, or when the running session ends."
        }
        val group = state.groups.firstOrNull { it.name == groupName }
        if (members.isNotEmpty() && (group?.invincibleMode == true || members.any { it.invincibleMode })) {
            val verdict = tamperGuard.checkProtectedChange()
            if (!verdict.allowed) return verdict.message
        }
        return null
    }
}
