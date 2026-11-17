package com.focusforceplus.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.focusforceplus.app.ui.navigation.AppModule
import com.focusforceplus.app.ui.navigation.NavConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists how the user arranged the navigation bar and which modules are
 * switched off. Stored by [AppModule.key], so a module added in a later version is
 * simply appended to an existing order instead of breaking it.
 */
@Singleton
class NavRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private companion object {
        val ORDER_KEY = stringPreferencesKey("nav_order")
        val HIDDEN_KEY = stringSetPreferencesKey("nav_hidden")
        val OFF_KEY = stringSetPreferencesKey("modules_off")
        val DOCK_HINT_PLAYS_KEY = intPreferencesKey("dock_hint_plays")
        val DOCK_SCROLLED_KEY = booleanPreferencesKey("dock_scrolled_once")

        /** How often the bar demonstrates that it scrolls before giving up. */
        const val MAX_HINT_PLAYS = 4
    }

    val config: Flow<NavConfig> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val stored = prefs[ORDER_KEY]
                ?.split(',')
                ?.mapNotNull { AppModule.fromKey(it) }
                ?.distinct()
                .orEmpty()
            val order = stored + AppModule.entries.filterNot { it in stored }
            val hidden = prefs[HIDDEN_KEY]
                ?.mapNotNull { AppModule.fromKey(it) }
                ?.filter { it.canHide }
                ?.toSet()
                ?: AppModule.entries.filterNot { it.shownByDefault }.toSet()
            val off = prefs[OFF_KEY]
                ?.mapNotNull { AppModule.fromKey(it) }
                ?.filter { it.isFeature }
                ?.toSet()
                .orEmpty()
            NavConfig(order = order, hidden = hidden, off = off)
        }
        .distinctUntilChanged()

    suspend fun current(): NavConfig = config.first()

    suspend fun isModuleOn(module: AppModule): Boolean = current().isOn(module)

    suspend fun saveOrder(order: List<AppModule>) {
        dataStore.edit { it[ORDER_KEY] = order.joinToString(",") { m -> m.key } }
    }

    suspend fun setHidden(module: AppModule, hidden: Boolean) {
        if (!module.canHide) return
        val current = current().hidden
        val next = if (hidden) current + module else current - module
        dataStore.edit { it[HIDDEN_KEY] = next.map { m -> m.key }.toSet() }
    }

    /** Only records the flag. Pausing and re-arming goes through ModuleController. */
    suspend fun setOff(module: AppModule, off: Boolean) {
        if (!module.isFeature) return
        val current = current().off
        val next = if (off) current + module else current - module
        dataStore.edit { it[OFF_KEY] = next.map { m -> m.key }.toSet() }
    }

    /** Writes a whole setup at once, for restoring a backup. */
    suspend fun restore(config: NavConfig) {
        dataStore.edit {
            it[ORDER_KEY] = config.order.joinToString(",") { m -> m.key }
            it[HIDDEN_KEY] = config.hidden.filter { m -> m.canHide }.map { m -> m.key }.toSet()
            it[OFF_KEY] = config.off.filter { m -> m.isFeature }.map { m -> m.key }.toSet()
        }
    }

    suspend fun resetToDefaults() {
        dataStore.edit {
            it.remove(ORDER_KEY)
            it.remove(HIDDEN_KEY)
        }
    }

    // ── Scroll hint of the bar ────────────────────────────────────────────────

    /** True while the bar should still demonstrate that it can be swiped. */
    val showDockHint: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            prefs[DOCK_SCROLLED_KEY] != true && (prefs[DOCK_HINT_PLAYS_KEY] ?: 0) < MAX_HINT_PLAYS
        }
        .distinctUntilChanged()

    suspend fun noteDockHintPlayed() {
        dataStore.edit { it[DOCK_HINT_PLAYS_KEY] = (it[DOCK_HINT_PLAYS_KEY] ?: 0) + 1 }
    }

    suspend fun noteDockScrolledByUser() {
        dataStore.edit { it[DOCK_SCROLLED_KEY] = true }
    }
}
