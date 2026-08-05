package com.focusforceplus.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.focusforceplus.app.compliance.dayCodeFor
import com.focusforceplus.app.compliance.previousDayCode
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.data.repository.SleepSchedule
import com.focusforceplus.app.util.SleepAlarmHelper
import com.focusforceplus.app.util.SleepNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Drives the sleep schedule: wind-down reminder, bedtime (DND on, status
 * notification, native backup alarms, linked bedtime routine), and wake-up
 * (DND restored, linked morning routine). Fires on every scheduled day-time and
 * checks the active days itself — on inactive days it just re-arms silently.
 * Blocking during the window is evaluated live by the accessibility service.
 */
@AndroidEntryPoint
class SleepReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WIND_DOWN = "com.focusforceplus.app.SLEEP_WIND_DOWN"
        const val ACTION_BEDTIME   = "com.focusforceplus.app.SLEEP_BEDTIME"
        const val ACTION_WAKE      = "com.focusforceplus.app.SLEEP_WAKE"
    }

    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var routineRepository: RoutineRepository
    @Inject lateinit var sleepAlarmHelper: SleepAlarmHelper
    @Inject lateinit var notifHelper: SleepNotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val schedule = sleepRepository.schedule.first()
                if (!schedule.enabled) return@launch
                when (action) {
                    ACTION_WIND_DOWN -> if (isBedtimeDay(schedule)) handleWindDown(schedule)
                    ACTION_BEDTIME -> if (isBedtimeDay(schedule)) handleBedtime(context, schedule)
                    ACTION_WAKE -> handleWake(context, schedule)
                }
                // Arm the next occurrences (tomorrow) regardless of day filtering.
                sleepAlarmHelper.scheduleAll(schedule)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleWindDown(schedule: SleepSchedule) {
        notifHelper.showNotification(
            SleepNotificationHelper.NOTIFICATION_ID_WIND_DOWN,
            notifHelper.buildWindDownNotification(
                formatMinutes(schedule.bedtimeMinutes), schedule.windDownMinutes,
            ),
        )
    }

    private suspend fun handleBedtime(context: Context, schedule: SleepSchedule) {
        sleepRepository.setNightActive(true)
        notifHelper.cancelNotification(SleepNotificationHelper.NOTIFICATION_ID_WIND_DOWN)

        if (schedule.dndEnabled) applyDnd(context)

        notifHelper.showNotification(
            SleepNotificationHelper.NOTIFICATION_ID_SLEEP_ACTIVE,
            notifHelper.buildSleepActiveNotification(
                formatMinutes(schedule.wakeMinutes), schedule.blockedGroups.isNotEmpty(),
            ),
        )

        if (schedule.nativeAlarmCount > 0) setNativeWakeAlarms(context, schedule)

        // Linked bedtime routine gets the full routine alarm treatment.
        fireLinkedRoutine(context, schedule.bedtimeRoutineId, bedtime = true)
    }

    private suspend fun handleWake(context: Context, schedule: SleepSchedule) {
        if (!sleepRepository.nightActive.first()) return
        sleepRepository.setNightActive(false)
        restoreDnd(context)
        notifHelper.cancelNotification(SleepNotificationHelper.NOTIFICATION_ID_SLEEP_ACTIVE)

        // Linked morning routine = the in-app wake-up experience. The native
        // clock alarms (if configured) ring independently of the app.
        fireLinkedRoutine(context, schedule.morningRoutineId, bedtime = false)
    }

    // ── DND ───────────────────────────────────────────────────────────────────

    private suspend fun applyDnd(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return // degrade gracefully
        runCatching {
            sleepRepository.setPreviousDndFilter(nm.currentInterruptionFilter)
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
    }

    private suspend fun restoreDnd(context: Context) {
        val previous = sleepRepository.previousDndFilter.first()
        if (previous < 0) return
        sleepRepository.setPreviousDndFilter(-1)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            runCatching { nm.setInterruptionFilter(previous) }
        }
    }

    // ── Native backup alarms ──────────────────────────────────────────────────

    /**
     * Creates the wake-up alarms in the system clock app (never blockable, rings
     * even if this app is dead). Clock apps dedupe by time + label, so re-setting
     * them every night re-enables the same alarms instead of piling up new ones.
     */
    private fun setNativeWakeAlarms(context: Context, schedule: SleepSchedule) {
        for (i in 0 until schedule.nativeAlarmCount.coerceIn(1, 3)) {
            val minutes = (schedule.wakeMinutes + i * schedule.nativeAlarmGapMinutes) % (24 * 60)
            runCatching {
                context.startActivity(
                    Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, minutes / 60)
                        putExtra(AlarmClock.EXTRA_MINUTES, minutes % 60)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "FocusForce+ wake-up ${i + 1}")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Triggers the existing full routine alarm flow (screen, sound, notification).
     * A routine the user deleted after linking it would otherwise raise an alarm
     * for a nonexistent routine, so the link is verified and cleared if stale.
     */
    private suspend fun fireLinkedRoutine(context: Context, routineId: Long, bedtime: Boolean) {
        if (routineId == 0L) return
        val routine = runCatching { routineRepository.getRoutineById(routineId).first() }.getOrNull()
        if (routine == null) {
            if (bedtime) sleepRepository.setBedtimeRoutineId(0L)
            else sleepRepository.setMorningRoutineId(0L)
            return
        }
        context.sendBroadcast(
            Intent(context, RoutineAlarmReceiver::class.java).apply {
                putExtra("routineId", routineId)
                putExtra("routineName", routine.name)
            }
        )
    }

    /** The sleep day is the day of the bedtime; wake-up checks its own flag instead. */
    private fun isBedtimeDay(schedule: SleepSchedule): Boolean {
        val days = schedule.activeDaysCsv?.split(',')?.filter { it.isNotBlank() }?.toSet()
        if (days.isNullOrEmpty()) return true
        val today = dayCodeFor(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
        // A wind-down/bedtime alarm firing shortly after midnight belongs to the
        // previous day's schedule; treat pre-06:00 bedtimes that way.
        val minutesOfDay = Calendar.getInstance()
            .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val effective = if (schedule.bedtimeMinutes < 6 * 60 && minutesOfDay < 6 * 60) {
            previousDayCode(today)
        } else {
            today
        }
        return effective in days
    }

    private fun formatMinutes(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)
}
