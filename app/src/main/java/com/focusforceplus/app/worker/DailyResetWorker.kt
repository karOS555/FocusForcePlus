package com.focusforceplus.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusforceplus.app.data.repository.BlockerRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Calendar

/**
 * Runs around midnight and resets the blocker's daily state: usage counters and the
 * per-app exception budget. This reset is also the "natural end" that releases
 * invincible-locked daily-limit rules (compliance state machine, section 2.1).
 */
class DailyResetWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun blockerRepository(): BlockerRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .blockerRepository()
        repository.resetDailyUsage()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "blocker_daily_reset"

        /** Millis from now until the next local midnight (+1 min safety margin). */
        fun initialDelayMillis(): Long {
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return next.timeInMillis - System.currentTimeMillis()
        }
    }
}
