package com.focusforceplus.app.data.repository

import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.focusforceplus.app.compliance.ComplianceLimits
import com.focusforceplus.app.compliance.Guard
import com.focusforceplus.app.compliance.GuardSettings
import com.focusforceplus.app.compliance.GuardSlot
import com.focusforceplus.app.compliance.NagPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val DIGEST_TIMES_KEY    = stringSetPreferencesKey("todo_digest_times")
        private val DEFAULT_PRIORITY_KEY = intPreferencesKey("todo_default_priority")
        private val AUTO_DELETE_DAYS_KEY = intPreferencesKey("todo_auto_delete_days")
        private val UNDATED_ALARM_GAP_KEY = intPreferencesKey("todo_undated_alarm_gap")

        const val DEFAULT_UNDATED_ALARM_GAP_MINUTES = 5

        private val BLOCKER_ENABLED_KEY            = booleanPreferencesKey("blocker_enabled")
        private val SHORTS_INSTAGRAM_KEY           = booleanPreferencesKey("shorts_block_instagram")
        private val EXTERNAL_STARTS_KEY            = booleanPreferencesKey("external_starts")
        private val THEME_KEY                      = stringPreferencesKey("theme")
        private val SAVED_THEMES_KEY               = stringPreferencesKey("saved_themes")
        private val CALENDAR_HOME_KEY              = booleanPreferencesKey("calendar_on_home")
        private val CALENDAR_IDS_KEY               = stringPreferencesKey("calendar_ids")
        private val CALENDAR_TODO_TARGET_KEY       = longPreferencesKey("calendar_todo_target")
        private val CALENDAR_BLOCK_GROUPS_KEY      = stringPreferencesKey("calendar_block_groups")
        private val TASK_SYNC_AUTHORITY_KEY        = stringPreferencesKey("task_sync_authority")
        private val TASK_SYNC_LIST_KEY             = longPreferencesKey("task_sync_list")
        private val TASK_SYNC_SEEN_KEY             = stringPreferencesKey("task_sync_seen")
        private val SHORTS_YOUTUBE_KEY             = booleanPreferencesKey("shorts_block_youtube")
        private val BLOCK_DURING_ROUTINES_KEY      = booleanPreferencesKey("blocker_during_routines")
        private val BLOCK_DURING_FOCUS_KEY         = booleanPreferencesKey("blocker_during_focus")
        private val ALARM_SOUND_ENABLED_KEY        = booleanPreferencesKey("alarm_sound_enabled")
        private val ALARM_VIBRATION_ENABLED_KEY    = booleanPreferencesKey("alarm_vibration_enabled")
        private val ONBOARDING_COMPLETED_KEY       = booleanPreferencesKey("onboarding_completed")
        private val HELP_HINTS_SEEN_KEY            = stringSetPreferencesKey("help_hints_seen")
        private val FSI_PROMPT_SHOWN_KEY           = booleanPreferencesKey("fsi_prompt_shown")
        private val LEGACY_TODO_ALARMS_RETIRED_KEY = booleanPreferencesKey("legacy_todo_alarms_retired")

        private val TP_ENABLED_KEY                 = booleanPreferencesKey("tp_enabled")
        private val TP_WINDOW_START_KEY            = intPreferencesKey("tp_window_start_minutes")
        private val TP_WINDOW_DURATION_KEY         = intPreferencesKey("tp_window_duration_minutes")
        private val TP_ANCHOR_WALL_KEY             = longPreferencesKey("tp_anchor_wall_millis")
        private val TP_ANCHOR_ELAPSED_KEY          = longPreferencesKey("tp_anchor_elapsed_millis")

        private val GUARDS_ENABLED_KEY     = booleanPreferencesKey("guards_enabled")

        private val NAG_HIGH_INTERVAL_KEY  = intPreferencesKey("todo_nag_high_interval")
        private val NAG_HIGH_REPEATS_KEY   = intPreferencesKey("todo_nag_high_repeats")
        private val NAG_MED_INTERVAL_KEY   = intPreferencesKey("todo_nag_medium_interval")
        private val NAG_MED_REPEATS_KEY    = intPreferencesKey("todo_nag_medium_repeats")
        private val RING_SECONDS_KEY       = intPreferencesKey("todo_ring_seconds")
        private val TRASH_RETENTION_KEY    = intPreferencesKey("trash_retention_days")
        private val AUTO_BACKUP_URI_KEY    = stringPreferencesKey("auto_backup_uri")
        private val AUTO_BACKUP_KEEP_KEY   = intPreferencesKey("auto_backup_keep")
        private val AUTO_BACKUP_LAST_KEY   = longPreferencesKey("auto_backup_last")
        private val AUTO_BACKUP_ERROR_KEY  = stringPreferencesKey("auto_backup_error")

        const val DEFAULT_AUTO_BACKUP_KEEP = 7

        const val DEFAULT_RING_SECONDS = 60
        const val DEFAULT_TRASH_RETENTION_DAYS = 30

        val DEFAULT_DIGEST_TIMES: List<Pair<Int, Int>> = listOf(6 to 0, 18 to 0)

        fun encodeTime(hour: Int, minute: Int) = "$hour:$minute"

        fun decodeTime(s: String): Pair<Int, Int>? {
            val parts = s.split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return if (h in 0..23 && m in 0..59) h to m else null
        }
    }

    // ── Digest reminder times ─────────────────────────────────────────────────

    val digestTimes: Flow<List<Pair<Int, Int>>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[DIGEST_TIMES_KEY]
            if (raw.isNullOrEmpty()) {
                DEFAULT_DIGEST_TIMES
            } else {
                raw.mapNotNull { decodeTime(it) }
                    .sortedBy { it.first * 60 + it.second }
                    .ifEmpty { DEFAULT_DIGEST_TIMES }
            }
        }

    suspend fun saveDigestTimes(times: List<Pair<Int, Int>>) {
        dataStore.edit { prefs ->
            prefs[DIGEST_TIMES_KEY] = times.map { encodeTime(it.first, it.second) }.toSet()
        }
    }

    // ── Default priority for new todos ────────────────────────────────────────

    val defaultPriority: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[DEFAULT_PRIORITY_KEY] ?: 1 }

    suspend fun saveDefaultPriority(priority: Int) {
        dataStore.edit { prefs -> prefs[DEFAULT_PRIORITY_KEY] = priority }
    }

    // ── Auto-delete completed todos (0 = never) ───────────────────────────────

    val autoDeleteDays: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[AUTO_DELETE_DAYS_KEY] ?: 0 }

    suspend fun saveAutoDeleteDays(days: Int) {
        dataStore.edit { prefs -> prefs[AUTO_DELETE_DAYS_KEY] = days }
    }

    // ── Undated-todo alarm spacing ────────────────────────────────────────────

    /**
     * Minutes between the staggered full-screen alarms that fire for undated
     * medium/high todos at each daily reminder time — so several open todos
     * never ring all at once.
     */
    val undatedAlarmGapMinutes: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[UNDATED_ALARM_GAP_KEY] ?: DEFAULT_UNDATED_ALARM_GAP_MINUTES }

    suspend fun saveUndatedAlarmGapMinutes(minutes: Int) {
        dataStore.edit { prefs -> prefs[UNDATED_ALARM_GAP_KEY] = minutes.coerceIn(1, 60) }
    }

    // ── App blocker ───────────────────────────────────────────────────────────

    /** Master switch for the app blocker (default off — blocking is strictly opt-in). */
    val blockerEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[BLOCKER_ENABLED_KEY] ?: false }

    /** The picked theme as JSON (see ui.theme.ThemeSpec); null = the default look. */
    val themeJson: Flow<String?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[THEME_KEY] }

    suspend fun saveThemeJson(json: String?) {
        dataStore.edit { prefs -> if (json == null) prefs.remove(THEME_KEY) else prefs[THEME_KEY] = json }
    }

    // ── Calendar ──────────────────────────────────────────────────────────────

    /** Today's events on Home (only with calendar access). */
    val calendarOnHome: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[CALENDAR_HOME_KEY] ?: true }

    suspend fun saveCalendarOnHome(on: Boolean) {
        dataStore.edit { it[CALENDAR_HOME_KEY] = on }
    }

    /** Calendars FocusForce+ looks at; null = every calendar that is switched on. */
    val calendarIds: Flow<Set<Long>?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[CALENDAR_IDS_KEY]?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet() }

    suspend fun saveCalendarIds(ids: Set<Long>?) {
        dataStore.edit { prefs ->
            if (ids == null) prefs.remove(CALENDAR_IDS_KEY) else prefs[CALENDAR_IDS_KEY] = ids.joinToString(",")
        }
    }

    /** The calendar todos with a date go into; null = none. */
    val calendarTodoTarget: Flow<Long?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[CALENDAR_TODO_TARGET_KEY] }

    suspend fun saveCalendarTodoTarget(calendarId: Long?) {
        dataStore.edit { prefs ->
            if (calendarId == null) prefs.remove(CALENDAR_TODO_TARGET_KEY) else prefs[CALENDAR_TODO_TARGET_KEY] = calendarId
        }
    }

    /** Block groups that block while a busy calendar event is on. */
    val calendarBlockGroups: Flow<Set<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[CALENDAR_BLOCK_GROUPS_KEY]?.split('|')?.filter { it.isNotBlank() }?.toSet().orEmpty() }

    suspend fun saveCalendarBlockGroups(groups: Set<String>) {
        dataStore.edit { prefs ->
            if (groups.isEmpty()) prefs.remove(CALENDAR_BLOCK_GROUPS_KEY)
            else prefs[CALENDAR_BLOCK_GROUPS_KEY] = groups.joinToString("|")
        }
    }

    // ── Tasks app ─────────────────────────────────────────────────────────────

    /** The tasks app and list todos are synced with; null = no sync. */
    val taskSyncTarget: Flow<TaskSyncTarget?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val authority = prefs[TASK_SYNC_AUTHORITY_KEY]
            val list = prefs[TASK_SYNC_LIST_KEY]
            if (authority != null && list != null) TaskSyncTarget(authority, list) else null
        }
        .distinctUntilChanged()

    suspend fun saveTaskSyncTarget(target: TaskSyncTarget?) {
        dataStore.edit { prefs ->
            if (target == null) {
                prefs.remove(TASK_SYNC_AUTHORITY_KEY)
                prefs.remove(TASK_SYNC_LIST_KEY)
            } else {
                prefs[TASK_SYNC_AUTHORITY_KEY] = target.authority
                prefs[TASK_SYNC_LIST_KEY] = target.listId
            }
        }
    }

    /** Task ids the last sync saw in the list; a task not among them is new. */
    val taskSyncSeen: Flow<Set<Long>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[TASK_SYNC_SEEN_KEY]?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty() }

    suspend fun saveTaskSyncSeen(ids: Set<Long>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) prefs.remove(TASK_SYNC_SEEN_KEY) else prefs[TASK_SYNC_SEEN_KEY] = ids.joinToString(",")
        }
    }

    /** The user's own saved themes, as a JSON list (see ui.theme.ThemeSpec.listToJson). */
    val savedThemesJson: Flow<String?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SAVED_THEMES_KEY] }

    suspend fun saveSavedThemesJson(json: String?) {
        dataStore.edit { prefs -> if (json == null) prefs.remove(SAVED_THEMES_KEY) else prefs[SAVED_THEMES_KEY] = json }
    }

    /** Whether start links from other apps, automation and NFC tags may run. Off by default. */
    val externalStartsAllowed: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[EXTERNAL_STARTS_KEY] ?: false }

    suspend fun saveExternalStartsAllowed(allowed: Boolean) {
        dataStore.edit { it[EXTERNAL_STARTS_KEY] = allowed }
    }

    /** Leave Instagram's Reels (needs the separate short-video service). */
    val shortsBlockInstagram: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SHORTS_INSTAGRAM_KEY] ?: false }

    /** Leave YouTube's Shorts (needs the separate short-video service). */
    val shortsBlockYouTube: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SHORTS_YOUTUBE_KEY] ?: false }

    suspend fun saveShortsBlock(instagram: Boolean? = null, youtube: Boolean? = null) {
        dataStore.edit { prefs ->
            instagram?.let { prefs[SHORTS_INSTAGRAM_KEY] = it }
            youtube?.let { prefs[SHORTS_YOUTUBE_KEY] = it }
        }
    }

    suspend fun saveBlockerEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[BLOCKER_ENABLED_KEY] = enabled }
    }

    val blockDuringRoutines: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[BLOCK_DURING_ROUTINES_KEY] ?: true }

    suspend fun saveBlockDuringRoutines(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[BLOCK_DURING_ROUTINES_KEY] = enabled }
    }

    val blockDuringFocus: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[BLOCK_DURING_FOCUS_KEY] ?: true }

    suspend fun saveBlockDuringFocus(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[BLOCK_DURING_FOCUS_KEY] = enabled }
    }

    // ── Alarm sound / vibration (global) ──────────────────────────────────────

    val alarmSoundEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[ALARM_SOUND_ENABLED_KEY] ?: true }

    suspend fun saveAlarmSoundEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ALARM_SOUND_ENABLED_KEY] = enabled }
    }

    val alarmVibrationEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[ALARM_VIBRATION_ENABLED_KEY] ?: true }

    suspend fun saveAlarmVibrationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ALARM_VIBRATION_ENABLED_KEY] = enabled }
    }

    // ── Onboarding ────────────────────────────────────────────────────────────

    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[ONBOARDING_COMPLETED_KEY] ?: false }

    suspend fun saveOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED_KEY] = completed }
    }

    // ── Full-screen alarm permission prompt ───────────────────────────────────

    /**
     * The lock-screen alarm permission is optional and, on some phones, not even
     * grantable — so we offer it exactly once instead of on every app start. It
     * stays reachable in Settings > Permissions.
     */
    val fullScreenIntentPromptShown: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[FSI_PROMPT_SHOWN_KEY] ?: false }

    suspend fun markFullScreenIntentPromptShown() {
        dataStore.edit { prefs -> prefs[FSI_PROMPT_SHOWN_KEY] = true }
    }

    /**
     * Whether the todo alarms and notifications that 0.9.3 and older left behind
     * have been removed. That is done once after the update, see
     * [com.focusforceplus.app.util.TodoReminderEngine].
     */
    val legacyTodoAlarmsRetired: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[LEGACY_TODO_ALARMS_RETIRED_KEY] ?: false }

    suspend fun markLegacyTodoAlarmsRetired() {
        dataStore.edit { prefs -> prefs[LEGACY_TODO_ALARMS_RETIRED_KEY] = true }
    }

    // ── Tamper Protection ─────────────────────────────────────────────────────

    val tpEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[TP_ENABLED_KEY] ?: false }

    /** Daily change-window start (minutes since midnight); default 08:00. */
    val tpWindowStartMinutes: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[TP_WINDOW_START_KEY] ?: 8 * 60 }

    /** Daily change-window length in minutes; default 30. */
    val tpWindowDurationMinutes: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[TP_WINDOW_DURATION_KEY] ?: 30 }

    suspend fun saveTpEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[TP_ENABLED_KEY] = enabled }
    }

    suspend fun saveTpWindow(startMinutes: Int, durationMinutes: Int) {
        dataStore.edit { prefs ->
            prefs[TP_WINDOW_START_KEY] = startMinutes
            prefs[TP_WINDOW_DURATION_KEY] = durationMinutes
        }
    }

    /** Wall-clock/monotonic anchor pair for time-manipulation detection. */
    val tpClockAnchor: Flow<Pair<Long, Long>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> (prefs[TP_ANCHOR_WALL_KEY] ?: 0L) to (prefs[TP_ANCHOR_ELAPSED_KEY] ?: 0L) }

    suspend fun saveTpClockAnchor(wallMillis: Long, elapsedMillis: Long) {
        dataStore.edit { prefs ->
            prefs[TP_ANCHOR_WALL_KEY] = wallMillis
            prefs[TP_ANCHOR_ELAPSED_KEY] = elapsedMillis
        }
    }

    // ── Feature help hints ────────────────────────────────────────────────────

    /** Keys of feature-help hints the user has already seen (first-open bubbles). */
    val helpHintsSeen: Flow<Set<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[HELP_HINTS_SEEN_KEY] ?: emptySet() }

    suspend fun markHelpHintSeen(key: String) {
        dataStore.edit { prefs ->
            prefs[HELP_HINTS_SEEN_KEY] = (prefs[HELP_HINTS_SEEN_KEY] ?: emptySet()) + key
        }
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    /** App-wide defaults for the speed bumps; items may override them. */
    val guardSettings: Flow<GuardSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            GuardSettings(
                enabled = prefs[GUARDS_ENABLED_KEY] ?: true,
                slots = GuardSlot.entries.mapNotNull { slot ->
                    Guard.decode(prefs[guardKey(slot)])?.let { slot to it }
                }.toMap(),
            )
        }

    suspend fun saveGuardsEnabled(enabled: Boolean) {
        dataStore.edit { it[GUARDS_ENABLED_KEY] = enabled }
    }

    suspend fun saveGuard(slot: GuardSlot, guard: Guard) {
        dataStore.edit { it[guardKey(slot)] = guard.clamped().encode() }
    }

    /** One slot back to its built-in default. */
    suspend fun resetGuard(slot: GuardSlot) {
        dataStore.edit { it.remove(guardKey(slot)) }
    }

    /** Every slot back to its built-in default. */
    suspend fun resetGuards() {
        dataStore.edit { prefs -> GuardSlot.entries.forEach { prefs.remove(guardKey(it)) } }
    }

    /** Writes the whole set at once, for restoring a backup. */
    suspend fun saveGuardSettings(settings: GuardSettings) {
        dataStore.edit { prefs ->
            prefs[GUARDS_ENABLED_KEY] = settings.enabled
            GuardSlot.entries.forEach { slot ->
                val guard = settings.slots[slot]
                if (guard == null) prefs.remove(guardKey(slot)) else prefs[guardKey(slot)] = guard.clamped().encode()
            }
        }
    }

    private fun guardKey(slot: GuardSlot) = stringPreferencesKey("guard_${slot.key}")

    // ── Repeated todo reminders ───────────────────────────────────────────────

    /** How an unanswered todo of [priority] keeps coming back. */
    fun nagPolicy(priority: Int): Flow<NagPolicy> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            when (priority) {
                2 -> NagPolicy.clamp(
                    NagPolicy(
                        prefs[NAG_HIGH_INTERVAL_KEY] ?: NagPolicy.HIGH_DEFAULT.intervalMinutes,
                        prefs[NAG_HIGH_REPEATS_KEY] ?: NagPolicy.HIGH_DEFAULT.maxRepeats,
                    )
                )
                1 -> NagPolicy.clamp(
                    NagPolicy(
                        prefs[NAG_MED_INTERVAL_KEY] ?: NagPolicy.MEDIUM_DEFAULT.intervalMinutes,
                        prefs[NAG_MED_REPEATS_KEY] ?: NagPolicy.MEDIUM_DEFAULT.maxRepeats,
                    )
                )
                else -> NagPolicy.NONE
            }
        }

    suspend fun saveNagPolicy(priority: Int, policy: NagPolicy) {
        val clamped = NagPolicy.clamp(policy)
        dataStore.edit { prefs ->
            when (priority) {
                2 -> {
                    prefs[NAG_HIGH_INTERVAL_KEY] = clamped.intervalMinutes
                    prefs[NAG_HIGH_REPEATS_KEY] = clamped.maxRepeats
                }
                1 -> {
                    prefs[NAG_MED_INTERVAL_KEY] = clamped.intervalMinutes
                    prefs[NAG_MED_REPEATS_KEY] = clamped.maxRepeats
                }
            }
        }
    }

    /** How long a high-priority alarm keeps sounding when nobody reacts. */
    val todoRingSeconds: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            (prefs[RING_SECONDS_KEY] ?: DEFAULT_RING_SECONDS).coerceIn(
                ComplianceLimits.MIN_TODO_RING_SECONDS,
                ComplianceLimits.MAX_TODO_RING_SECONDS,
            )
        }

    suspend fun saveTodoRingSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[RING_SECONDS_KEY] = seconds.coerceIn(
                ComplianceLimits.MIN_TODO_RING_SECONDS,
                ComplianceLimits.MAX_TODO_RING_SECONDS,
            )
        }
    }

    // ── Trash ─────────────────────────────────────────────────────────────────

    /** Days a deleted item stays in the trash before it is removed for good. */
    /** Where automatic backups go, or null when they are off. Never part of a backup. */
    data class AutoBackupState(
        val folderUri: String? = null,
        val keep: Int = DEFAULT_AUTO_BACKUP_KEEP,
        val lastAt: Long? = null,
        val lastError: String? = null,
    )

    val autoBackup: Flow<AutoBackupState> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AutoBackupState(
                folderUri = prefs[AUTO_BACKUP_URI_KEY],
                keep = prefs[AUTO_BACKUP_KEEP_KEY] ?: DEFAULT_AUTO_BACKUP_KEEP,
                lastAt = prefs[AUTO_BACKUP_LAST_KEY],
                lastError = prefs[AUTO_BACKUP_ERROR_KEY],
            )
        }

    suspend fun saveAutoBackupFolder(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(AUTO_BACKUP_URI_KEY) else prefs[AUTO_BACKUP_URI_KEY] = uri
            prefs.remove(AUTO_BACKUP_ERROR_KEY)
        }
    }

    suspend fun saveAutoBackupKeep(keep: Int) {
        dataStore.edit { it[AUTO_BACKUP_KEEP_KEY] = keep.coerceIn(1, 60) }
    }

    suspend fun saveAutoBackupResult(at: Long, error: String?) {
        dataStore.edit { prefs ->
            if (error == null) {
                prefs[AUTO_BACKUP_LAST_KEY] = at
                prefs.remove(AUTO_BACKUP_ERROR_KEY)
            } else {
                prefs[AUTO_BACKUP_ERROR_KEY] = error
            }
        }
    }

    val trashRetentionDays: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            (prefs[TRASH_RETENTION_KEY] ?: DEFAULT_TRASH_RETENTION_DAYS)
                .coerceIn(1, ComplianceLimits.MAX_TRASH_RETENTION_DAYS)
        }

    suspend fun saveTrashRetentionDays(days: Int) {
        dataStore.edit { prefs ->
            prefs[TRASH_RETENTION_KEY] = days.coerceIn(1, ComplianceLimits.MAX_TRASH_RETENTION_DAYS)
        }
    }
}

/** A tasks app (by its provider authority) and one of its lists. */
data class TaskSyncTarget(val authority: String, val listId: Long)
