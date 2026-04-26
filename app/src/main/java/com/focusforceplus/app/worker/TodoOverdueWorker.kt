package com.focusforceplus.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.service.TodoReminderForegroundService
import com.focusforceplus.app.util.TodoNotificationHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class TodoOverdueWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun todoRepository(): TodoRepository
        fun todoNotificationHelper(): TodoNotificationHelper
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java,
        )
        val repository       = ep.todoRepository()
        val notifHelper      = ep.todoNotificationHelper()
        val settingsRepo     = ep.settingsRepository()

        val now   = System.currentTimeMillis()

        // Auto-delete completed todos older than the configured threshold
        val autoDeleteDays = settingsRepo.autoDeleteDays.first()
        if (autoDeleteDays > 0) {
            val cutoff = now - autoDeleteDays * 24 * 60 * 60 * 1000L
            repository.deleteCompletedBefore(cutoff)
        }
        val todos = repository.getAllTodos().first()
        val open  = todos.filter { !it.isCompleted }

        val overdue = open.filter { it.dueDateTime != null && it.dueDateTime < now }
        val undated = open.filter { it.dueDateTime == null }

        for (todo in overdue) {
            notifHelper.showNotification(
                TodoNotificationHelper.todoDigestNotificationId(todo.id),
                notifHelper.buildPerTodoReminderNotification(todo.id, todo.title, todo.priority, isOverdue = true),
            )
        }
        for (todo in undated) {
            notifHelper.showNotification(
                TodoNotificationHelper.todoDigestNotificationId(todo.id),
                notifHelper.buildPerTodoReminderNotification(todo.id, todo.title, todo.priority, isOverdue = false),
            )
        }

        val pinnedCount = (overdue + undated).count { it.priority > 0 }
        if (pinnedCount > 0) {
            applicationContext.startForegroundService(
                TodoReminderForegroundService.startIntent(applicationContext, pinnedCount)
            )
        } else {
            notifHelper.cancelNotification(TodoNotificationHelper.NOTIFICATION_ID_OVERDUE)
            runCatching {
                applicationContext.startService(TodoReminderForegroundService.stopIntent(applicationContext))
            }
        }

        return Result.success()
    }
}
