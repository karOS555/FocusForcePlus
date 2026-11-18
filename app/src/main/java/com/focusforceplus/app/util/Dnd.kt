package com.focusforceplus.app.util

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit

/**
 * The one place that switches Do Not Disturb on and back off.
 *
 * Focus sessions, sleep mode and routines can overlap (a bedtime routine that
 * runs into the night, a focus session during a routine), so each of them is a
 * holder, and Do Not Disturb goes off only when the last one is done. Kept in
 * SharedPreferences and written at once, so it survives the app being killed.
 *
 * Android 15 and later: the app has its own mode ("Do Not Disturb (FocusForce+)"),
 * separate from the user's own Do Not Disturb. Switching off then only ever
 * switches that mode off; the user's own stays as it is. Putting back an earlier
 * state there would leave the app's mode on for good.
 *
 * Before Android 15 there is one global switch: what was set before is
 * remembered when the first holder arrives and put back when the last one
 * leaves, only if it is still what was set here (a change made by hand wins),
 * and a stronger mode the user set by hand is never weakened.
 */
object Dnd {
    const val FOCUS = "focus"
    const val SLEEP = "sleep"
    const val ROUTINE = "routine"

    private const val PREFS = "dnd_holders"
    private const val KEY_PREVIOUS = "previous"
    private const val KEY_APPLIED = "applied"
    private const val HOLDER = "holder_"
    private val lock = Any()

    /**
     * Switches Do Not Disturb on for [holder]. [knownPrevious]: the state from
     * before, when the caller already knows it better than the current one does
     * (a night that an older version of the app started). False without access.
     */
    fun acquire(
        context: Context,
        holder: String,
        filter: Int = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        knownPrevious: Int? = null,
    ): Boolean {
        val nm = manager(context)
        if (!nm.isNotificationPolicyAccessGranted) return false
        synchronized(lock) {
            val prefs = prefs(context)
            val holders = holders(prefs)
            val current = nm.currentInterruptionFilter
            val target = if (ownMode) strongest(holders.values + filter)
                         else strongest(holders.values + filter + current)
            val switched = runCatching {
                if (ownMode || target != current) nm.setInterruptionFilter(target)
            }.isSuccess
            if (!switched) return false
            prefs.edit(commit = true) {
                if (holders.isEmpty()) putInt(KEY_PREVIOUS, knownPrevious ?: current)
                putInt(HOLDER + holder, filter)
                putInt(KEY_APPLIED, target)
            }
            return true
        }
    }

    /**
     * [holder] is done. While others still hold Do Not Disturb it stays on (only as
     * strong as they still ask for); the last one puts back what was there before.
     * [legacyApplied] and [legacyPrevious]: what an older version of the app kept
     * itself, for something that was running during the update.
     */
    fun release(
        context: Context,
        holder: String,
        legacyApplied: Int? = null,
        legacyPrevious: Int? = null,
    ) {
        val nm = manager(context)
        val granted = nm.isNotificationPolicyAccessGranted
        synchronized(lock) {
            val prefs = prefs(context)
            val holders = holders(prefs)
            if (holder !in holders) {
                if (granted && holders.isEmpty() && legacyApplied != null && legacyPrevious != null) {
                    if (ownMode) {
                        runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
                    } else if (nm.currentInterruptionFilter == legacyApplied) {
                        runCatching { nm.setInterruptionFilter(legacyPrevious) }
                    }
                }
                return
            }
            val rest = holders - holder
            val applied = prefs.getInt(KEY_APPLIED, -1)
            val previous = prefs.getInt(KEY_PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL)
            val untouched = granted && nm.currentInterruptionFilter == applied
            if (rest.isEmpty()) {
                when {
                    !granted -> Unit
                    ownMode -> runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
                    untouched -> runCatching { nm.setInterruptionFilter(previous) }
                }
                prefs.edit(commit = true) { clear() }
                return
            }
            val target = if (ownMode) strongest(rest.values) else strongest(rest.values + previous)
            var nowApplied = applied
            if ((ownMode && granted || untouched) && target != applied &&
                runCatching { nm.setInterruptionFilter(target) }.isSuccess
            ) {
                nowApplied = target
            }
            prefs.edit(commit = true) {
                remove(HOLDER + holder)
                putInt(KEY_APPLIED, nowApplied)
            }
        }
    }

    /** Android 15 and later: the app switches only its own Do Not Disturb mode. */
    private val ownMode: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    /**
     * Android 15 and later: switches the app's own mode off while nothing holds
     * it, e.g. one an older version of the app left on for good.
     */
    fun clearStale(context: Context) {
        if (!ownMode) return
        val nm = manager(context)
        if (!nm.isNotificationPolicyAccessGranted) return
        synchronized(lock) {
            if (holders(prefs(context)).isEmpty()) {
                runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
            }
        }
    }

    /** True while [holder] has Do Not Disturb switched on. */
    fun holds(context: Context, holder: String): Boolean = prefs(context).contains(HOLDER + holder)

    private fun holders(prefs: SharedPreferences): Map<String, Int> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(HOLDER) && value is Int) key.removePrefix(HOLDER) to value else null
        }.toMap()

    /** Total silence, then alarms only, then priority only, then everything. */
    private fun rank(filter: Int): Int = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_NONE -> 3
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> 2
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> 1
        else -> 0
    }

    private fun strongest(filters: Collection<Int>): Int =
        filters.filter { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }.maxByOrNull(::rank)
            ?: NotificationManager.INTERRUPTION_FILTER_ALL

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
