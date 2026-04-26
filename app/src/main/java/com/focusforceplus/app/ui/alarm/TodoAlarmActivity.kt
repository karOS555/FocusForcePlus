package com.focusforceplus.app.ui.alarm

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
import com.focusforceplus.app.data.model.ChecklistItem
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforceplus.app.service.TodoAlarmForegroundService
import com.focusforceplus.app.ui.theme.Error
import com.focusforceplus.app.ui.theme.FocusForceTheme
import com.focusforceplus.app.ui.theme.Warning
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.util.Calendar

@AndroidEntryPoint
class TodoAlarmActivity : ComponentActivity() {

    private val viewModel: TodoAlarmViewModel by viewModels()

    private var isHighPriority = false
    private var ringtone: android.media.Ringtone? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        onBackPressedDispatcher.addCallback(this) { /* block back press */ }

        isHighPriority = intent.getIntExtra("priority", 1) == 2

        setContent {
            FocusForceTheme {
                val state by viewModel.uiState.collectAsState()
                TodoAlarmScreen(
                    state                    = state,
                    onDone                   = { viewModel.markDone { stopAndFinish() } },
                    onSnooze                 = { minutes -> viewModel.snooze(minutes) { stopAndFinish() } },
                    onReschedule             = { hour, minute, tomorrow ->
                        viewModel.reschedule(absoluteMillis(hour, minute, tomorrow)) { stopAndFinish() }
                    },
                    onDismiss                = { viewModel.dismiss { stopAndFinish() } },
                    onToggleChecklistItem    = viewModel::toggleChecklistItem,
                    onDismissAllDonePrompt   = viewModel::dismissAllDonePrompt,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isHighPriority) {
            playAlarmSound()
            startVibration()
        }
    }

    override fun onPause() {
        stopAlarmSound()
        stopVibration()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && isHighPriority) {
            val state = viewModel.uiState.value
            if (!state.isLoading) {
                startForegroundService(
                    TodoAlarmForegroundService.startIntent(
                        this,
                        viewModel.todoId,
                        state.todoTitle,
                        state.snoozeCount,
                        state.maxSnoozeCount,
                        priority = 2,
                    )
                )
            }
        }
    }

    private fun stopAndFinish() {
        finish() // onPause() will stop sound/vibration
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
                    .setAudioAttributes(attrs).build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            }

            ringtone = RingtoneManager.getRingtone(this, uri)?.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
                it.play()
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarmSound() {
        try {
            ringtone?.stop(); ringtone = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it)
                }
            }
        } catch (_: Exception) {}
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    private fun startVibration() {
        try {
            val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 800, 400, 800, 800),
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0),
                0,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vib.vibrate(effect, VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM).build())
            } else {
                vib.vibrate(effect)
            }
        } catch (_: Exception) {}
    }

    private fun stopVibration() {
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    private fun absoluteMillis(hour: Int, minute: Int, tomorrow: Boolean): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (tomorrow) add(Calendar.DAY_OF_YEAR, 1)
        }
        if (!tomorrow && cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoAlarmScreen(
    state: TodoAlarmUiState,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
    onReschedule: (hour: Int, minute: Int, tomorrow: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onToggleChecklistItem: (Int) -> Unit,
    onDismissAllDonePrompt: () -> Unit,
) {
    var showDoneConfirmDialog  by remember { mutableStateOf(false) }
    var showSnoozeDialog       by remember { mutableStateOf(false) }
    var showRescheduleDialog   by remember { mutableStateOf(false) }
    var showDismissDialog      by remember { mutableStateOf(false) }

    var timeStr by remember { mutableStateOf(currentTimeString()) }
    LaunchedEffect(Unit) {
        while (true) { timeStr = currentTimeString(); delay(1_000) }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.06f,
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
                text      = "Todo reminder",
                style     = MaterialTheme.typography.titleMedium,
                color     = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            if (!state.isLoading) {
                PriorityBadge(state.priority)
            }

            Text(
                text       = state.todoTitle,
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                textAlign  = TextAlign.Center,
            )

            if (!state.todoDescription.isNullOrBlank()) {
                Text(
                    text      = state.todoDescription,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = Color.White.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                )
            }

            if (state.checklistItems.isNotEmpty()) {
                AlarmChecklist(
                    items    = state.checklistItems,
                    onToggle = onToggleChecklistItem,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Done — High requires 2nd confirmation
            Button(
                onClick  = if (state.priority == 2) {
                    { showDoneConfirmDialog = true }
                } else {
                    onDone
                },
                modifier = Modifier.fillMaxWidth().height(64.dp).scale(scale),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Done", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            // Snooze
            val snoozeLabel = when {
                !state.canSnooze && state.priority == 2 -> "No snoozes left"
                !state.canSnooze -> "No snoozes left"
                state.priority == 2 -> "Snooze (${state.remainingSnoozes} left)"
                else -> "Snooze"
            }
            OutlinedButton(
                onClick  = if (state.canSnooze) { { showSnoozeDialog = true } } else { {} },
                enabled  = state.canSnooze,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Text(snoozeLabel, color = if (state.canSnooze) Color.White else Color.White.copy(alpha = 0.38f))
            }

            // Reschedule — High has a reschedule limit
            val rescheduleLabel = when {
                state.priority == 2 && !state.canReschedule -> "No reschedules left"
                state.priority == 2 -> "Reschedule (${state.remainingReschedules} left)"
                else -> "Reschedule"
            }
            FilledTonalButton(
                onClick  = if (state.canReschedule || state.priority != 2) {
                    { showRescheduleDialog = true }
                } else {
                    {}
                },
                enabled  = state.canReschedule || state.priority != 2,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
            ) { Text(rescheduleLabel) }

            // Dismiss — High priority has no dismiss
            if (state.priority != 2) {
                TextButton(onClick = { showDismissDialog = true }) {
                    Text("Dismiss", color = Color.White.copy(alpha = 0.4f))
                }
            }
        }
    }

    // ── Done confirmation (High priority only) ────────────────────────────────

    if (showDoneConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDoneConfirmDialog = false },
            title = { Text("Mark as done?") },
            text  = { Text("Are you sure you want to mark \"${state.todoTitle}\" as completed?") },
            confirmButton = {
                Button(onClick = { showDoneConfirmDialog = false; onDone() }) { Text("Yes, done!") }
            },
            dismissButton = {
                TextButton(onClick = { showDoneConfirmDialog = false }) { Text("Not yet") }
            },
        )
    }

    // ── Snooze dialog ─────────────────────────────────────────────────────────

    if (showSnoozeDialog) {
        AlertDialog(
            onDismissRequest = { showSnoozeDialog = false },
            title = { Text("Snooze for how long?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.priority == 2) {
                        Text(
                            "${state.remainingSnoozes} snooze(s) remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
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
        TodoRescheduleDialog(
            onConfirm = { h, m, tomorrow ->
                showRescheduleDialog = false
                onReschedule(h, m, tomorrow)
            },
            onDismiss = { showRescheduleDialog = false },
        )
    }

    // ── All checklist items done prompt ───────────────────────────────────────

    if (state.showAllDonePrompt) {
        AlertDialog(
            onDismissRequest = onDismissAllDonePrompt,
            title = { Text("All items checked!") },
            text  = { Text("You checked off every item. Mark \"${state.todoTitle}\" as done?") },
            confirmButton = {
                Button(onClick = { onDismissAllDonePrompt(); onDone() }) { Text("Mark as done") }
            },
            dismissButton = {
                TextButton(onClick = onDismissAllDonePrompt) { Text("Not yet") }
            },
        )
    }

    // ── Dismiss confirmation (Medium only) ────────────────────────────────────

    if (showDismissDialog) {
        AlertDialog(
            onDismissRequest = { showDismissDialog = false },
            title = { Text("Dismiss reminder?") },
            text  = { Text("The todo will remain open. A soft reminder will stay in your notifications until you complete it.") },
            confirmButton = {
                Button(
                    onClick = { showDismissDialog = false; onDismiss() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Dismiss") }
            },
            dismissButton = {
                TextButton(onClick = { showDismissDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AlarmChecklist(items: List<ChecklistItem>, onToggle: (Int) -> Unit) {
    val done = items.count { it.done }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text  = "Checklist \u00b7 $done/${items.size}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(4.dp))
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { onToggle(index) },
                    colors = CheckboxDefaults.colors(
                        checkedColor   = MaterialTheme.colorScheme.primary,
                        uncheckedColor = Color.White.copy(alpha = 0.6f),
                        checkmarkColor = Color.White,
                    ),
                )
                Text(
                    text           = item.text,
                    style          = MaterialTheme.typography.bodyMedium,
                    color          = if (item.done) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.9f),
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoRescheduleDialog(
    onConfirm: (hour: Int, minute: Int, tomorrow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val timeState = rememberTimePickerState(is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule todo") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("Today") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = { Text("Tomorrow") })
                }
                Spacer(Modifier.height(16.dp))
                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(timeState.hour, timeState.minute, selectedTab == 1) }) {
                Text("Reschedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun PriorityBadge(priority: Int) {
    val (label, color) = when (priority) {
        0    -> "Low priority"    to Color(0xFF94a3b8)
        2    -> "High priority"   to Error
        else -> "Medium priority" to Warning
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.2f),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelMedium,
            color    = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun currentTimeString(): String {
    val cal = Calendar.getInstance()
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
