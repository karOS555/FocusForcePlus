package com.focusforceplus.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusforceplus.app.util.AlarmRearmer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Periodic safety net: re-arms all alarms twice a day. AlarmManager alarms can be
 * silently dropped by aggressive OEM battery managers or edge cases the explicit
 * triggers (boot, update, time change) don't cover — for an app whose entire value
 * is "the reminder actually fires", a cheap re-arm pass is worth it. Re-arming is
 * idempotent (same PendingIntents, FLAG_UPDATE_CURRENT), so this never duplicates.
 */
class AlarmGuardWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun alarmRearmer(): AlarmRearmer
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .alarmRearmer()
            .rearmAll()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "alarm_guard"
    }
}
