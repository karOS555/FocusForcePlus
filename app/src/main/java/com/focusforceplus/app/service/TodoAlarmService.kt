package com.focusforceplus.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.ui.alarm.TodoAlarmActivity
import com.focusforceplus.app.util.AlarmSoundPolicy
import com.focusforceplus.app.util.TodoNotifications
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Rings a due todo.
 *
 * It exists for one reason: the sound of a high-priority todo has to keep going
 * when the user leaves the alarm screen, and only a foreground service can do
 * that. So it runs exactly as long as something is ringing and then stops itself.
 * The time a ring lasts is fixed and short (a setting, capped in
 * ComplianceLimits), after which the todo falls back to a silent notification
 * until its next reminder.
 *
 * Todos that become due together ring one after the other, never on top of each
 * other.
 */
@AndroidEntryPoint
class TodoAlarmService : Service() {

    @Inject lateinit var engine: TodoReminderEngine
    @Inject lateinit var notifications: TodoNotifications
    @Inject lateinit var repository: TodoRepository
    @Inject lateinit var settings: SettingsRepository

    companion object {
        private const val ACTION_RING = "com.focusforceplus.app.TODO_RING"
        private const val ACTION_STOP = "com.focusforceplus.app.TODO_RING_STOP"
        private const val ACTION_SCREEN = "com.focusforceplus.app.TODO_RING_SCREEN"
        private const val EXTRA_ID = "todoId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DESCRIPTION = "description"
        private const val EXTRA_PRIORITY = "priority"
        private const val EXTRA_ICON = "iconKey"

        /** How long a medium-priority todo holds the screen; it makes no sound. */
        private const val MEDIUM_HOLD_MILLIS = 20_000L

        /** How long the alarm screen gets to appear before the banner steps in. */
        private const val SCREEN_GRACE_MILLIS = 1_500L

        private val ringing: MutableSet<Long> = ConcurrentHashMap.newKeySet()
        private val soundingId = MutableStateFlow(0L)

        /**
         * Id of the todo that is ringing at this moment, or 0. The alarm screen
         * keeps the display awake for exactly that long and no longer.
         */
        val sounding: StateFlow<Long> = soundingId.asStateFlow()

        /** True while this todo is ringing or waiting for its turn to ring. */
        fun isRinging(todoId: Long): Boolean = todoId in ringing

        fun ringIntent(context: Context, todo: TodoEntity): Intent =
            Intent(context, TodoAlarmService::class.java).apply {
                action = ACTION_RING
                putExtra(EXTRA_ID, todo.id)
                putExtra(EXTRA_TITLE, todo.title)
                putExtra(EXTRA_DESCRIPTION, todo.description)
                putExtra(EXTRA_PRIORITY, todo.priority)
                putExtra(EXTRA_ICON, todo.iconKey)
            }

        /**
         * The alarm screen came to the front or left it. Decides whether the
         * notification of a ringing todo needs to be loud.
         */
        fun screenChanged(context: Context, todoId: Long) {
            if (!isRinging(todoId)) return
            runCatching {
                context.startService(
                    Intent(context, TodoAlarmService::class.java).apply {
                        action = ACTION_SCREEN
                        putExtra(EXTRA_ID, todoId)
                    }
                )
            }
        }

        /** Stops the ring of this todo, if there is one. Safe to call at any time. */
        fun stop(context: Context, todoId: Long) {
            if (!isRinging(todoId)) return
            runCatching {
                context.startService(
                    Intent(context, TodoAlarmService::class.java).apply {
                        action = ACTION_STOP
                        putExtra(EXTRA_ID, todoId)
                    }
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = ArrayDeque<TodoEntity>()
    private var current: TodoEntity? = null
    private var timeout: Job? = null
    private var screenCheck: Job? = null

    private var ringtone: Ringtone? = null
    private var focusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val todo = TodoEntity(
                    id = intent.getLongExtra(EXTRA_ID, 0L),
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    description = intent.getStringExtra(EXTRA_DESCRIPTION),
                    priority = intent.getIntExtra(EXTRA_PRIORITY, 2),
                    iconKey = intent.getStringExtra(EXTRA_ICON),
                    createdAt = 0L,
                )
                val active = current
                when {
                    todo.id <= 0L -> if (active == null) stopSelf()
                    active == null -> startRinging(todo)
                    active.id == todo.id -> startRinging(todo) // same todo again: restart its ring
                    else -> {
                        ringing += todo.id
                        queue.removeAll { it.id == todo.id }
                        queue.addLast(todo)
                        // Until its turn comes it is listed quietly, so it does not
                        // seem to have gone missing.
                        runCatching { notifications.post(todo.id, notifications.ringing(todo, onScreen = true)) }
                    }
                }
            }
            ACTION_STOP -> finish(intent.getLongExtra(EXTRA_ID, 0L))
            ACTION_SCREEN -> {
                val active = current
                if (active != null && active.id == intent.getLongExtra(EXTRA_ID, 0L)) {
                    screenCheck?.cancel()
                    post(active)
                } else if (active == null) {
                    stopSelf()
                }
            }
            else -> if (current == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(todo: TodoEntity) {
        timeout?.cancel()
        silence()
        current = todo
        ringing += todo.id
        soundingId.value = todo.id

        // Start quiet. The alarm screen is being opened at this very moment; only if
        // it does not come up (the system may refuse it) does the notification turn
        // into the loud, full-screen kind.
        post(todo, forceQuiet = true)
        screenCheck?.cancel()
        screenCheck = scope.launch {
            delay(SCREEN_GRACE_MILLIS)
            if (current?.id == todo.id) post(todo)
        }

        val high = todo.priority == 2
        if (high) {
            playSound()
            vibrate(repeat = true)
        } else {
            vibrate(repeat = false)
        }
        timeout = scope.launch {
            val millis = if (high) settings.todoRingSeconds.first() * 1_000L else MEDIUM_HOLD_MILLIS
            delay(millis)
            finish(todo.id)
        }
    }

    private fun post(todo: TodoEntity, forceQuiet: Boolean = false) {
        val onScreen = forceQuiet || TodoAlarmActivity.visibleTodoId == todo.id
        val notification = notifications.ringing(todo, onScreen = onScreen)
        val id = TodoNotifications.notificationId(todo.id)
        // The specialUse type exists from API 34; below that the manifest type applies.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    /**
     * Ends the ring of [todoId], whether it timed out or was answered. The
     * notification is left in place and detached; the engine then replaces it with
     * whatever the todo's state calls for, or removes it.
     */
    private fun finish(todoId: Long) {
        val active = current
        if (active == null || active.id != todoId) {
            queue.removeAll { it.id == todoId }
            ringing -= todoId
            if (active == null && queue.isEmpty()) stopSelf()
            return
        }
        timeout?.cancel()
        screenCheck?.cancel()
        silence()
        ringing -= todoId
        current = null
        soundingId.value = 0L

        val next = queue.removeFirstOrNull()
        if (next != null) {
            // Switching the foreground notification to the next todo drops the old
            // one; the engine puts the right one back for the todo that just ended.
            startRinging(next)
            scope.launch { engine.onRingEnded(todoId) }
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            scope.launch {
                engine.onRingEnded(todoId)
                if (current == null) stopSelf()
            }
        }
    }

    override fun onDestroy() {
        silence()
        current?.let { ringing -= it.id }
        queue.forEach { ringing -= it.id }
        queue.clear()
        current = null
        soundingId.value = 0L
        scope.cancel()
        super.onDestroy()
    }

    // ── Sound and vibration ───────────────────────────────────────────────────

    private fun playSound() {
        if (!AlarmSoundPolicy.soundEnabled) return
        runCatching {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            audio.requestAudioFocus(request)

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.also {
                it.audioAttributes = attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
                it.play()
            }
        }
    }

    private fun vibrate(repeat: Boolean) {
        if (!AlarmSoundPolicy.vibrationEnabled) return
        runCatching {
            val device: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = device
            val effect = if (repeat) {
                VibrationEffect.createWaveform(
                    longArrayOf(0, 800, 400, 800, 800),
                    intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0),
                    0,
                )
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device.vibrate(
                    effect,
                    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build(),
                )
            } else {
                device.vibrate(effect)
            }
        }
    }

    private fun silence() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching {
            focusRequest?.let { (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
        }
        focusRequest = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }
}
