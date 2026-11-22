package com.focusforceplus.app.ui.screens.blocker

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.NEVER_BLOCK_PACKAGES
import com.focusforceplus.app.compliance.SHORT_VIDEO_ONLY_APPS
import com.focusforceplus.app.compliance.ShortFeed
import com.focusforceplus.app.compliance.ShortVideoAllowance
import com.focusforceplus.app.compliance.ShortVideoUsage
import com.focusforceplus.app.compliance.TamperProtectionGuard
import com.focusforceplus.app.compliance.dayCodeFor
import com.focusforceplus.app.compliance.isGroupLocked
import com.focusforceplus.app.compliance.isGroupScheduleActive
import com.focusforceplus.app.compliance.isRuleLocked
import com.focusforceplus.app.compliance.validateBlockWindow
import com.focusforceplus.app.compliance.weakensInvincibleGroup
import com.focusforceplus.app.compliance.weakensInvincibleRule
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.service.ServiceLink
import com.focusforceplus.app.util.InstalledApp
import com.focusforceplus.app.util.InstalledAppsHelper
import com.focusforceplus.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar
import javax.inject.Inject

/** Everything the block editor collects for one group. */
data class GroupEditorResult(
    val name: String,
    val limitMinutes: Int?,
    val invincible: Boolean,
    val windowStart: Int?,
    val windowEnd: Int?,
    val windowDaysCsv: String?,
    val blockDuringRoutines: Boolean?,
    val blockDuringFocus: Boolean?,
    val members: Set<String>,
    val memberDisplayNames: Map<String, String>,
    val iconKey: String? = null,
    val alwaysBlock: Boolean = false,
)

/** One row in the blocker list: an installed app plus its rule (if configured). */
data class BlockerAppRow(
    val packageName: String,
    val appName: String,
    val rule: BlockedAppEntity?,
) {
    val isBlocked: Boolean get() = rule?.isBlocked == true
}

/** A group plus its live member count and combined usage, for the group cards. */
data class BlockerGroupRow(
    val group: BlockerGroupEntity,
    val memberCount: Int,
    val usedTodayMinutes: Int,
    val locked: Boolean,
)

/** One entry of "Your blocks": a group with its apps, or a single app's own rule. */
sealed interface BlockItem {
    val key: String

    data class Group(val row: BlockerGroupRow, val members: List<BlockedAppEntity>) : BlockItem {
        override val key: String get() = "g:" + row.group.name
        val active: Boolean get() = members.any { it.isBlocked }
    }

    data class App(val rule: BlockedAppEntity) : BlockItem {
        override val key: String get() = "a:" + rule.packageName
    }
}

/** One feed inside a bigger app (Reels, Shorts), as the short-video section shows it. */
data class ShortFeedRow(val feed: ShortFeed, val on: Boolean, val usedMinutes: Int)

/** An installed app that is nothing but short videos, with its block if it has one. */
data class ShortAppRow(val packageName: String, val appName: String, val rule: BlockedAppEntity?) {
    val on: Boolean get() = rule?.isBlocked == true
}

/** Why the feeds that are switched on are not being left right now. */
enum class ShortTrouble { SERVICE_OFF, SERVICE_NOT_RUNNING, BLOCKING_PAUSED }

/** Everything the short-video section shows. Only what is installed is in it. */
data class ShortVideos(
    val loaded: Boolean = false,
    val feeds: List<ShortFeedRow> = emptyList(),
    val apps: List<ShortAppRow> = emptyList(),
    val allowance: ShortVideoAllowance = ShortVideoAllowance(),
    /** Today's minutes in all the feeds together. */
    val usedMinutes: Int = 0,
    /** The short-video service is switched on in Android. */
    val serviceOn: Boolean = false,
    val trouble: ShortTrouble? = null,
)

/** An app that cannot be opened at this moment, and why in a few words. */
data class BlockedNow(val packageName: String, val appName: String, val why: String)

data class BlockerListUiState(
    val blocks: List<BlockItem> = emptyList(),
    val blockedNow: List<BlockedNow> = emptyList(),
    val rows: List<BlockerAppRow> = emptyList(),
    val searchQuery: String = "",
    val groups: List<BlockerGroupRow> = emptyList(),
    val selectedGroup: String? = null,
    val accessibilityEnabled: Boolean = false,
    /** Switched on in Android, yet not connected: nothing is blocked. */
    val serviceNotRunning: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val blockerEnabled: Boolean = false,
    val isLoadingApps: Boolean = true,
    val message: String? = null,
) {
    val groupNames: List<String> get() = groups.map { it.group.name }
}

@HiltViewModel
class BlockerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: BlockerRepository,
    private val settingsRepository: SettingsRepository,
    private val installedAppsHelper: InstalledAppsHelper,
    private val tamperGuard: TamperProtectionGuard,
    private val locks: com.focusforceplus.app.compliance.BlockerLocks,
    private val groupReferences: com.focusforceplus.app.util.BlockGroupReferences,
) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedGroup = MutableStateFlow<String?>(null)
    private val _permissions = MutableStateFlow(false to false) // accessibility, usage
    private val _isLoadingApps = MutableStateFlow(true)
    private val _message = MutableStateFlow<String?>(null)

    private val _shortsServiceOn = MutableStateFlow(false)

    /** The day at the last look at this page: on a new one the short-video time starts over. */
    private val _today = MutableStateFlow(LocalDate.now().toEpochDay())

    private val _pausedNotice = MutableStateFlow(false)

    /**
     * True right after a block was switched on while blocking as a whole is paused.
     * Its own switch then says on and nothing happens, which is easy to miss, so
     * the page says it once.
     */
    val pausedNotice: StateFlow<Boolean> = _pausedNotice.asStateFlow()

    fun pausedNoticeShown() = _pausedNotice.update { false }

    suspend fun blockingIsPaused(): Boolean = !settingsRepository.blockerEnabled.first()

    private data class ShortSettings(
        val on: Map<ShortFeed, Boolean>,
        val allowance: ShortVideoAllowance,
        val usage: ShortVideoUsage,
    )

    val shortVideos: StateFlow<ShortVideos> = combine(
        combine(
            settingsRepository.shortsBlockInstagram,
            settingsRepository.shortsBlockYouTube,
            settingsRepository.shortVideoAllowance,
            settingsRepository.shortVideoUsage,
            _today,
        ) { instagram, youtube, allowance, usage, today ->
            ShortSettings(
                on = mapOf(ShortFeed.INSTAGRAM to instagram, ShortFeed.YOUTUBE to youtube),
                allowance = allowance,
                usage = usage.on(today),
            )
        },
        combine(_installedApps, _isLoadingApps, ::Pair),
        repository.getAllApps(),
        combine(
            _shortsServiceOn,
            ServiceLink.notRunning(_shortsServiceOn, ServiceLink.shortVideos),
            settingsRepository.blockerEnabled,
            ::Triple,
        ),
    ) { settings, (installed, loading), rules, (serviceOn, stalled, blockingOn) ->
        val installedPackages = installed.map { it.packageName }.toSet()
        val ruleByPackage = rules.associateBy { it.packageName }
        // A feed whose app is not on this phone is not offered, whatever its switch says.
        val feeds = ShortFeed.entries
            .filter { it.packageName in installedPackages }
            .map { ShortFeedRow(it, on = settings.on[it] == true, usedMinutes = settings.usage.minutes(it)) }
        ShortVideos(
            loaded = !loading,
            feeds = feeds,
            apps = installed
                .filter { it.packageName in SHORT_VIDEO_ONLY_APPS }
                .map { ShortAppRow(it.packageName, it.appName, ruleByPackage[it.packageName]) },
            allowance = settings.allowance,
            usedMinutes = settings.usage.totalMinutes,
            serviceOn = serviceOn,
            trouble = when {
                feeds.none { it.on } -> null
                !serviceOn -> ShortTrouble.SERVICE_OFF
                stalled -> ShortTrouble.SERVICE_NOT_RUNNING
                !blockingOn -> ShortTrouble.BLOCKING_PAUSED
                else -> null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShortVideos())

    fun setShortFeed(feed: ShortFeed, on: Boolean) {
        viewModelScope.launch {
            when (feed) {
                ShortFeed.INSTAGRAM -> settingsRepository.saveShortsBlock(instagram = on)
                ShortFeed.YOUTUBE -> settingsRepository.saveShortsBlock(youtube = on)
            }
        }
    }

    /** The daily time for the feeds. More than before is a loosening; the screen guards that. */
    fun setShortVideoAllowance(allowance: ShortVideoAllowance) {
        viewModelScope.launch { settingsRepository.saveShortVideoAllowance(allowance) }
    }

    /** Speed bump defaults, for pausing the blocker or a block. */
    val guards: StateFlow<com.focusforceplus.app.compliance.GuardSettings> = settingsRepository.guardSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.focusforceplus.app.compliance.GuardSettings())

    val uiState: StateFlow<BlockerListUiState> = combine(
        combine(_installedApps, repository.getAllApps(), repository.getAllGroups(), _searchQuery, _selectedGroup) {
                installed, rules, groups, query, group ->
            val ruleByPkg = rules.associateBy { it.packageName }
            // Rules for apps that are no longer launchable still show (so they can be removed).
            val installedPkgs = installed.map { it.packageName }.toSet()
            val orphanRules = rules.filter { it.packageName !in installedPkgs }

            var rows = installed
                .filter { it.packageName !in NEVER_BLOCK_PACKAGES }
                .map { app -> BlockerAppRow(app.packageName, app.appName, ruleByPkg[app.packageName]) } +
                orphanRules.map { BlockerAppRow(it.packageName, it.appName, it) }

            if (query.isNotBlank()) {
                rows = rows.filter {
                    it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
            }
            if (group != null) {
                rows = rows.filter { it.rule?.groupName == group }
            }

            val minutesOfDay = minutesOfDay()
            val today = todayCode()
            val groupRows = groups.map { g ->
                val members = rules.filter { it.groupName == g.name }
                val used = members.sumOf { it.usedTodayMinutes }
                BlockerGroupRow(
                    group = g,
                    memberCount = members.size,
                    usedTodayMinutes = used,
                    locked = isGroupLocked(g, used, isGroupScheduleActive(g, minutesOfDay, today)),
                )
            }

            val sorted = rows.sortedWith(
                compareByDescending<BlockerAppRow> { it.isBlocked }
                    .thenBy { it.appName.lowercase() }
            )
            val blocks = groupRows.map { g -> BlockItem.Group(g, rules.filter { it.groupName == g.group.name }) } +
                rules.filter { it.groupName == null }.sortedBy { it.appName.lowercase() }.map { BlockItem.App(it) }
            Triple(sorted, groupRows, blocks to blockedNow(rules, groups, minutesOfDay, today))
        },
        combine(_permissions, ServiceLink.notRunning(_permissions.map { it.first }, ServiceLink.blocker)) { perms, stalled ->
            Triple(perms.first, perms.second, stalled)
        },
        settingsRepository.blockerEnabled,
        _isLoadingApps,
        _message,
    ) { (rows, groups, more), perms, blockerEnabled, loading, message ->
        BlockerListUiState(
            blocks = more.first,
            blockedNow = more.second,
            rows = rows,
            searchQuery = _searchQuery.value,
            groups = groups,
            selectedGroup = _selectedGroup.value,
            accessibilityEnabled = perms.first,
            serviceNotRunning = perms.third,
            usageAccessGranted = perms.second,
            blockerEnabled = blockerEnabled,
            isLoadingApps = loading,
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BlockerListUiState(),
    )

    init {
        refreshApps()
        refreshPermissions()
        watchForBlocksSwitchedOnWhilePaused()
    }

    /**
     * A block that comes on while blocking is paused sets [pausedNotice]. It looks at
     * the rules themselves, so it holds for every way a block can come on: its
     * switch, the editor, a new block, or the row of a short-video app.
     */
    private fun watchForBlocksSwitchedOnWhilePaused() {
        viewModelScope.launch {
            var before: Set<String>? = null
            repository.getAllApps().collect { rules ->
                val on = rules.filter { it.isBlocked }
                    .map { rule -> rule.groupName?.let { "g:$it" } ?: "a:${rule.packageName}" }
                    .toSet()
                val known = before
                before = on
                // The first look only notes what is there.
                if (known != null && (on - known).isNotEmpty() && !settingsRepository.blockerEnabled.first()) {
                    _pausedNotice.value = true
                }
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _installedApps.value = installedAppsHelper.getInstalledApps()
            _isLoadingApps.value = false
        }
    }

    /** Called from the screen's ON_RESUME observer — system settings may have changed. */
    fun refreshPermissions() {
        _today.value = LocalDate.now().toEpochDay()
        _shortsServiceOn.value = PermissionHelper.isShortFormServiceEnabled(context)
        _permissions.value =
            PermissionHelper.isAccessibilityServiceEnabled(context) to
                PermissionHelper.hasUsageStatsPermission(context)
    }

    fun setSearchQuery(query: String) = _searchQuery.update { query }
    fun selectGroup(group: String?) = _selectedGroup.update { group }
    fun clearMessage() = _message.update { null }

    /** Header quick action. Turning blocking ON never conflicts with invincible
     *  locks — only turning it OFF is guarded (in BlockerSettingsViewModel). */
    fun enableBlocking() {
        viewModelScope.launch { settingsRepository.saveBlockerEnabled(true) }
    }

    suspend fun loadIcon(packageName: String): Drawable? = installedAppsHelper.loadIcon(packageName)

    private fun minutesOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun todayCode(): String =
        dayCodeFor(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))

    /** What is blocked at this very moment by limits, schedules and plain blocks. */
    private fun blockedNow(
        rules: List<BlockedAppEntity>,
        groups: List<BlockerGroupEntity>,
        minutesOfDay: Int,
        today: String,
    ): List<BlockedNow> {
        val now = System.currentTimeMillis()
        val byName = groups.associateBy { it.name }
        val usedByGroup = rules.filter { it.groupName != null }
            .groupBy { it.groupName!! }
            .mapValues { (_, members) -> members.sumOf { it.usedTodayMinutes } }
        return rules.mapNotNull { rule ->
            val group = rule.groupName?.let { byName[it] }
            val used = group?.let { usedByGroup[it.name] } ?: 0
            val limitReached = group?.sharedDailyLimitMinutes?.let { used >= it } == true
            val windowActive = group != null && isGroupScheduleActive(group, minutesOfDay, today)
            val reason = com.focusforceplus.app.compliance.evaluateBlock(
                app = rule,
                nowMillis = now,
                minutesOfDay = minutesOfDay,
                routineBlocking = false,
                focusBlocking = false,
                inGroup = group != null,
                groupLimitReached = limitReached,
                groupWindowActive = windowActive,
                groupAlwaysBlocks = group?.alwaysBlock == true,
            ) ?: return@mapNotNull null
            val why = when (reason) {
                com.focusforceplus.app.compliance.BlockReason.DAILY_LIMIT -> "Limit used \u00B7 free again at midnight"
                com.focusforceplus.app.compliance.BlockReason.GROUP_LIMIT -> "Limit of ${group?.name} used \u00B7 free again at midnight"
                com.focusforceplus.app.compliance.BlockReason.GROUP_WINDOW ->
                    "Schedule \u00B7 free again at ${formatMinutes(group?.windowEndMinutes ?: 0)}"
                com.focusforceplus.app.compliance.BlockReason.TIME_WINDOW ->
                    "Schedule \u00B7 free again at ${formatMinutes(rule.windowEndMinutes ?: 0)}"
                com.focusforceplus.app.compliance.BlockReason.GROUP_ALWAYS -> "Always, by ${group?.name}"
                else -> "Blocked"
            }
            BlockedNow(rule.packageName, rule.appName, why)
        }.sortedBy { it.appName.lowercase() }
    }

    /**
     * The master switch. Turning blocking OFF follows the same rules as everywhere:
     * refused while an invincible rule is actively blocking, and gated by Tamper
     * Protection while invincible rules exist at all (Golden Rule #11).
     */
    fun setBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                locks.refusalToStopBlocking()?.let {
                    _message.value = it
                    return@launch
                }
            }
            settingsRepository.saveBlockerEnabled(enabled)
        }
    }

    private fun lockedMessage() =
        "This rule is locked by Invincible Mode while it is active. It unlocks at its " +
            "natural end (midnight for daily limits, the window end for time windows)."

    /** Quick toggle from the list row. Creates a manual-block rule on first enable. */
    fun toggleBlock(row: BlockerAppRow) {
        viewModelScope.launch {
            val rule = row.rule
            when {
                rule == null -> repository.insertApp(
                    BlockedAppEntity(
                        packageName = row.packageName,
                        appName = row.appName,
                        isBlocked = true,
                    )
                )
                rule.isBlocked -> {
                    // Switching a rule off frees the app: not while Invincible Mode
                    // holds it (its own, its block's, or a running session's), and
                    // with Tamper Protection only inside the window.
                    val refusal = locks.refusalToFree(rule)
                    if (refusal == null) repository.updateApp(rule.copy(isBlocked = false))
                    else _message.value = refusal
                }
                else -> repository.updateApp(rule.copy(isBlocked = true))
            }
        }
    }

    /**
     * Persists the rule edited in the config sheet. While the existing rule is
     * invincible-locked (ACTIVE state) every edit is rejected — Golden Rule #11.
     * Returns errors via [BlockerListUiState.message].
     */
    fun saveRule(updated: BlockedAppEntity) {
        viewModelScope.launch {
            val existing = repository.getByPackageName(updated.packageName)
            if (existing != null && isRuleLocked(existing, minutesOfDay())) {
                _message.value = lockedMessage()
                return@launch
            }
            // Switching it off or taking it out of its block frees the app, which
            // a locked block or a running invincible session does not allow.
            val frees = existing != null && existing.isBlocked &&
                (!updated.isBlocked || updated.groupName != existing.groupName)
            if (frees) {
                locks.refusalToFree(existing!!)?.let {
                    _message.value = it
                    return@launch
                }
            }
            // Weakening an invincible rule is a protected change (TP window only).
            if (existing != null && weakensInvincibleRule(existing, updated)) {
                val verdict = tamperGuard.checkProtectedChange()
                if (!verdict.allowed) {
                    _message.value = verdict.message
                    return@launch
                }
            }
            val start = updated.windowStartMinutes
            val end = updated.windowEndMinutes
            if (start != null && end != null) {
                validateBlockWindow(start, end)?.let {
                    _message.value = it
                    return@launch
                }
            }
            if (existing == null) {
                repository.insertApp(updated.copy(id = 0L))
            } else {
                repository.updateApp(updated.copy(id = existing.id, usedTodayMinutes = existing.usedTodayMinutes))
            }
        }
    }

    fun deleteRule(rule: BlockedAppEntity) {
        viewModelScope.launch {
            locks.refusalToFree(rule)?.let {
                _message.value = it
                return@launch
            }
            repository.deleteApp(rule)
        }
    }

    /**
     * A block's own switch. Switching it off frees every app in it, so it is
     * refused while Invincible Mode holds the block or one of its apps (Golden
     * Rule #11), and with Tamper Protection it only works inside the window.
     */
    fun setGroupBlocked(group: String, blocked: Boolean) {
        viewModelScope.launch {
            if (!blocked) {
                locks.refusalToFreeGroup(group)?.let {
                    _message.value = it
                    return@launch
                }
            }
            repository.getAllAppsOnce()
                .filter { it.groupName == group && it.isBlocked != blocked }
                .forEach { rule -> repository.updateApp(rule.copy(isBlocked = blocked)) }
        }
    }

    // ── Group management ──────────────────────────────────────────────────────

    /**
     * Creates or updates a group and syncs its membership. [members] is the full
     * desired member set (packages). Apps that already have a rule get their
     * groupName set; apps without a rule get a fresh blocked rule so they can be
     * governed. Removing an app from the group clears its groupName. While the
     * existing group is invincible-locked, membership/limit weakening is gated.
     */
    fun saveGroup(originalName: String?, form: GroupEditorResult, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val trimmed = form.name.trim()
            if (trimmed.isBlank()) {
                _message.value = "Give the group a name."
                return@launch
            }
            val windowStartMinutes = form.windowStart
            val windowEndMinutes = form.windowEnd
            if (windowStartMinutes != null && windowEndMinutes != null) {
                validateBlockWindow(windowStartMinutes, windowEndMinutes)?.let {
                    _message.value = it
                    return@launch
                }
            }
            val existing = originalName?.let { repository.getGroupByName(it) }
            val hasSchedule = windowStartMinutes != null && windowEndMinutes != null
            val memberPackages = form.members
            val desired = BlockerGroupEntity(
                id = existing?.id ?: 0L,
                name = trimmed,
                sharedDailyLimitMinutes = form.limitMinutes,
                invincibleMode = form.invincible && (form.limitMinutes != null || hasSchedule),
                windowStartMinutes = windowStartMinutes,
                windowEndMinutes = windowEndMinutes,
                windowDaysCsv = form.windowDaysCsv,
                blockDuringRoutines = form.blockDuringRoutines,
                blockDuringFocus = form.blockDuringFocus,
                iconKey = form.iconKey,
                alwaysBlock = form.alwaysBlock,
            )

            // Weakening an invincible group (limit up/removed, invincible off,
            // schedule removed/shortened) is TP-gated.
            if (existing != null && weakensInvincibleGroup(existing, desired)) {
                val verdict = tamperGuard.checkProtectedChange()
                if (!verdict.allowed) {
                    _message.value = verdict.message
                    return@launch
                }
            }
            // An app that leaves a block is freed: not while Invincible Mode holds
            // it (the block's lock, or a running session that blocks this block).
            // That goes for the apps taken out of this block, and for apps that
            // are moved here out of another one.
            val allRules = repository.getAllAppsOnce()
            val currentMembers = if (existing == null) emptyList() else allRules.filter { it.groupName == existing.name }
            val leaving = currentMembers.filter { it.packageName !in memberPackages } +
                allRules.filter { it.packageName in memberPackages && it.groupName != null && it.groupName != existing?.name }
            for (rule in leaving) {
                locks.refusalToFree(rule)?.let {
                    _message.value = it
                    return@launch
                }
            }

            val renamed = existing != null && existing.name != trimmed
            if ((existing == null || renamed) && repository.getGroupByName(trimmed) != null) {
                _message.value = "A block named \"$trimmed\" already exists."
                return@launch
            }
            if (existing == null) {
                val id = repository.insertGroup(desired)
                if (id == -1L) {
                    _message.value = "A block named \"$trimmed\" already exists."
                    return@launch
                }
            } else {
                repository.updateGroup(desired.copy(id = existing.id))
                if (renamed) {
                    // Its apps move to the new name, and so does everything that
                    // names this block: sleep mode, focus sessions, routines and
                    // the calendar setting would otherwise stop blocking it.
                    repository.renameGroupMembers(existing.name, trimmed)
                    groupReferences.rename(existing.name, trimmed)
                    if (_selectedGroup.value == existing.name) _selectedGroup.value = trimmed
                }
            }

            // Sync membership to the desired set.
            val nowMembers = repository.getAllAppsOnce().filter { it.groupName == trimmed }
            // Remove apps no longer selected.
            nowMembers.filter { it.packageName !in memberPackages }
                .forEach { repository.leaveGroup(it) }
            // Add newly selected apps.
            memberPackages.forEach { pkg ->
                val rule = repository.getByPackageName(pkg)
                if (rule == null) {
                    repository.insertApp(
                        BlockedAppEntity(
                            packageName = pkg,
                            appName = form.memberDisplayNames[pkg] ?: pkg,
                            isBlocked = true,
                            groupName = trimmed,
                        )
                    )
                } else if (rule.groupName != trimmed) {
                    repository.setAppGroup(pkg, trimmed)
                }
            }
            onSaved()
        }
    }

    fun deleteGroup(groupRow: BlockerGroupRow, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            // Deleting a block frees its apps, like its switch does.
            locks.refusalToFreeGroup(groupRow.group.name)?.let {
                _message.value = it
                return@launch
            }
            repository.deleteGroup(groupRow.group)
            if (_selectedGroup.value == groupRow.group.name) _selectedGroup.value = null
            onDeleted()
        }
    }

    suspend fun installedAppsSnapshot(): List<InstalledApp> = installedAppsHelper.getInstalledApps()
}
