package com.focusforceplus.app.ui.screens.blocker

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.NEVER_BLOCK_PACKAGES
import com.focusforceplus.app.compliance.TamperProtectionGuard
import com.focusforceplus.app.compliance.isGroupLocked
import com.focusforceplus.app.compliance.isRuleLocked
import com.focusforceplus.app.compliance.validateBlockWindow
import com.focusforceplus.app.compliance.weakensInvincibleGroup
import com.focusforceplus.app.compliance.weakensInvincibleRule
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.SettingsRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

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

data class BlockerListUiState(
    val rows: List<BlockerAppRow> = emptyList(),
    val searchQuery: String = "",
    val groups: List<BlockerGroupRow> = emptyList(),
    val selectedGroup: String? = null,
    val accessibilityEnabled: Boolean = false,
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
) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedGroup = MutableStateFlow<String?>(null)
    private val _permissions = MutableStateFlow(false to false) // accessibility, usage
    private val _isLoadingApps = MutableStateFlow(true)
    private val _message = MutableStateFlow<String?>(null)

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
            val groupRows = groups.map { g ->
                val members = rules.filter { it.groupName == g.name }
                val used = members.sumOf { it.usedTodayMinutes }
                BlockerGroupRow(
                    group = g,
                    memberCount = members.size,
                    usedTodayMinutes = used,
                    locked = isGroupLocked(g, used),
                )
            }

            val sorted = rows.sortedWith(
                compareByDescending<BlockerAppRow> { it.isBlocked }
                    .thenBy { it.appName.lowercase() }
            )
            sorted to groupRows
        },
        _permissions,
        settingsRepository.blockerEnabled,
        _isLoadingApps,
        _message,
    ) { (rows, groups), perms, blockerEnabled, loading, message ->
        BlockerListUiState(
            rows = rows,
            searchQuery = _searchQuery.value,
            groups = groups,
            selectedGroup = _selectedGroup.value,
            accessibilityEnabled = perms.first,
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
                rule.isBlocked && isRuleLocked(rule, minutesOfDay()) ->
                    _message.value = lockedMessage()
                rule.isBlocked && rule.invincibleMode -> {
                    // Turning an invincible rule off is a protected change (TP).
                    val verdict = tamperGuard.checkProtectedChange()
                    if (verdict.allowed) repository.updateApp(rule.copy(isBlocked = false))
                    else _message.value = verdict.message
                }
                else -> repository.updateApp(rule.copy(isBlocked = !rule.isBlocked))
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
            if (isRuleLocked(rule, minutesOfDay())) {
                _message.value = lockedMessage()
                return@launch
            }
            if (rule.invincibleMode) {
                val verdict = tamperGuard.checkProtectedChange()
                if (!verdict.allowed) {
                    _message.value = verdict.message
                    return@launch
                }
            }
            repository.deleteApp(rule)
        }
    }

    /** Enables/disables every unlocked rule of [group] (locked rules are skipped). */
    fun setGroupBlocked(group: String, blocked: Boolean) {
        viewModelScope.launch {
            var skipped = 0
            repository.getAllAppsOnce()
                .filter { it.groupName == group }
                .forEach { rule ->
                    if (isRuleLocked(rule, minutesOfDay()) && !blocked) {
                        skipped++
                    } else if (rule.isBlocked != blocked) {
                        repository.updateApp(rule.copy(isBlocked = blocked))
                    }
                }
            if (skipped > 0) {
                _message.value = "$skipped rule(s) stayed on — they are locked by Invincible Mode until their natural end."
            }
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
    fun saveGroup(
        originalName: String?,
        name: String,
        sharedLimitMinutes: Int?,
        invincible: Boolean,
        memberPackages: Set<String>,
        memberDisplayNames: Map<String, String>,
    ) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                _message.value = "Give the group a name."
                return@launch
            }
            val existing = originalName?.let { repository.getGroupByName(it) }
            val desired = BlockerGroupEntity(
                id = existing?.id ?: 0L,
                name = trimmed,
                sharedDailyLimitMinutes = sharedLimitMinutes,
                invincibleMode = invincible && sharedLimitMinutes != null,
            )

            // Weakening an invincible group (limit up/removed, invincible off) is TP-gated.
            if (existing != null && weakensInvincibleGroup(existing, desired)) {
                val verdict = tamperGuard.checkProtectedChange()
                if (!verdict.allowed) {
                    _message.value = verdict.message
                    return@launch
                }
            }
            // While locked, membership must not change either (would free members).
            val currentMembers = repository.getAllAppsOnce().filter { it.groupName == originalName }
            if (existing != null && isGroupLocked(existing, currentMembers.sumOf { it.usedTodayMinutes })) {
                val leaving = currentMembers.any { it.packageName !in memberPackages }
                if (leaving) {
                    val verdict = tamperGuard.checkProtectedChange()
                    if (!verdict.allowed) {
                        _message.value = verdict.message
                        return@launch
                    }
                }
            }

            if (existing == null) {
                val id = repository.insertGroup(desired)
                if (id == -1L) {
                    _message.value = "A group named \"$trimmed\" already exists."
                    return@launch
                }
            } else {
                repository.updateGroup(desired.copy(id = existing.id))
                // Handle rename: move members to the new name.
                if (existing.name != trimmed) {
                    currentMembers.forEach { repository.setAppGroup(it.packageName, trimmed) }
                }
            }

            // Sync membership to the desired set.
            val effectiveOldName = existing?.let { if (it.name != trimmed) trimmed else it.name } ?: trimmed
            val nowMembers = repository.getAllAppsOnce().filter { it.groupName == effectiveOldName }
            // Remove apps no longer selected.
            nowMembers.filter { it.packageName !in memberPackages }
                .forEach { repository.setAppGroup(it.packageName, null) }
            // Add newly selected apps.
            memberPackages.forEach { pkg ->
                val rule = repository.getByPackageName(pkg)
                if (rule == null) {
                    repository.insertApp(
                        BlockedAppEntity(
                            packageName = pkg,
                            appName = memberDisplayNames[pkg] ?: pkg,
                            isBlocked = true,
                            groupName = trimmed,
                        )
                    )
                } else if (rule.groupName != trimmed) {
                    repository.setAppGroup(pkg, trimmed)
                }
            }
        }
    }

    fun deleteGroup(groupRow: BlockerGroupRow) {
        viewModelScope.launch {
            if (groupRow.locked) {
                val verdict = tamperGuard.checkProtectedChange()
                if (!verdict.allowed) {
                    _message.value = verdict.message
                    return@launch
                }
            }
            repository.deleteGroup(groupRow.group)
            if (_selectedGroup.value == groupRow.group.name) _selectedGroup.value = null
        }
    }

    suspend fun installedAppsSnapshot(): List<InstalledApp> = installedAppsHelper.getInstalledApps()
}
