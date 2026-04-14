package com.focusforceplus.app.ui.alarm

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
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.theme.FocusForceTheme
import com.focusforceplus.app.util.NotificationHelper
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

        val isPreAlarm = intent.getBooleanExtra("isPreAlarm", false)

        if (!isPreAlarm) {
            startVibration()
            // Show over the lock screen and turn the display on.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Block back-press so the alarm cannot be silently dismissed.
            onBackPressedDispatcher.addCallback(this) { /* block */ }

            playAlarmSound()
        }
        // Pre-alarm: back works normally, no sound, no lock-screen overlay.

        val routineId = intent.getLongExtra("routineId", 0L)

        setContent {
            FocusForceTheme {
                val s by viewModel.uiState.collectAsState()
                RoutineAlarmScreen(
                    state        = s,
                    isPreAlarm   = isPreAlarm,
                    onStartNow   = { startRoutineAndFinish(routineId) },
                    onSnooze     = { minutes ->
                        stopAlarmSound()
                        // When snoozing from the pre-alarm screen, remove the pre-alarm
                        // notification so the snooze notification replaces it cleanly.
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
                    // Pre-alarm "Dismiss": just close the screen, notification stays, routine fires normally.
                    // Full alarm "Cancel Routine": stop sound, cancel all notifications, close.
                    onDismiss    = {
                        if (isPreAlarm) {
                            finish()
                        } else {
                            stopAlarmSound()
                            notificationHelper.cancelNotification(
                                NotificationHelper.alarmNotificationId(routineId)
                            )
                            notificationHelper.cancelNotification(
                                NotificationHelper.preAlarmNotificationId(routineId)
                            )
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
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            }

            ringtone = RingtoneManager.getRingtone(this, uri)?.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.isLooping = true
                }
                it.play()
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarmSound() {
        try {
            ringtone?.stop()
            ringtone = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it)
                }
            }
        } catch (_: Exception) {}
        stopVibration()
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    private fun startVibration() {
        try {
            val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib

            // Alarm-style pattern: 800ms on, 400ms off, 800ms on, 800ms off — loops from index 0.
            val timings    = longArrayOf(0, 800, 400, 800, 800)
            val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0,
                                        VibrationEffect.DEFAULT_AMPLITUDE, 0)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)

            // On Android 13+ use USAGE_ALARM so vibration plays even in silent mode.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vib.vibrate(effect, attrs)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineAlarmScreen(
    state: AlarmUiState,
    isPreAlarm: Boolean,
    onStartNow: () -> Unit,
    onSnooze: (Int) -> Unit,
    onReschedule: (hour: Int, minute: Int, tomorrow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var showSnoozeDialog     by remember { mutableStateOf(false) }
    var showRescheduleDialog by remember { mutableStateOf(false) }

    // Live clock.
    var timeStr by remember { mutableStateOf(currentTimeString()) }
    LaunchedEffect(Unit) {
        while (true) { timeStr = currentTimeString(); delay(1_000) }
    }

    // Pulsing start button (only for full alarm, not pre-alarm).
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue  = 1f,
        targetValue   = if (isPreAlarm) 1f else 1.06f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label         = "scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050508), Color(0xFF0f2847)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text       = timeStr,
                fontSize   = 72.sp,
                fontWeight = FontWeight.Light,
                color      = Color.White,
                textAlign  = TextAlign.Center,
            )

            Text(
                text      = if (isPreAlarm) "Starting in 15 minutes" else "Time to start your routine",
                style     = MaterialTheme.typography.titleMedium,
                color     = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            // Routine icon
            val routineIcon = RoutineIcons.find(state.routineIconKey)
            if (routineIcon != null) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Color.White.copy(alpha = 0.12f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector     = routineIcon.vector,
                        contentDescription = null,
                        tint            = Color.White,
                        modifier        = Modifier.size(40.dp),
                    )
                }
            }

            Text(
                text       = state.routineName,
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                textAlign  = TextAlign.Center,
            )

            // Optional description
            if (!state.routineDescription.isNullOrBlank()) {
                Text(
                    text      = state.routineDescription,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = Color.White.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Start button
            Button(
                onClick  = onStartNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .scale(scale),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    "Start Routine",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (isPreAlarm) {
                // ── Pre-alarm actions ─────────────────────────────────────────
                // "Snooze 15 min" delays the routine start (uses the routine's snooze allowance).
                if (state.canSnooze) {
                    OutlinedButton(
                        onClick  = { onSnooze(15) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "Snooze 15 min (${state.remainingSnoozes} left)",
                            color = Color.White,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick  = {},
                        enabled  = false,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Text("No snoozes left")
                    }
                }

                // "Close" just closes the screen — notification stays, routine fires on time.
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color.White.copy(alpha = 0.4f))
                }

            } else {
                // ── Full alarm actions ────────────────────────────────────────
                if (state.canSnooze) {
                    OutlinedButton(
                        onClick  = { showSnoozeDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "Snooze (${state.remainingSnoozes} left)",
                            color = Color.White,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick  = {},
                        enabled  = false,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Text("No snoozes left")
                    }
                }

                if (state.maxRescheduleCount > 0) {
                    FilledTonalButton(
                        onClick  = { if (state.canReschedule) showRescheduleDialog = true },
                        enabled  = state.canReschedule,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            if (state.canReschedule)
                                "Reschedule (${state.remainingReschedules} left)"
                            else
                                "No reschedules left",
                        )
                    }
                }

                // "Cancel Routine" — hidden in invincible mode. Also hidden while loading
                // to avoid a flash before invincibleMode is read from the database.
                if (!state.isLoading && !state.invincibleMode) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel Routine", color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    // ── Snooze dialog ─────────────────────────────────────────────────────────

    if (showSnoozeDialog) {
        AlertDialog(
            onDismissRequest = { showSnoozeDialog = false },
            title = { Text("Snooze for how long?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${state.remainingSnoozes} snooze(s) remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    listOf(5 to "5 minutes", 10 to "10 minutes", 15 to "15 minutes",
                           30 to "30 minutes", 60 to "1 hour").forEach { (mins, label) ->
                        OutlinedButton(
                            onClick  = { showSnoozeDialog = false; onSnooze(mins) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSnoozeDialog = false }) { Text("Cancel") }
            },
        )
    }

    // ── Reschedule dialog ─────────────────────────────────────────────────────

    if (showRescheduleDialog) {
        RescheduleDialog(
            remainingReschedules = state.remainingReschedules,
            onConfirm = { hour, minute, tomorrow ->
                showRescheduleDialog = false
                onReschedule(hour, minute, tomorrow)
            },
            onDismiss = { showRescheduleDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleDialog(
    remainingReschedules: Int,
    onConfirm: (hour: Int, minute: Int, tomorrow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val timeState = rememberTimePickerState(is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule routine") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$remainingReschedules reschedule(s) remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        text     = { Text("Today") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        text     = { Text("Tomorrow") },
                    )
                }

                Spacer(Modifier.height(16.dp))

                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(timeState.hour, timeState.minute, selectedTab == 1)
            }) { Text("Reschedule") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun currentTimeString(): String {
    val cal = Calendar.getInstance()
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
