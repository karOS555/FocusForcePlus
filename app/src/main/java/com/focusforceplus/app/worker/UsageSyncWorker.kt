package com.focusforceplus.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusforceplus.app.util.UsageTracker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Periodically mirrors today's usage into the blocked-apps table so daily-limit
 * state and the numbers in the blocker UI stay current even when the accessibility
 * service has no reason to refresh them (e.g. blocked app not opened for a while).
 */
class UsageSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun usageTracker(): UsageTracker
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .usageTracker()
            .syncBlockedAppUsage()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "blocker_usage_sync"
    }
}
