package com.focusforceplus.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.util.AlarmSoundPolicy
import com.focusforceplus.app.util.BlockerNotificationHelper
import com.focusforceplus.app.util.FocusNotificationHelper
import com.focusforceplus.app.util.NotificationHelper
import com.focusforceplus.app.util.TodoAlarmHelper
import com.focusforceplus.app.util.TodoNotificationHelper
import com.focusforceplus.app.worker.DailyResetWorker
import com.focusforceplus.app.worker.TodoOverdueWorker
import com.focusforceplus.app.worker.UsageSyncWorker
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
    @Inject lateinit var blockerNotificationHelper: BlockerNotificationHelper
    @Inject lateinit var focusNotificationHelper: FocusNotificationHelper
    @Inject lateinit var todoAlarmHelper: TodoAlarmHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        todoNotificationHelper.createChannels()
        blockerNotificationHelper.createChannels()
        focusNotificationHelper.createChannels()

        appScope.launch {
            val times = settingsRepository.digestTimes.first()
            todoAlarmHelper.scheduleDigestAlarms(times)
        }

        // Keep the synchronous alarm sound/vibration snapshot in sync with settings.
        appScope.launch {
            settingsRepository.alarmSoundEnabled.collect { AlarmSoundPolicy.soundEnabled = it }
        }
        appScope.launch {
            settingsRepository.alarmVibrationEnabled.collect { AlarmSoundPolicy.vibrationEnabled = it }
        }

        val workManager = WorkManager.getInstance(this)

        workManager.enqueueUniquePeriodicWork(
            "todo_overdue_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TodoOverdueWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build(),
        )

        // Midnight reset for the app blocker (usage counters + exception budget).
        workManager.enqueueUniquePeriodicWork(
            DailyResetWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyResetWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(DailyResetWorker.initialDelayMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )

        // Keep per-app usage numbers fresh for limits and the blocker UI.
        workManager.enqueueUniquePeriodicWork(
            UsageSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UsageSyncWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}
