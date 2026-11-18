package com.focusforceplus.app

import com.focusforceplus.app.ui.theme.ThemePresets
import com.focusforceplus.app.ui.theme.ThemeSpec
import com.focusforceplus.app.ui.theme.ThemeStore
import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.util.AlarmSoundPolicy
import com.focusforceplus.app.util.BlockerNotificationHelper
import com.focusforceplus.app.util.FocusNotificationHelper
import com.focusforceplus.app.util.NotificationHelper
import com.focusforceplus.app.util.NotificationStyle
import com.focusforceplus.app.util.SleepAlarmHelper
import com.focusforceplus.app.util.SleepNotificationHelper
import com.focusforceplus.app.util.AlarmRearmer
import com.focusforceplus.app.util.TodoNotifications
import com.focusforceplus.app.worker.AlarmGuardWorker
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
    @Inject lateinit var todoNotifications: TodoNotifications
    @Inject lateinit var blockerNotificationHelper: BlockerNotificationHelper
    @Inject lateinit var focusNotificationHelper: FocusNotificationHelper
    @Inject lateinit var sleepNotificationHelper: SleepNotificationHelper
    @Inject lateinit var alarmRearmer: AlarmRearmer
    @Inject lateinit var sleepAlarmHelper: SleepAlarmHelper
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var focusRecovery: com.focusforceplus.app.service.FocusSessionRecovery
    @Inject lateinit var calendarTodoMirror: com.focusforceplus.app.data.calendar.CalendarTodoMirror
    @Inject lateinit var routineSessionStore: com.focusforceplus.app.service.RoutineSessionStore
    @Inject lateinit var activeRoutineRegistry: com.focusforceplus.app.service.ActiveRoutineRegistry

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Channel groups first — channels reference them by id.
        NotificationStyle.createChannelGroups(this)
        notificationHelper.createChannels()
        todoNotifications.createChannels()
        blockerNotificationHelper.createChannels()
        focusNotificationHelper.createChannels()
        sleepNotificationHelper.createChannels()

        // Every start of the process brings alarms and notifications back in line
        // with what is stored. Phones that kill the app overnight would otherwise
        // leave a todo without its next reminder until someone opened the app.
        appScope.launch { alarmRearmer.rearmAll() }

        // Todos with a date, mirrored into the calendar the user picked (if any).
        calendarTodoMirror.start(appScope)

        // A focus session whose process was killed without coming back (force stop,
        // a crash) still holds Do Not Disturb. A system restart of the service
        // happens within moments of the process start, so wait for that first.
        appScope.launch {
            kotlinx.coroutines.delay(10_000L)
            focusRecovery.resolveIfOrphaned()
            // A routine that holds Do Not Disturb but neither runs nor waits to be
            // picked up again (no snapshot) was stopped from outside: put it back.
            val dnd = com.focusforceplus.app.util.Dnd
            if (dnd.holds(this@FocusForcePlusApp, dnd.ROUTINE) && routineSessionStore.load() == null &&
                activeRoutineRegistry.activeSession.value == null
            ) {
                dnd.release(this@FocusForcePlusApp, dnd.ROUTINE)
            }
            // Android 15+: the app's own Do Not Disturb mode never stays on with
            // nothing holding it (older versions could leave it on for good).
            dnd.clearStale(this@FocusForcePlusApp)
        }

        // The picked theme, for every activity.
        appScope.launch {
            settingsRepository.themeJson.collect { json ->
                ThemeStore.apply(json?.let(ThemeSpec::fromJson) ?: ThemePresets.MIDNIGHT)
            }
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

        // Safety net: re-arm all alarms twice a day in case an OEM battery
        // manager silently purged them (idempotent, effectively free).
        workManager.enqueueUniquePeriodicWork(
            AlarmGuardWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AlarmGuardWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build(),
        )
    }
}
