package com.focusforceplus.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.data.db.entity.RoutineCompletionEntity
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.util.AlarmSoundPolicy
import com.focusforceplus.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns a running routine session end-to-end: the per-task countdown, the ongoing
 * notification, the task-end wake-up (sound + vibration + full-screen alert), and
 * the [ActiveRoutineRegistry] state. The service — not the UI — drives the timer,
 * so the session survives the app being swiped away or the screen turning off,
 * which is the whole point of an app built around persistent reminders.
 *
 * Robustness layers:
 * - Remaining time derives from [SystemClock.elapsedRealtime] against an absolute
 *   task end, never from counting ticks — Doze or a throttled ticker can't drift it.
 * - An exact [AlarmManager.setAlarmClock] safety alarm fires at task end via
 *   [RoutineTaskEndReceiver]; if the process was killed, the receiver restarts the
 *   service, which restores from [RoutineSessionStore] and rings the missed alert.
 * - The ongoing notification uses the system chronometer, so it counts down without
 *   a notify() call every second (battery).
 */
@AndroidEntryPoint
class RoutineForegroundService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var activeRoutineRegistry: ActiveRoutineRegistry
    @Inject lateinit var repository: RoutineRepository
    @Inject lateinit var sessionStore: RoutineSessionStore
    /** App-lifetime scope: the history write must survive this service stopping. */
    @Inject lateinit var applicationScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var ringStopJob: Job? = null

    private var routineId: Long = 0L
    private var routineName: String = ""
    private var invincible: Boolean = false
    private var blocksApps: Boolean = false
    private var tasks: List<RoutineTaskEntity> = emptyList()
    private var taskIndex: Int = 0
    private var taskTotalSeconds: Int = 0
    /** Absolute end of the current task on the monotonic clock. */
    private var taskEndElapsed: Long = 0L
    private var inOvertime: Boolean = false
    private var accumulatedOvertimeSeconds: Int = 0
    private var lastNagAtOvertimeSeconds: Int = 0
    private var completed: Boolean = false

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getLongExtra(EXTRA_ROUTINE_ID, 0L)
                if (id == 0L) { stopIfIdle(); return START_NOT_STICKY }
                // A redelivered start after a process kill must resume, not restart.
                if (flags and START_FLAG_REDELIVERY != 0) {
                    startForegroundPlaceholder()
                    restoreOrStop()
                } else {
                    startSession(id)
                }
            }
            ACTION_COMPLETE_TASK -> if (routineId != 0L) completeCurrentTask()
            ACTION_ADD_TIME -> if (routineId != 0L) {
                addTime(intent.getIntExtra(EXTRA_MINUTES, 5))
            }
            ACTION_CANCEL -> {
                // Invincible sessions have no early exit (defense in depth — the UI
                // already hides the cancel affordances).
                if (!invincible) endSession()
            }
            ACTION_TASK_END_CHECK -> {
                if (routineId == 0L) {
                    // Process was killed mid-session; the safety alarm brought us back.
                    startForegroundPlaceholder()
                    restoreOrStop()
                } else {
                    checkTaskEnd()
                }
            }
            else -> stopIfIdle()
        }
        return START_REDELIVER_INTENT
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    private fun startSession(id: Long) {
        routineId = id
        completed = false
        startForegroundPlaceholder()
        serviceScope.launch {
            val routine = repository.getRoutineById(id).first()
            val loadedTasks = repository.getTasksForRoutine(id).first()
            if (routine == null || loadedTasks.isEmpty()) { stopSelf(); return@launch }
            routineName = routine.name
            invincible = routine.invincibleMode
            blocksApps = routine.appBlockerEnabled
            tasks = loadedTasks
            activeRoutineRegistry.markActive(id, blocksApps, invincible)
            beginTask(0)
            startTicker()
        }
    }

    private fun restoreOrStop() {
        val snapshot = sessionStore.load()
        if (snapshot == null) { stopIfIdle(); return }
        serviceScope.launch {
            val routine = repository.getRoutineById(snapshot.routineId).first()
            val loadedTasks = repository.getTasksForRoutine(snapshot.routineId).first()
            if (routine == null || loadedTasks.isEmpty() ||
                snapshot.taskIndex !in loadedTasks.indices
            ) {
                sessionStore.clear()
                stopIfIdle()
                return@launch
            }
            routineId = snapshot.routineId
            routineName = routine.name
            invincible = routine.invincibleMode
            blocksApps = routine.appBlockerEnabled
            tasks = loadedTasks
            taskIndex = snapshot.taskIndex
            taskTotalSeconds = snapshot.taskTotalSeconds
            inOvertime = snapshot.inOvertime
            accumulatedOvertimeSeconds = snapshot.accumulatedOvertimeSeconds
            lastNagAtOvertimeSeconds = overtimeSeconds()
            taskEndElapsed = SystemClock.elapsedRealtime() +
                (snapshot.taskEndWallMillis - System.currentTimeMillis())
            activeRoutineRegistry.markActive(routineId, blocksApps, invincible)
            updateOngoingNotification()
            if (!inOvertime && remainingSeconds() <= 0) {
                enterOvertime()
            } else if (inOvertime) {
                // The user missed the original alert while we were dead — re-ring.
                fireTaskEndAlert()
            }
            startTicker()
        }
    }

    private fun beginTask(index: Int) {
        val task = tasks[index]
        taskIndex = index
        taskTotalSeconds = task.durationMinutes * 60
        taskEndElapsed = SystemClock.elapsedRealtime() + taskTotalSeconds * 1_000L
        inOvertime = false
        lastNagAtOvertimeSeconds = 0
        stopAlarmRing()
        notificationHelper.cancelNotification(NotificationHelper.taskEndNotificationId(routineId))
        scheduleTaskEndAlarm()
        persistSnapshot()
        updateOngoingNotification()
        publishProgress()
    }

    private fun completeCurrentTask() {
        if (completed || tasks.isEmpty()) return
        if (inOvertime) accumulatedOvertimeSeconds += overtimeSeconds()
        val next = taskIndex + 1
        if (next >= tasks.size) {
            finishSession()
        } else {
            beginTask(next)
        }
    }

    private fun addTime(minutes: Int) {
        if (completed || tasks.isEmpty()) return
        if (inOvertime) {
            accumulatedOvertimeSeconds += overtimeSeconds()
            inOvertime = false
            lastNagAtOvertimeSeconds = 0
            stopAlarmRing()
            notificationHelper.cancelNotification(NotificationHelper.taskEndNotificationId(routineId))
            taskEndElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L
        } else {
            taskEndElapsed += minutes * 60_000L
        }
        scheduleTaskEndAlarm()
        persistSnapshot()
        updateOngoingNotification()
        publishProgress()
    }

    private fun finishSession() {
        completed = true
        tickerJob?.cancel()
        stopAlarmRing()
        cancelTaskEndAlarm()
        sessionStore.clear()
        notificationHelper.cancelNotification(NotificationHelper.taskEndNotificationId(routineId))
        val stats = RoutineSessionProgress(
            routineId = routineId,
            taskIndex = tasks.size,
            taskCount = tasks.size,
            taskTotalSeconds = 0,
            remainingSeconds = 0,
            overtimeSeconds = 0,
            phase = RoutineSessionPhase.COMPLETED,
            totalScheduledMinutes = tasks.sumOf { it.durationMinutes },
            totalOvertimeMinutes = accumulatedOvertimeSeconds / 60,
        )
        activeRoutineRegistry.publishProgress(stats)
        val entry = RoutineCompletionEntity(
            routineId = routineId,
            routineName = routineName,
            completedAt = System.currentTimeMillis(),
            scheduledMinutes = stats.totalScheduledMinutes,
            overtimeMinutes = stats.totalOvertimeMinutes,
            tasksCompleted = tasks.size,
        )
        applicationScope.launch { runCatching { repository.recordCompletion(entry) } }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Cancel path: tears the session down without recording a completion. */
    private fun endSession() {
        tickerJob?.cancel()
        stopAlarmRing()
        cancelTaskEndAlarm()
        sessionStore.clear()
        if (routineId != 0L) {
            notificationHelper.cancelNotification(NotificationHelper.taskEndNotificationId(routineId))
            activeRoutineRegistry.clearProgress(routineId)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Ticker ────────────────────────────────────────────────────────────────

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var sinceNotificationRefresh = 0
            while (!completed) {
                checkTaskEnd()
                publishProgress()
                // The countdown itself is drawn by the system chronometer, but the
                // timeline tracker only moves when the notification is re-posted.
                // Every 15s keeps it visibly live without a notify() per second.
                if (++sinceNotificationRefresh >= NOTIFICATION_REFRESH_SECONDS) {
                    sinceNotificationRefresh = 0
                    updateOngoingNotification()
                }
                delay(1_000L)
            }
        }
    }

    private fun checkTaskEnd() {
        if (completed || tasks.isEmpty()) return
        if (!inOvertime && remainingSeconds() <= 0) {
            enterOvertime()
        } else if (inOvertime) {
            maybeNag()
        }
    }

    private fun enterOvertime() {
        inOvertime = true
        lastNagAtOvertimeSeconds = 0
        persistSnapshot()
        updateOngoingNotification()
        fireTaskEndAlert()
        publishProgress()
    }

    /** Re-alerts every task-configured interval while the task sits in overtime. */
    private fun maybeNag() {
        val intervalSec = (tasks.getOrNull(taskIndex)?.overtimeReminderIntervalMinutes ?: 5) * 60
        if (intervalSec <= 0) return
        val overtime = overtimeSeconds()
        if (overtime - lastNagAtOvertimeSeconds >= intervalSec) {
            lastNagAtOvertimeSeconds = overtime
            fireTaskEndAlert()
        }
    }

    private fun remainingSeconds(): Int =
        ((taskEndElapsed - SystemClock.elapsedRealtime()) / 1_000L).toInt().coerceAtLeast(0)

    private fun overtimeSeconds(): Int =
        if (!inOvertime) 0
        else ((SystemClock.elapsedRealtime() - taskEndElapsed) / 1_000L).toInt().coerceAtLeast(0)

    private fun publishProgress() {
        if (completed || tasks.isEmpty()) return
        activeRoutineRegistry.publishProgress(
            RoutineSessionProgress(
                routineId = routineId,
                taskIndex = taskIndex,
                taskCount = tasks.size,
                taskTotalSeconds = taskTotalSeconds,
                remainingSeconds = remainingSeconds(),
                overtimeSeconds = overtimeSeconds(),
                phase = if (inOvertime) RoutineSessionPhase.OVERTIME else RoutineSessionPhase.RUNNING,
                totalScheduledMinutes = tasks.sumOf { it.durationMinutes },
                totalOvertimeMinutes = (accumulatedOvertimeSeconds + overtimeSeconds()) / 60,
            )
        )
    }

    // ── Task-end wake-up ──────────────────────────────────────────────────────

    /**
     * The point of the app: a task running out must be as hard to miss as an alarm
     * clock. Rings + vibrates (honoring the global toggles), posts a full-screen
     * alert notification, and brings the active-routine screen to the front.
     */
    private fun fireTaskEndAlert() {
        val taskName = tasks.getOrNull(taskIndex)?.name ?: ""
        notificationHelper.showNotification(
            NotificationHelper.taskEndNotificationId(routineId),
            notificationHelper.buildTaskEndAlarmNotification(
                routineId, routineName, taskName, overtimeSeconds() / 60,
            ),
        )
        startAlarmRing()
        // SYSTEM_ALERT_WINDOW (granted for the alarm flow) exempts this launch from
        // background-activity restrictions; the full-screen intent above is the
        // fallback for locked screens.
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra("routineId", routineId)
                }
            )
        }
    }

    private fun startAlarmRing() {
        stopAlarmRing()
        if (AlarmSoundPolicy.soundEnabled) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(this, uri)?.also {
                    it.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
                    it.play()
                }
            }
        }
        if (AlarmSoundPolicy.vibrationEnabled) {
            runCatching {
                val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as Vibrator
                }
                vibrator = vib
                val timings = longArrayOf(0, 800, 400, 800, 800)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0,
                                            VibrationEffect.DEFAULT_AMPLITUDE, 0)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                    vib.vibrate(effect, attrs)
                } else {
                    vib.vibrate(effect)
                }
            }
        }
        // Ring for a bounded stretch; the overtime nag re-rings on its interval.
        ringStopJob?.cancel()
        ringStopJob = serviceScope.launch {
            delay(RING_DURATION_MILLIS)
            stopAlarmRing()
        }
    }

    private fun stopAlarmRing() {
        ringStopJob?.cancel()
        ringStopJob = null
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    // ── Safety alarm (process-death insurance) ────────────────────────────────

    private fun taskEndAlarmIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            this, RC_TASK_END_ALARM,
            Intent(this, RoutineTaskEndReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun scheduleTaskEndAlarm() {
        val endWall = System.currentTimeMillis() + (taskEndElapsed - SystemClock.elapsedRealtime())
        val showIntent = PendingIntent.getActivity(
            this, RC_TASK_END_SHOW,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("routineId", routineId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(endWall, showIntent),
            taskEndAlarmIntent(),
        )
    }

    private fun cancelTaskEndAlarm() {
        (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(taskEndAlarmIntent())
    }

    private fun persistSnapshot() {
        val endWall = System.currentTimeMillis() + (taskEndElapsed - SystemClock.elapsedRealtime())
        sessionStore.save(
            RoutineSessionSnapshot(
                routineId = routineId,
                routineName = routineName,
                taskIndex = taskIndex,
                taskEndWallMillis = endWall,
                taskTotalSeconds = taskTotalSeconds,
                inOvertime = inOvertime,
                accumulatedOvertimeSeconds = accumulatedOvertimeSeconds,
            )
        )
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun startForegroundPlaceholder() {
        pin(notificationHelper.buildOngoingNotification(
            routineId, routineName.ifEmpty { "Routine" }, "Starting…",
            System.currentTimeMillis(), overtime = false,
        ))
    }

    private fun updateOngoingNotification() {
        val endWall = System.currentTimeMillis() + (taskEndElapsed - SystemClock.elapsedRealtime())
        pin(notificationHelper.buildOngoingNotification(
            routineId = routineId,
            routineName = routineName,
            taskName = tasks.getOrNull(taskIndex)?.name ?: "",
            taskEndWallMillis = endWall,
            overtime = inOvertime,
            taskIndex = taskIndex,
            taskDurationsMinutes = tasks.map { it.durationMinutes },
            elapsedSeconds = elapsedInRoutineSeconds(),
            chipSeconds = if (inOvertime) overtimeSeconds() else remainingSeconds(),
            invincible = invincible,
        ))
    }

    /**
     * How far into the whole routine we are, in seconds — the position of the
     * tracker on the notification timeline. Tasks already done count in full;
     * overtime does not push the tracker past the current task's own segment.
     */
    private fun elapsedInRoutineSeconds(): Int {
        if (tasks.isEmpty()) return 0
        val before = tasks.take(taskIndex).sumOf { it.durationMinutes } * 60
        val inCurrent = (taskTotalSeconds - remainingSeconds()).coerceIn(0, taskTotalSeconds)
        return before + inCurrent
    }

    private fun pin(notification: android.app.Notification) {
        // specialUse type exists from API 34; below that the manifest type applies.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun stopIfIdle() {
        if (routineId == 0L) {
            startForegroundPlaceholder()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── Teardown ──────────────────────────────────────────────────────────────

    override fun onDestroy() {
        tickerJob?.cancel()
        stopAlarmRing()
        if (routineId != 0L) {
            activeRoutineRegistry.markIdle(routineId)
            if (!completed) {
                // Keep the snapshot + safety alarm: if the system killed us, the
                // task-end alarm restarts the service and the session resumes.
                activeRoutineRegistry.clearProgress(routineId)
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START          = "com.focusforceplus.app.ROUTINE_SESSION_START"
        const val ACTION_COMPLETE_TASK  = "com.focusforceplus.app.ROUTINE_SESSION_COMPLETE_TASK"
        const val ACTION_ADD_TIME       = "com.focusforceplus.app.ROUTINE_SESSION_ADD_TIME"
        const val ACTION_CANCEL         = "com.focusforceplus.app.ROUTINE_SESSION_CANCEL"
        const val ACTION_TASK_END_CHECK = "com.focusforceplus.app.ROUTINE_SESSION_TASK_END_CHECK"

        private const val EXTRA_ROUTINE_ID = "routineId"
        private const val EXTRA_MINUTES = "minutes"

        private const val RC_TASK_END_ALARM = 640_001
        private const val RC_TASK_END_SHOW = 640_002
        private const val RING_DURATION_MILLIS = 20_000L

        /** How often the live notification is re-posted to move the timeline tracker. */
        private const val NOTIFICATION_REFRESH_SECONDS = 15

        fun start(context: Context, routineId: Long) {
            context.startForegroundService(
                Intent(context, RoutineForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_ROUTINE_ID, routineId)
                }
            )
        }

        fun completeTask(context: Context) = command(context, ACTION_COMPLETE_TASK)

        fun addTime(context: Context, minutes: Int) {
            context.startService(
                Intent(context, RoutineForegroundService::class.java).apply {
                    action = ACTION_ADD_TIME
                    putExtra(EXTRA_MINUTES, minutes)
                }
            )
        }

        fun cancel(context: Context) = command(context, ACTION_CANCEL)

        private fun command(context: Context, action: String) {
            runCatching {
                context.startService(
                    Intent(context, RoutineForegroundService::class.java).apply {
                        this.action = action
                    }
                )
            }
        }
    }
}
