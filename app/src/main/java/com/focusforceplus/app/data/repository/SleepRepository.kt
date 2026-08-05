package com.focusforceplus.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's sleep schedule. One schedule per device — bedtime, wake-up, and what
 * happens in between (wind-down reminder, DND, group blocking, linked routines,
 * native backup alarms).
 */
data class SleepSchedule(
    val enabled: Boolean = false,
    /** Bedtime, minutes since midnight. */
    val bedtimeMinutes: Int = 23 * 60,
    /** Wake-up, minutes since midnight. */
    val wakeMinutes: Int = 7 * 60,
    /** Days the schedule applies (day of the bedtime), CSV of routine day codes; null = every day. */
    val activeDaysCsv: String? = null,
    /** Minutes before bedtime for the wind-down reminder; 0 = off. */
    val windDownMinutes: Int = 30,
    val dndEnabled: Boolean = true,
    /** Blocker groups blocked during the sleep window; null/empty = no blocking. */
    val blockedGroupsCsv: String? = null,
    /** Locks the schedule against weakening while the sleep window is running. */
    val invincibleMode: Boolean = false,
    /** Routine fired at bedtime (full alarm flow); 0 = none. */
    val bedtimeRoutineId: Long = 0L,
    /** Routine fired at wake-up (full alarm flow); 0 = none. */
    val morningRoutineId: Long = 0L,
    /** Backup wake-up alarms created in the system clock app; 0 = off, max 3. */
    val nativeAlarmCount: Int = 0,
    /** Gap between the backup alarms in minutes. */
    val nativeAlarmGapMinutes: Int = 10,
) {
    val blockedGroups: Set<String>
        get() = blockedGroupsCsv?.split('|')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
}

@Singleton
class SleepRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val ENABLED_KEY          = booleanPreferencesKey("sleep_enabled")
        private val BEDTIME_KEY          = intPreferencesKey("sleep_bedtime_minutes")
        private val WAKE_KEY             = intPreferencesKey("sleep_wake_minutes")
        private val DAYS_KEY             = stringPreferencesKey("sleep_days_csv")
        private val WIND_DOWN_KEY        = intPreferencesKey("sleep_wind_down_minutes")
        private val DND_KEY              = booleanPreferencesKey("sleep_dnd_enabled")
        private val BLOCKED_GROUPS_KEY   = stringPreferencesKey("sleep_blocked_groups")
        private val INVINCIBLE_KEY       = booleanPreferencesKey("sleep_invincible")
        private val BEDTIME_ROUTINE_KEY  = longPreferencesKey("sleep_bedtime_routine_id")
        private val MORNING_ROUTINE_KEY  = longPreferencesKey("sleep_morning_routine_id")
        private val NATIVE_COUNT_KEY     = intPreferencesKey("sleep_native_alarm_count")
        private val NATIVE_GAP_KEY       = intPreferencesKey("sleep_native_alarm_gap")
        // Runtime night state (not user settings)
        private val NIGHT_ACTIVE_KEY     = booleanPreferencesKey("sleep_night_active")
        private val PREV_DND_FILTER_KEY  = intPreferencesKey("sleep_prev_dnd_filter")
    }

    val schedule: Flow<SleepSchedule> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            SleepSchedule(
                enabled = prefs[ENABLED_KEY] ?: false,
                bedtimeMinutes = prefs[BEDTIME_KEY] ?: 23 * 60,
                wakeMinutes = prefs[WAKE_KEY] ?: 7 * 60,
                activeDaysCsv = prefs[DAYS_KEY]?.takeIf { it.isNotBlank() },
                windDownMinutes = prefs[WIND_DOWN_KEY] ?: 30,
                dndEnabled = prefs[DND_KEY] ?: true,
                blockedGroupsCsv = prefs[BLOCKED_GROUPS_KEY]?.takeIf { it.isNotBlank() },
                invincibleMode = prefs[INVINCIBLE_KEY] ?: false,
                bedtimeRoutineId = prefs[BEDTIME_ROUTINE_KEY] ?: 0L,
                morningRoutineId = prefs[MORNING_ROUTINE_KEY] ?: 0L,
                nativeAlarmCount = prefs[NATIVE_COUNT_KEY] ?: 0,
                nativeAlarmGapMinutes = prefs[NATIVE_GAP_KEY] ?: 10,
            )
        }

    suspend fun saveSchedule(schedule: SleepSchedule) {
        dataStore.edit { prefs ->
            prefs[ENABLED_KEY] = schedule.enabled
            prefs[BEDTIME_KEY] = schedule.bedtimeMinutes
            prefs[WAKE_KEY] = schedule.wakeMinutes
            prefs[DAYS_KEY] = schedule.activeDaysCsv ?: ""
            prefs[WIND_DOWN_KEY] = schedule.windDownMinutes
            prefs[DND_KEY] = schedule.dndEnabled
            prefs[BLOCKED_GROUPS_KEY] = schedule.blockedGroupsCsv ?: ""
            prefs[INVINCIBLE_KEY] = schedule.invincibleMode
            prefs[BEDTIME_ROUTINE_KEY] = schedule.bedtimeRoutineId
            prefs[MORNING_ROUTINE_KEY] = schedule.morningRoutineId
            prefs[NATIVE_COUNT_KEY] = schedule.nativeAlarmCount.coerceIn(0, 3)
            prefs[NATIVE_GAP_KEY] = schedule.nativeAlarmGapMinutes.coerceIn(1, 30)
        }
    }

    suspend fun setBedtimeRoutineId(routineId: Long) {
        dataStore.edit { prefs -> prefs[BEDTIME_ROUTINE_KEY] = routineId }
    }

    suspend fun setMorningRoutineId(routineId: Long) {
        dataStore.edit { prefs -> prefs[MORNING_ROUTINE_KEY] = routineId }
    }

    // ── Runtime night state ───────────────────────────────────────────────────

    /** True between a fired bedtime and the matching wake-up. */
    val nightActive: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[NIGHT_ACTIVE_KEY] ?: false }

    suspend fun setNightActive(active: Boolean) {
        dataStore.edit { prefs -> prefs[NIGHT_ACTIVE_KEY] = active }
    }

    /** DND interruption filter to restore at wake-up; -1 = none saved. */
    val previousDndFilter: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[PREV_DND_FILTER_KEY] ?: -1 }

    suspend fun setPreviousDndFilter(filter: Int) {
        dataStore.edit { prefs -> prefs[PREV_DND_FILTER_KEY] = filter }
    }
}
