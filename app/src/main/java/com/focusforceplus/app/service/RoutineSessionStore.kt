package com.focusforceplus.app.service

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Minimal persisted snapshot of a running routine session. */
data class RoutineSessionSnapshot(
    val routineId: Long,
    val routineName: String,
    val taskIndex: Int,
    /** Wall-clock end of the current task (or when it ended, once in overtime). */
    val taskEndWallMillis: Long,
    val taskTotalSeconds: Int,
    val inOvertime: Boolean,
    val accumulatedOvertimeSeconds: Int,
)

/**
 * Crash/kill insurance for the routine session. [RoutineForegroundService] writes a
 * snapshot on task transitions (not every tick — cheap on battery and flash). If the
 * process dies mid-session, the task-end safety alarm restarts the service, which
 * restores from here and fires the wake-up the user would otherwise have missed.
 * A reboot invalidates the session; [BootReceiver] then clears the snapshot and
 * posts an honest "routine was interrupted" notification.
 */
@Singleton
class RoutineSessionStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("routine_session_store", Context.MODE_PRIVATE)

    fun save(snapshot: RoutineSessionSnapshot) {
        prefs.edit {
            putLong(KEY_ROUTINE_ID, snapshot.routineId)
            putString(KEY_ROUTINE_NAME, snapshot.routineName)
            putInt(KEY_TASK_INDEX, snapshot.taskIndex)
            putLong(KEY_TASK_END_WALL, snapshot.taskEndWallMillis)
            putInt(KEY_TASK_TOTAL, snapshot.taskTotalSeconds)
            putBoolean(KEY_IN_OVERTIME, snapshot.inOvertime)
            putInt(KEY_ACCUM_OVERTIME, snapshot.accumulatedOvertimeSeconds)
        }
    }

    fun load(): RoutineSessionSnapshot? {
        val routineId = prefs.getLong(KEY_ROUTINE_ID, 0L)
        if (routineId <= 0L) return null
        return RoutineSessionSnapshot(
            routineId = routineId,
            routineName = prefs.getString(KEY_ROUTINE_NAME, "") ?: "",
            taskIndex = prefs.getInt(KEY_TASK_INDEX, 0),
            taskEndWallMillis = prefs.getLong(KEY_TASK_END_WALL, 0L),
            taskTotalSeconds = prefs.getInt(KEY_TASK_TOTAL, 0),
            inOvertime = prefs.getBoolean(KEY_IN_OVERTIME, false),
            accumulatedOvertimeSeconds = prefs.getInt(KEY_ACCUM_OVERTIME, 0),
        )
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val KEY_ROUTINE_ID = "routineId"
        const val KEY_ROUTINE_NAME = "routineName"
        const val KEY_TASK_INDEX = "taskIndex"
        const val KEY_TASK_END_WALL = "taskEndWallMillis"
        const val KEY_TASK_TOTAL = "taskTotalSeconds"
        const val KEY_IN_OVERTIME = "inOvertime"
        const val KEY_ACCUM_OVERTIME = "accumulatedOvertimeSeconds"
    }
}
