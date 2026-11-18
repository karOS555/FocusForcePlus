package com.focusforceplus.app.ui.alarm

import com.focusforceplus.app.ui.theme.ffBackdrop
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.compliance.Guard
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.OutlineButton
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.TabItem
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.TonalButton
import com.focusforceplus.app.ui.components.UnderlineTabs
import com.focusforceplus.app.ui.guards.GuardCopy
import com.focusforceplus.app.ui.guards.GuardDialog
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FocusForceTheme
import com.focusforceplus.app.util.NotificationHelper
import com.focusforceplus.app.util.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class RoutineAlarmActivity : ComponentActivity() {

    @Inject lateinit var notificationHelper: NotificationHelper

    private val viewModel: RoutineAlarmViewModel by viewModels()

    private var ringtone: Ringtone? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        val isPreAlarm = intent.getBooleanExtra("isPreAlarm", false)

        if (!isPreAlarm) {
            startVibration()
            // Show over the lock screen and turn the display on.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Back does not silently dismiss a ringing alarm.
            onBackPressedDispatcher.addCallback(this) { /* block */ }

            playAlarmSound()
        }
        // The heads-up before the start: no sound, Back works, no lock-screen overlay.

        val routineId = intent.getLongExtra("routineId", 0L)

        setContent {
            FocusForceTheme {
                val state by viewModel.uiState.collectAsState()
                // A routine deleted after its alarm was set: stop and go away quietly.
                LaunchedEffect(state.notFound) {
                    if (state.notFound) {
                        stopAlarmSound()
                        notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))
                        notificationHelper.cancelNotification(NotificationHelper.preAlarmNotificationId(routineId))
                        finish()
                    }
                }
                RoutineAlarmScreen(
                    state = state,
                    isPreAlarm = isPreAlarm,
                    onStartNow = { startRoutineAndFinish(routineId) },
                    onSnooze = { minutes ->
                        stopAlarmSound()
                        // From the heads-up screen, the snooze notification replaces the
                        // heads-up notification.
                        if (isPreAlarm) {
                            notificationHelper.cancelNotification(
                                NotificationHelper.preAlarmNotificationId(routineId)
                            )
                        }
                        viewModel.snooze(minutes)
                        finish()
                    },
                    onReschedule = { hour, minute, tomorrow ->
                        stopAlarmSound()
                        viewModel.reschedule(hour, minute, tomorrow)
                        finish()
                    },
                    // Heads-up: just close; the notification stays and the alarm comes on
                    // time. Full alarm: skip this run.
                    onDismiss = {
                        if (isPreAlarm) {
                            finish()
                        } else {
                            stopAlarmSound()
                            notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))
                            notificationHelper.cancelNotification(NotificationHelper.preAlarmNotificationId(routineId))
                            finish()
                        }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        stopAlarmSound()
        stopVibration()
        super.onDestroy()
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    private fun playAlarmSound() {
        if (!com.focusforceplus.app.util.AlarmSoundPolicy.soundEnabled) return
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)

            ringtone = RingtoneManager.getRingtone(this, uri)?.also {
                it.audioAttributes = attrs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
                it.play()
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarmSound() {
        try {
            ringtone?.stop()
            ringtone = null
            audioFocusRequest?.let {
                (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it)
            }
        } catch (_: Exception) {}
        stopVibration()
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    private fun startVibration() {
        if (!com.focusforceplus.app.util.AlarmSoundPolicy.vibrationEnabled) return
        try {
            val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib
            // 800 ms on, 400 off, 800 on, 800 off, looping.
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 800, 400, 800, 800),
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0),
                0,
            )
            // Android 13+: USAGE_ALARM so it vibrates in silent mode too.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vib.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
            } else {
                vib.vibrate(effect)
            }
        } catch (_: Exception) {}
    }

    private fun stopVibration() {
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun startRoutineAndFinish(routineId: Long) {
        stopAlarmSound()
        notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))
        notificationHelper.cancelNotification(NotificationHelper.preAlarmNotificationId(routineId))
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("routineId", routineId)
            },
        )
        finish()
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

private enum class RoutineDeferral { SNOOZE, RESCHEDULE }

@Composable
private fun RoutineAlarmScreen(
    state: AlarmUiState,
    isPreAlarm: Boolean,
    onStartNow: () -> Unit,
    onSnooze: (Int) -> Unit,
    onReschedule: (hour: Int, minute: Int, tomorrow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var gateFor by remember { mutableStateOf<RoutineDeferral?>(null) }
    var pickerFor by remember { mutableStateOf<RoutineDeferral?>(null) }
    var confirmSkip by remember { mutableStateOf(false) }

    var clock by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentTime()
            delay(1_000)
        }
    }

    fun guardOf(deferral: RoutineDeferral): Guard =
        if (deferral == RoutineDeferral.SNOOZE) state.snoozeGuard else state.rescheduleGuard

    fun request(deferral: RoutineDeferral) {
        if (guardOf(deferral).isNone) pickerFor = deferral else gateFor = deferral
    }

    Box(
        Modifier
            .fillMaxSize()
            .ffBackdrop(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 16.dp),
        ) {
            Text(text = clock, style = MaterialTheme.typography.displayLarge, color = colors.text)
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isPreAlarm) "Routine · starts at " + com.focusforceplus.app.util.TimeFormat.clock(state.startMinutes)
                       else "Routine · time to start",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                IconCircle(
                    icon = RoutineIcons.find(state.routineIconKey)?.vector ?: Icons.Filled.ViewDay,
                    size = 52.dp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.routineName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.text,
                )
                if (state.stepCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${state.stepCount} steps · ${TimeFormat.duration(state.totalMinutes)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted,
                    )
                }
                if (!state.routineDescription.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.routineDescription,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                }
            }

            PrimaryButton(
                text = if (isPreAlarm) "Start now" else "Start the routine",
                onClick = onStartNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
            Spacer(Modifier.height(12.dp))

            if (isPreAlarm) {
                OutlineButton(
                    text = if (state.canSnooze) "Snooze 15 min · ${state.remainingSnoozes} left" else "No snoozes left",
                    onClick = {
                        if (state.snoozeGuard.isNone) onSnooze(15) else gateFor = RoutineDeferral.SNOOZE
                    },
                    enabled = state.canSnooze,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextAction("Close", onClick = onDismiss, color = colors.textMuted)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        text = if (state.canSnooze) "Snooze · ${state.remainingSnoozes} left" else "No snoozes left",
                        onClick = { request(RoutineDeferral.SNOOZE) },
                        enabled = state.canSnooze,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.maxRescheduleCount > 0) {
                        TonalButton(
                            text = if (state.canReschedule) "Move · ${state.remainingReschedules} left" else "No moves left",
                            onClick = { request(RoutineDeferral.RESCHEDULE) },
                            enabled = state.canReschedule,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.invincibleMode) {
                            "Invincible: this run can be started, snoozed or moved, not skipped."
                        } else {
                            "Left unanswered, it stays in your notifications."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textFaint,
                        modifier = Modifier.weight(1f),
                    )
                    // Hidden while loading too, so it never flashes before the mode is known.
                    if (!state.isLoading && !state.invincibleMode) {
                        TextAction("Skip this run", onClick = { confirmSkip = true }, color = colors.textMuted)
                    }
                }
            }
        }
    }

    gateFor?.let { deferral ->
        GuardDialog(
            guard = guardOf(deferral),
            copy = if (deferral == RoutineDeferral.SNOOZE) GuardCopy.SNOOZE else GuardCopy.RESCHEDULE,
            subject = state.routineName,
            onPassed = {
                gateFor = null
                // The heads-up screen snoozes by a fixed 15 minutes.
                if (isPreAlarm && deferral == RoutineDeferral.SNOOZE) onSnooze(15) else pickerFor = deferral
            },
            onDismiss = { gateFor = null },
        )
    }

    when (pickerFor) {
        RoutineDeferral.SNOOZE -> SnoozeDialog(
            onPick = { pickerFor = null; onSnooze(it) },
            onDismiss = { pickerFor = null },
        )
        RoutineDeferral.RESCHEDULE -> RescheduleDialog(
            onConfirm = { h, m, tomorrow -> pickerFor = null; onReschedule(h, m, tomorrow) },
            onDismiss = { pickerFor = null },
        )
        null -> Unit
    }

    if (confirmSkip) {
        ConfirmDialog(
            title = "Skip this run?",
            message = "The alarm stops and the routine comes back at its next scheduled time.",
            confirmLabel = "Skip",
            onConfirm = {
                confirmSkip = false
                onDismiss()
            },
            onDismiss = { confirmSkip = false },
        )
    }
}

@Composable
private fun SnoozeDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = FfTheme.colors
    val options = listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text("Snooze for how long?", style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (minutes, label) ->
                            FfChip(label = label, selected = false, onClick = { onPick(minutes) })
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleDialog(
    onConfirm: (hour: Int, minute: Int, tomorrow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var day by remember { mutableIntStateOf(0) }
    val time = androidx.compose.material3.rememberTimePickerState(is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text("Move to when?", style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                UnderlineTabs(
                    tabs = listOf(TabItem("Today"), TabItem("Tomorrow")),
                    selectedIndex = day,
                    onSelect = { day = it },
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.TimePicker(
                    state = time,
                    colors = androidx.compose.material3.TimePickerDefaults.colors(
                        clockDialColor = colors.surface,
                        selectorColor = colors.primary,
                        timeSelectorSelectedContainerColor = colors.container,
                        timeSelectorSelectedContentColor = colors.onContainer,
                        timeSelectorUnselectedContainerColor = colors.surface,
                        timeSelectorUnselectedContentColor = colors.text,
                        clockDialSelectedContentColor = colors.onPrimary,
                        clockDialUnselectedContentColor = colors.textSecondary,
                        containerColor = Color.Transparent,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(time.hour, time.minute, day == 1) }) {
                Text("Move", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

private fun currentTime(): String {
    val cal = Calendar.getInstance()
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
