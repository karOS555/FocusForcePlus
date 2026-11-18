package com.focusforceplus.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusforceplus.app.data.tasks.TaskSync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Picks up changes made in the tasks app while FocusForce+ was not running. Does
 * nothing when no task sync is set up.
 */
class TaskSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun taskSync(): TaskSync
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .taskSync()
            .syncNow()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "task_sync"
    }
}
