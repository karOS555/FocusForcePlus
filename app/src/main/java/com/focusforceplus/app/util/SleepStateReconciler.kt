package com.focusforceplus.app.util

import android.app.NotificationManager
import android.content.Context
import com.focusforceplus.app.compliance.dayCodeFor
import com.focusforceplus.app.compliance.isSleepWindowActive
import com.focusforceplus.app.data.repository.SleepRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repairs sleep-mode state that a missed wake-up left behind. If the device
 * reboots (or the process dies) during the night, the wake alarm can pass
 * unhandled — which would otherwise strand Do Not Disturb and the "sleep mode
 * until …" notification for the rest of the day.
 *
 * Runs from [AlarmRearmer], so boot, app update, clock changes and the periodic
 * alarm guard all reconcile. Blocking itself never needs this: the accessibility
 * service derives it from the schedule times, not from the night flag.
 */
@Singleton
class SleepStateReconciler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sleepRepository: SleepRepository,
    private val notifHelper: SleepNotificationHelper,
) {
    suspend fun reconcile() {
        if (!sleepRepository.nightActive.first()) return

        val schedule = sleepRepository.schedule.first()
        val cal = Calendar.getInstance()
        val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val stillNight = schedule.enabled && isSleepWindowActive(
            schedule.bedtimeMinutes, schedule.wakeMinutes, schedule.activeDaysCsv,
            minutesOfDay, dayCodeFor(cal.get(Calendar.DAY_OF_WEEK)),
        )
        if (stillNight) {
            // A night an older version started kept Do Not Disturb itself: hand it over.
            val legacy = sleepRepository.previousDndFilter.first()
            if (legacy >= 0 && !Dnd.holds(context, Dnd.SLEEP) &&
                Dnd.acquire(context, Dnd.SLEEP, knownPrevious = legacy)
            ) {
                sleepRepository.setPreviousDndFilter(-1)
            }
            return
        }

        // The night is over but its wake-up never ran — close it out.
        sleepRepository.setNightActive(false)
        notifHelper.cancelNotification(SleepNotificationHelper.NOTIFICATION_ID_SLEEP_ACTIVE)

        restoreSleepDnd(context, sleepRepository)
    }
}

/**
 * Sleep mode hands Do Not Disturb back (see [Dnd]). Also closes out what an older
 * version of the app kept itself for a night that was running during the update.
 */
suspend fun restoreSleepDnd(context: Context, sleepRepository: SleepRepository) {
    val legacy = sleepRepository.previousDndFilter.first()
    if (legacy >= 0) sleepRepository.setPreviousDndFilter(-1)
    Dnd.release(
        context,
        Dnd.SLEEP,
        legacyApplied = NotificationManager.INTERRUPTION_FILTER_PRIORITY.takeIf { legacy >= 0 },
        legacyPrevious = legacy.takeIf { it >= 0 },
    )
}
