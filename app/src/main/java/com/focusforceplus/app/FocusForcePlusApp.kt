package com.focusforceplus.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.util.NotificationHelper
import com.focusforceplus.app.util.TodoAlarmHelper
import com.focusforceplus.app.util.TodoNotificationHelper
import com.focusforceplus.app.worker.TodoOverdueWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class FocusForcePlusApp : Application() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var todoNotificationHelper: TodoNotificationHelper
    @Inject lateinit var todoAlarmHelper: TodoAlarmHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        todoNotificationHelper.createChannels()

        appScope.launch {
            val times = settingsRepository.digestTimes.first()
            todoAlarmHelper.scheduleDigestAlarms(times)
        }

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "todo_overdue_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TodoOverdueWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build(),
        )
    }
}
