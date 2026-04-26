package com.focusforceplus.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
}
