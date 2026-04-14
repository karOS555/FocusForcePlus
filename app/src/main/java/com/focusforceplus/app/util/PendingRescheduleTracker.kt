package com.focusforceplus.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists pending one-time reschedule alarms across process restarts. */
@Singleton
class PendingRescheduleTracker @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("pending_reschedule", Context.MODE_PRIVATE)

    fun set(routineId: Long, triggerMillis: Long) {
        prefs.edit().putLong("time_$routineId", triggerMillis).apply()
    }

    fun clear(routineId: Long) {
        prefs.edit().remove("time_$routineId").apply()
    }

    /** Returns the trigger time only if it is still in the future, otherwise clears and returns null. */
    fun get(routineId: Long): Long? {
        val v = prefs.getLong("time_$routineId", -1L)
        if (v <= 0L) return null
        return if (v > System.currentTimeMillis()) v else { clear(routineId); null }
    }
}
