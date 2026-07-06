package com.focusforceplus.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focusforceplus.app.compliance.BlockReason
import com.focusforceplus.app.compliance.NEVER_BLOCK_PACKAGES
import com.focusforceplus.app.compliance.evaluateBlock
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.ui.screens.blocker.BlockedAppActivity
import com.focusforceplus.app.util.BlockerNotificationHelper
import com.focusforceplus.app.util.InstalledAppsHelper
import com.focusforceplus.app.util.UsageTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Detects foreground app changes and covers blocked apps with [BlockedAppActivity].
 *
 * Compliance constraints (see `.claude/PLAY_STORE_COMPLIANCE.md`):
 * - Minimal configuration: only `typeWindowStateChanged`, `canRetrieveWindowContent`
 *   is false (`res/xml/accessibility_service_config.xml`) — the service never reads
 *   screen content, it only learns which package came to the foreground.
 * - Event scope is narrowed at runtime: `serviceInfo.packageNames` is set to exactly
 *   the packages with blocking rules, so Android does not even deliver events for
 *   other apps. With no rules it is pinned to our own package (= effectively silent).
 * - Never blocks the packages in [NEVER_BLOCK_PACKAGES] (system settings, dialer, …)
 *   or the default launcher (Golden Rule #2).
 * - On disconnect: informational cleanup only — no auto-restart, no back-navigation,
 *   no pressure (Golden Rules #2/#3).
 */
@AndroidEntryPoint
class AppBlockerAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockerRepository: BlockerRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var routineRegistry: ActiveRoutineRegistry
    @Inject lateinit var focusRegistry: FocusSessionRegistry
    @Inject lateinit var usageTracker: UsageTracker
    @Inject lateinit var installedAppsHelper: InstalledAppsHelper
    @Inject lateinit var blockerNotificationHelper: BlockerNotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Immutable snapshot the event thread reads; rebuilt whenever inputs change. */
    private data class BlockerSnapshot(
        val rulesByPackage: Map<String, BlockedAppEntity> = emptyMap(),
        val groupsByName: Map<String, BlockerGroupEntity> = emptyMap(),
        /** Summed usedTodayMinutes of ALL member apps per group (incl. disabled rules). */
        val groupUsedMinutes: Map<String, Int> = emptyMap(),
        val blockerEnabled: Boolean = false,
        val blockDuringRoutines: Boolean = true,
        val blockDuringFocus: Boolean = true,
    )

    @Volatile private var snapshot = BlockerSnapshot()
    @Volatile private var launcherPackage: String? = null

    /** Debounce per package so a stubborn foreground app can't spawn screen loops. */
    private val lastBlockShownAt = HashMap<String, Long>()

    /** Usage refresh throttle — aggregate queries are not free. */
    @Volatile private var lastUsageSyncAt = 0L
    @Volatile private var usageByPackage: Map<String, Int> = emptyMap()

    override fun onServiceConnected() {
        super.onServiceConnected()
        launcherPackage = installedAppsHelper.defaultLauncherPackage()

        serviceScope.launch {
            combine(
                blockerRepository.getAllApps(),
                blockerRepository.getAllGroups(),
                settingsRepository.blockerEnabled,
                settingsRepository.blockDuringRoutines,
                settingsRepository.blockDuringFocus,
            ) { apps, groups, enabled, duringRoutines, duringFocus ->
                BlockerSnapshot(
                    rulesByPackage = apps
                        .filter { it.isBlocked }
                        .associateBy { it.packageName },
                    groupsByName = groups.associateBy { it.name },
                    groupUsedMinutes = apps
                        .filter { it.groupName != null }
                        .groupBy { it.groupName!! }
                        .mapValues { (_, members) -> members.sumOf { it.usedTodayMinutes } },
                    blockerEnabled = enabled,
                    blockDuringRoutines = duringRoutines,
                    blockDuringFocus = duringFocus,
                )
            }.collect { fresh ->
                snapshot = fresh
                updateEventScope(fresh.rulesByPackage.keys)
            }
        }
    }

    /** Narrows delivered events to exactly the packages we have rules for. */
    private fun updateEventScope(packages: Set<String>) {
        runCatching {
            serviceInfo = serviceInfo?.apply {
                packageNames = if (packages.isEmpty()) {
                    arrayOf(packageName) // no rules -> receive (almost) nothing
                } else {
                    packages.toTypedArray()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == launcherPackage || pkg in NEVER_BLOCK_PACKAGES) return

        val snap = snapshot
        if (!snap.blockerEnabled) return
        val rule = snap.rulesByPackage[pkg] ?: return

        serviceScope.launch {
            val now = System.currentTimeMillis()

            // Refresh usage lazily so daily limits use current numbers.
            val currentRule = if (rule.dailyLimitMinutes != null) {
                if (now - lastUsageSyncAt > USAGE_SYNC_THROTTLE_MILLIS) {
                    lastUsageSyncAt = now
                    usageByPackage = usageTracker.syncBlockedAppUsage()
                }
                rule.copy(usedTodayMinutes = usageByPackage[pkg] ?: rule.usedTodayMinutes)
            } else {
                rule
            }

            // Focus sessions may narrow blocking to selected groups.
            val focusSession = focusRegistry.activeSession.value
            val ruleGroupName = currentRule.groupName
            val focusApplies = snap.blockDuringFocus &&
                focusSession?.blocksApps == true &&
                (focusSession.blockedGroups == null ||
                    (ruleGroupName != null && ruleGroupName in focusSession.blockedGroups))

            // Shared group limit: summed usage of all members vs the group budget.
            val group = currentRule.groupName?.let { snap.groupsByName[it] }
            val groupUsed = currentRule.groupName?.let { snap.groupUsedMinutes[it] } ?: 0
            val groupLimitReached = group?.sharedDailyLimitMinutes != null &&
                groupUsed >= group.sharedDailyLimitMinutes

            val reason = evaluateBlock(
                app = currentRule,
                nowMillis = now,
                minutesOfDay = minutesOfDay(),
                routineBlocking = snap.blockDuringRoutines && routineRegistry.isBlockingApps(),
                focusBlocking = focusApplies,
                inGroup = group != null,
                groupLimitReached = groupLimitReached,
            ) ?: return@launch

            val lastShown = synchronized(lastBlockShownAt) { lastBlockShownAt[pkg] ?: 0L }
            if (now - lastShown < BLOCK_SCREEN_DEBOUNCE_MILLIS) return@launch
            synchronized(lastBlockShownAt) { lastBlockShownAt[pkg] = now }

            showBlockedScreen(currentRule, reason, group, groupUsed)
        }
    }

    private fun showBlockedScreen(
        rule: BlockedAppEntity,
        reason: BlockReason,
        group: BlockerGroupEntity?,
        groupUsedMinutes: Int,
    ) {
        val sessionInvincible = when (reason) {
            BlockReason.ROUTINE_ACTIVE -> routineRegistry.isSessionInvincible()
            BlockReason.FOCUS_ACTIVE -> focusRegistry.isSessionInvincible()
            else -> false
        }
        // SYSTEM_ALERT_WINDOW (granted for the alarm flow) exempts this start from
        // background-activity-launch restrictions.
        runCatching {
            startActivity(
                Intent(this, BlockedAppActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                    putExtra(BlockedAppActivity.EXTRA_PACKAGE, rule.packageName)
                    putExtra(BlockedAppActivity.EXTRA_APP_NAME, rule.appName)
                    putExtra(BlockedAppActivity.EXTRA_REASON, reason.name)
                    putExtra(BlockedAppActivity.EXTRA_SESSION_INVINCIBLE, sessionInvincible)
                    if (group != null) {
                        putExtra(BlockedAppActivity.EXTRA_GROUP_NAME, group.name)
                        group.sharedDailyLimitMinutes?.let {
                            putExtra(BlockedAppActivity.EXTRA_GROUP_LIMIT, it)
                        }
                        putExtra(BlockedAppActivity.EXTRA_GROUP_USED, groupUsedMinutes)
                    }
                }
            )
        }
    }

    override fun onInterrupt() {
        // Nothing to interrupt — we render no feedback of our own.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // The user (or the system) disabled the service. Compliance: clean up,
        // inform once, and do nothing else — never restart or steer the user back.
        if (snapshot.rulesByPackage.isNotEmpty() && snapshot.blockerEnabled) {
            runCatching { blockerNotificationHelper.showServiceLostNotification() }
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val BLOCK_SCREEN_DEBOUNCE_MILLIS = 1_500L
        private const val USAGE_SYNC_THROTTLE_MILLIS = 30_000L

        private fun minutesOfDay(): Int {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
    }
}
