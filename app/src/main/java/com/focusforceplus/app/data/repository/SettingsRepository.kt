package com.focusforceplus.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

        private val BLOCKER_ENABLED_KEY            = booleanPreferencesKey("blocker_enabled")
        private val BLOCK_DURING_ROUTINES_KEY      = booleanPreferencesKey("blocker_during_routines")
        private val BLOCK_DURING_FOCUS_KEY         = booleanPreferencesKey("blocker_during_focus")
        private val ALARM_SOUND_ENABLED_KEY        = booleanPreferencesKey("alarm_sound_enabled")
        private val ALARM_VIBRATION_ENABLED_KEY    = booleanPreferencesKey("alarm_vibration_enabled")
        private val ONBOARDING_COMPLETED_KEY       = booleanPreferencesKey("onboarding_completed")
        private val HELP_HINTS_SEEN_KEY            = stringSetPreferencesKey("help_hints_seen")

        private val TP_ENABLED_KEY                 = booleanPreferencesKey("tp_enabled")
        private val TP_WINDOW_START_KEY            = intPreferencesKey("tp_window_start_minutes")
        private val TP_WINDOW_DURATION_KEY         = intPreferencesKey("tp_window_duration_minutes")
        private val TP_ANCHOR_WALL_KEY             = longPreferencesKey("tp_anchor_wall_millis")
        private val TP_ANCHOR_ELAPSED_KEY          = longPreferencesKey("tp_anchor_elapsed_millis")

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

    // ── App blocker ───────────────────────────────────────────────────────────

    /** Master switch for the app blocker (default off — blocking is strictly opt-in). */
    val blockerEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[BLOCKER_ENABLED_KEY] ?: false }

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
}
