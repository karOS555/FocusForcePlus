package com.focusforceplus.app.ui.screens.routine

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRoutineScreen(
    onBack: () -> Unit,
    viewModel: ActiveRoutineViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Sound and vibration events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                RoutineEvent.PlayAlarm -> playAlarm(context)
                RoutineEvent.VibrateShort -> vibrateShort(context)
            }
        }
    }

    if (s.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    if (s.isCompleted) {
        CompletionScreen(
            stats = s.completionStats!!,
            routineName = s.routineName,
            onDone = onBack,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.routineName, maxLines = 1) },
                navigationIcon = {
                    if (!s.invincibleMode) {
                        IconButton(onClick = viewModel::onCancelFirstClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Invincible mode",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Overall progress
            OverallProgressSection(
                currentIndex = s.currentTaskIndex,
                totalTasks = s.tasks.size,
                progress = s.overallProgress,
            )

            Spacer(Modifier.height(24.dp))

            // Task name
            Text(
                text = s.currentTask?.name ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(32.dp))

            // Circular timer
            CircularTimer(
                remainingSeconds = s.remainingSeconds,
                totalSeconds = s.taskTotalSeconds,
                overtimeSeconds = s.overtimeSeconds,
                timerState = s.timerState,
            )

            Spacer(Modifier.height(32.dp))

            // Action buttons
            when (s.timerState) {
                TimerState.BEFORE_START -> {
                    BeforeStartActions(
                        canSnooze = s.canSnooze,
                        remainingSnoozes = s.remainingSnoozes,
                        onStart = viewModel::startRoutine,
                        onSnooze = viewModel::showSnoozeDialog,
                    )
                }
                TimerState.RUNNING, TimerState.OVERTIME -> {
                    RunningActions(
                        onDone = viewModel::completeCurrentTask,
                        onAddTime = viewModel::showAddTimeDialog,
                    )
                }
            }

            // Cancel button (hidden in invincible mode)
            if (!s.invincibleMode && s.timerState != TimerState.BEFORE_START) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = viewModel::onCancelFirstClick) {
                    Text(
                        "Cancel routine",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (s.showThreeMinWarning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissThreeMinWarning,
            title = { Text("Almost done!") },
            text = {
                val mins = s.currentTask?.reminderBeforeEndMinutes ?: 3
                Text("$mins minutes remaining for \"${s.currentTask?.name}\".")
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissThreeMinWarning) { Text("OK") }
            },
        )
    }

    if (s.showTimeUpDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Time is up!") },
            text = { Text("\"${s.currentTask?.name}\" time has ended. What would you like to do?") },
            confirmButton = {
                Button(onClick = viewModel::completeCurrentTask) { Text("Done") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissTimeUpDialog(); viewModel.showAddTimeDialog() }) {
                    Text("Add time")
                }
            },
        )
    }

    if (s.showOvertimeReminder) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOvertimeReminder,
            title = { Text("Still going...") },
            text = { Text("You are ${s.overtimeReminderMinutes} minute(s) over time on \"${s.currentTask?.name}\".") },
            confirmButton = {
                TextButton(onClick = viewModel::dismissOvertimeReminder) { Text("OK") }
            },
        )
    }

    if (s.showAddTimeDialog) {
        TimeOptionDialog(
            title = "Add extra time",
            options = listOf(1, 2, 5, 10, 15),
            onSelect = { viewModel.addTime(it) },
            onDismiss = viewModel::dismissAddTimeDialog,
        )
    }

    if (s.showSnoozeDialog) {
        TimeOptionDialog(
            title = "Snooze routine",
            subtitle = "${s.remainingSnoozes} snooze(s) remaining",
            options = listOf(5, 10, 15, 30),
            onSelect = { minutes ->
                viewModel.snooze(minutes) { /* caller would schedule the alarm */ }
            },
            onDismiss = viewModel::dismissSnoozeDialog,
        )
    }

    // Cancel confirm flow
    when (s.cancelConfirmStep) {
        1 -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissCancel,
                title = { Text("Cancel routine?") },
                text = { Text("Your progress will be lost. Are you sure you want to stop?") },
                confirmButton = {
                    Button(
                        onClick = viewModel::onCancelConfirmed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Stop routine") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissCancel) { Text("Keep going") }
                },
            )
        }
        2 -> {
            RescheduleDialog(
                routineName = s.routineName,
                onReschedule = { _, _ -> onBack() },
                onSkip = onBack,
                onDismiss = viewModel::dismissCancel,
            )
        }
    }
}

// ── Timer sections ────────────────────────────────────────────────────────────

@Composable
private fun OverallProgressSection(
    currentIndex: Int,
    totalTasks: Int,
    progress: Float,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Task ${currentIndex + 1} of $totalTasks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun CircularTimer(
    remainingSeconds: Int,
    totalSeconds: Int,
    overtimeSeconds: Int,
    timerState: TimerState,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = when (timerState) {
        TimerState.OVERTIME -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val fraction = if (totalSeconds <= 0) 0f
    else when (timerState) {
        TimerState.BEFORE_START -> 1f
        TimerState.RUNNING -> remainingSeconds.toFloat() / totalSeconds.toFloat()
        TimerState.OVERTIME -> 0f
    }

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Progress arc (or overtime fills full red circle)
            val sweep = if (timerState == TimerState.OVERTIME) {
                val maxOvertimeDisplay = 10 * 60f
                (overtimeSeconds.toFloat() / maxOvertimeDisplay).coerceIn(0f, 1f) * 360f
            } else {
                fraction * 360f
            }
            if (sweep > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (timerState) {
                TimerState.BEFORE_START -> {
                    Text(
                        "Ready",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TimerState.RUNNING -> {
                    Text(
                        formatSeconds(remainingSeconds),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TimerState.OVERTIME -> {
                    Text(
                        "+${formatSeconds(overtimeSeconds)}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "overtime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatSeconds(total: Int): String {
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

// ── Action buttons ────────────────────────────────────────────────────────────

@Composable
private fun BeforeStartActions(
    canSnooze: Boolean,
    remainingSnoozes: Int,
    onStart: () -> Unit,
    onSnooze: () -> Unit,
) {
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text("Start", style = MaterialTheme.typography.titleMedium)
    }

    AnimatedVisibility(visible = canSnooze) {
        Column {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Snooze ($remainingSnoozes left)")
            }
        }
    }
}

@Composable
private fun RunningActions(
    onDone: () -> Unit,
    onAddTime: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onDone,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Done")
        }
        FilledTonalButton(
            onClick = onAddTime,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Text("+ Time")
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun TimeOptionDialog(
    title: String,
    subtitle: String? = null,
    options: List<Int>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { min ->
                        FilledTonalButton(
                            onClick = { onSelect(min) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("${min}m", fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleDialog(
    routineName: String,
    onReschedule: (Int, Int) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timeState = rememberTimePickerState(is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule \"$routineName\"?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Would you like to reschedule this routine for later today?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            Button(onClick = { onReschedule(timeState.hour, timeState.minute) }) {
                Text("Reschedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        },
    )
}

// ── Completion screen ─────────────────────────────────────────────────────────

@Composable
fun CompletionScreen(
    stats: CompletionStats,
    routineName: String,
    onDone: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ConfettiAnimation(infiniteTransition)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Routine complete!",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = routineName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompletionStatCard(
                    label = "Tasks done",
                    value = "${stats.tasksCompleted}",
                    modifier = Modifier.weight(1f),
                )
                CompletionStatCard(
                    label = "Scheduled",
                    value = "${stats.totalScheduledMinutes}m",
                    modifier = Modifier.weight(1f),
                )
                CompletionStatCard(
                    label = "Overtime",
                    value = if (stats.totalOvertimeMinutes > 0) "+${stats.totalOvertimeMinutes}m" else "0",
                    valueColor = if (stats.totalOvertimeMinutes > 0) MaterialTheme.colorScheme.error
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CompletionStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ConfettiAnimation(transition: InfiniteTransition) {
    val particleCount = 60
    val colors = listOf(
        Color(0xFF1d4ed8), Color(0xFF7c3aed), Color(0xFFec4899),
        Color(0xFFf59e0b), Color(0xFF10b981), Color(0xFFef4444),
    )

    // One animated phase per particle
    val phases = (0 until particleCount).map { i ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000 + (i * 47) % 1500,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "particle_$i",
        )
    }

    // Stable random seeds per particle
    val seeds = remember {
        (0 until particleCount).map {
            Triple(
                (it * 137 % 100) / 100f,   // x start (0..1)
                (it * 73 % 80 + 10) / 100f, // horizontal spread weight
                (it * 31 % 6),              // color index
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        phases.forEachIndexed { i, phase ->
            val progress = phase.value
            val (xSeed, xSpread, colorIdx) = seeds[i]

            val x = (xSeed + xSpread * sin(progress * Math.PI.toFloat() * 2f)) * size.width
            val y = progress * (size.height + 40.dp.toPx()) - 20.dp.toPx()
            val alpha = when {
                progress < 0.1f -> progress / 0.1f
                progress > 0.85f -> (1f - progress) / 0.15f
                else -> 1f
            }
            val rotation = progress * 720f
            val particleSize = (4 + (i % 6)).dp.toPx()

            drawRect(
                color = colors[colorIdx.toInt()].copy(alpha = alpha),
                topLeft = Offset(x - particleSize / 2f, y - particleSize / 2f),
                size = Size(particleSize, particleSize),
            )
        }
    }
}

// ── Platform helpers ──────────────────────────────────────────────────────────

private fun playAlarm(context: Context) {
    try {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)?.play()
    } catch (_: Exception) {}
}

private fun vibrateShort(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) {}
}
