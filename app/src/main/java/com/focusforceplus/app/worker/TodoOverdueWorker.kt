package com.focusforceplus.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.ListRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Periodic housekeeping, a few times a day.
 *
 * It empties whatever has outstayed its time in the trash, moves old finished
 * todos there if the user asked for that, and lets the todo engine put every
 * reminder back in line. The last part is a safety net: reminders are driven by
 * alarms, and this catches the rare alarm a battery manager dropped.
 *
 * The class keeps its old name because WorkManager stores the name of the worker
 * it has scheduled; renaming it would orphan the work on existing installs.
 */
class TodoOverdueWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun todoReminderEngine(): TodoReminderEngine
        fun routineRepository(): RoutineRepository
        fun focusRepository(): FocusRepository
        fun listRepository(): ListRepository
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val engine = ep.todoReminderEngine()

        runCatching { engine.cleanUp() }
        runCatching {
            val days = ep.settingsRepository().trashRetentionDays.first()
            val before = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
            ep.routineRepository().purgeTrashedBefore(before)
            ep.focusRepository().purgeTrashedBefore(before)
            ep.listRepository().purgeTrashedBefore(before)
        }
        runCatching { engine.syncAll() }

        return Result.success()
    }
}
