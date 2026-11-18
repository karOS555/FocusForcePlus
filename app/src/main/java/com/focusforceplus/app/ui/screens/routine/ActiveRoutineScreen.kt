package com.focusforceplus.app.ui.screens.routine

import com.focusforceplus.app.ui.theme.ffBackdrop
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FfTimePickerDialog
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
import com.focusforceplus.app.ui.theme.FfType
import com.focusforceplus.app.util.TimeFormat
import kotlin.math.sin

@Composable
fun ActiveRoutineScreen(
    onBack: () -> Unit,
    viewModel: ActiveRoutineViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    val stepList by viewModel.stepList.collectAsState()
    val colors = FfTheme.colors

    // While an invincible routine runs, Back must not be a way out: the run can only
    // be finished or moved, both of which have their own buttons here. Before it
    // starts, the screen is a preview and Back works.
    BackHandler(enabled = s.invincibleMode && s.timerState != TimerState.BEFORE_START) { }

    // The session (timer, sound, notifications) lives in RoutineForegroundService;
    // leaving this screen does not stop it.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                RoutineEvent.NavigateBack -> onBack()
            }
        }
    }

    // Which deferral waits behind its speed bump, and which picker is open.
    var snoozeGate by remember { mutableStateOf(false) }
    var moveGate by remember { mutableStateOf(false) }
    var movePicker by remember { mutableStateOf(false) }

    if (s.isLoading) {
        Box(Modifier.fillMaxSize().ffBackdrop())
        return
    }
    if (s.isCompleted) {
        CompletionScreen(stats = s.completionStats!!, routineName = s.routineName, onDone = onBack)
        return
    }

    var notesDismissed by remember(s.currentTaskIndex) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ffBackdrop()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        // ── Top: name and the way out ─────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.routineName,
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (s.invincibleMode) {
                Icon(Icons.Filled.Lock, contentDescription = "Invincible mode", tint = colors.accent)
            } else if (s.timerState == TimerState.BEFORE_START) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StepTimeline(tasks = s.tasks.map { it.durationMinutes }, current = s.currentTaskIndex)
        Spacer(Modifier.height(6.dp))
        Text(
            "Step ${s.currentTaskIndex + 1} of ${s.tasks.size}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )

        // ── The step ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IconCircle(
                icon = RoutineIcons.find(s.currentTask?.iconKey)?.vector ?: Icons.Filled.ViewDay,
                size = 48.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = s.currentTask?.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            val notes = s.currentTask?.description
            if (!notes.isNullOrBlank() && !notesDismissed) {
                Spacer(Modifier.height(12.dp))
                FfPaddedCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { notesDismissed = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Hide note", tint = colors.textMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            CircularTimer(
                remainingSeconds = s.remainingSeconds,
                totalSeconds = s.taskTotalSeconds,
                overtimeSeconds = s.overtimeSeconds,
                timerState = s.timerState,
            )
            stepList?.let { linked ->
                Spacer(Modifier.height(20.dp))
                com.focusforceplus.app.ui.screens.lists.ListTickCard(
                    linked = linked,
                    onTick = viewModel::tickListItem,
                    onStartFresh = { viewModel.restartList(linked.list.id) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            s.tasks.getOrNull(s.currentTaskIndex + 1)?.let { next ->
                Spacer(Modifier.height(20.dp))
                Text(
                    "Up next: ${next.name} · ${next.durationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }
        }

        // ── Actions ───────────────────────────────────────────────────────────
        when (s.timerState) {
            TimerState.BEFORE_START -> {
                PrimaryButton(
                    text = "Start",
                    onClick = viewModel::startRoutine,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                )
                if (s.canSnooze) {
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(
                        text = "Snooze · ${s.remainingSnoozes} left",
                        onClick = {
                            if (s.snoozeGuard.isNone) viewModel.showSnoozeDialog() else snoozeGate = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            TimerState.RUNNING, TimerState.OVERTIME -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Done",
                        icon = Icons.Filled.Check,
                        onClick = viewModel::completeCurrentTask,
                        modifier = Modifier.weight(1f).height(56.dp),
                    )
                    TonalButton(
                        text = "+ Time",
                        onClick = viewModel::showAddTimeDialog,
                        modifier = Modifier.weight(1f).height(56.dp),
                    )
                }
                if (!s.invincibleMode) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextAction("Stop the routine", onClick = viewModel::onCancelFirstClick, color = colors.textMuted)
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (s.showThreeMinWarning) {
        val mins = s.currentTask?.reminderBeforeEndMinutes ?: 3
        ConfirmDialog(
            title = "Almost there",
            message = "$mins minutes left for \"${s.currentTask?.name}\".",
            confirmLabel = "OK",
            dismissLabel = "",
            onConfirm = viewModel::dismissThreeMinWarning,
            onDismiss = viewModel::dismissThreeMinWarning,
        )
    }

    if (s.showTimeUpDialog) {
        TaskCompleteOverlay(
            taskName = s.currentTask?.name.orEmpty(),
            overtimeSeconds = s.overtimeSeconds,
            onDone = viewModel::completeCurrentTask,
            onAddTime = viewModel::addTime,
        )
    }

    if (s.showOvertimeReminder) {
        ConfirmDialog(
            title = "Still on it?",
            message = "You are ${s.overtimeReminderMinutes} min over on \"${s.currentTask?.name}\".",
            confirmLabel = "OK",
            dismissLabel = "",
            onConfirm = viewModel::dismissOvertimeReminder,
            onDismiss = viewModel::dismissOvertimeReminder,
        )
    }

    if (s.showAddTimeDialog) {
        MinutesDialog(
            title = "Add time",
            options = listOf(1, 2, 5, 10, 15),
            onSelect = { viewModel.addTime(it) },
            onDismiss = viewModel::dismissAddTimeDialog,
        )
    }

    if (snoozeGate) {
        GuardDialog(
            guard = s.snoozeGuard,
            copy = GuardCopy.SNOOZE,
            subject = s.routineName,
            onPassed = {
                snoozeGate = false
                viewModel.showSnoozeDialog()
            },
            onDismiss = { snoozeGate = false },
        )
    }

    if (s.showSnoozeDialog) {
        MinutesDialog(
            title = "Snooze for how long?",
            subtitle = "${s.remainingSnoozes} left",
            options = listOf(5, 10, 15, 30),
            onSelect = { viewModel.snooze(it) },
            onDismiss = viewModel::dismissSnoozeDialog,
        )
    }

    // Stopping: first "are you sure", then, while moves are left, the offer to move
    // the run instead of dropping it.
    when (s.cancelConfirmStep) {
        1 -> com.focusforceplus.app.ui.guards.GuardDialog(
            guard = s.endGuard,
            copy = com.focusforceplus.app.ui.guards.GuardCopy.ROUTINE_END,
            subject = s.routineName,
            onPassed = viewModel::onCancelConfirmed,
            onDismiss = viewModel::dismissCancel,
        )
        2 -> ConfirmDialog(
            title = "Move it instead?",
            message = "You can move this run to later today or to tomorrow, so it is not lost.",
            confirmLabel = "Move it",
            dismissLabel = "Just stop",
            onConfirm = {
                viewModel.dismissCancel()
                if (s.rescheduleGuard.isNone) movePicker = true else moveGate = true
            },
            onDismiss = {
                viewModel.dismissCancel()
                viewModel.cancelSession()
                onBack()
            },
        )
    }

    if (moveGate) {
        GuardDialog(
            guard = s.rescheduleGuard,
            copy = GuardCopy.RESCHEDULE,
            subject = s.routineName,
            onPassed = {
                moveGate = false
                movePicker = true
            },
            onDismiss = { moveGate = false },
        )
    }

    if (movePicker) {
        MoveDialog(
            onMove = { hour, minute, tomorrow ->
                movePicker = false
                viewModel.reschedule(hour, minute, tomorrow)
                onBack()
            },
            onDismiss = { movePicker = false },
        )
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

/** The run as one bar: a segment per step, as long as the step. */
@Composable
private fun StepTimeline(tasks: List<Int>, current: Int) {
    val colors = FfTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tasks.forEachIndexed { index, minutes ->
            Box(
                Modifier
                    .weight(minutes.coerceAtLeast(1).toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            index < current -> colors.accent
                            index == current -> colors.onContainer.copy(alpha = 0.55f)
                            else -> colors.surfaceHigh
                        }
                    ),
            )
        }
    }
}

@Composable
private fun CircularTimer(
    remainingSeconds: Int,
    totalSeconds: Int,
    overtimeSeconds: Int,
    timerState: TimerState,
) {
    val colors = FfTheme.colors
    val track = colors.surfaceHigh
    val arc = if (timerState == TimerState.OVERTIME) colors.error else colors.accent
    val fraction = if (totalSeconds <= 0) 0f else when (timerState) {
        TimerState.BEFORE_START -> 1f
        TimerState.RUNNING -> remainingSeconds.toFloat() / totalSeconds.toFloat()
        TimerState.OVERTIME -> 0f
    }

    Box(modifier = Modifier.size(232.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = if (timerState == TimerState.OVERTIME) {
                (overtimeSeconds / (10 * 60f)).coerceIn(0f, 1f) * 360f
            } else {
                fraction * 360f
            }
            if (sweep > 0f) {
                drawArc(arc, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (timerState) {
                TimerState.BEFORE_START -> Text(
                    TimeFormat.duration(totalSeconds / 60),
                    style = FfType.number.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                    color = colors.textSecondary,
                )
                TimerState.RUNNING -> {
                    Text(
                        formatSeconds(remainingSeconds),
                        style = FfType.number.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize),
                        color = colors.text,
                    )
                    Text("left", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                TimerState.OVERTIME -> {
                    Text(
                        "+${formatSeconds(overtimeSeconds)}",
                        style = FfType.number.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize),
                        color = colors.error,
                    )
                    Text("over", style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        }
    }
}

private fun formatSeconds(total: Int): String = "%d:%02d".format(total / 60, total % 60)

/** Time is up for a step: done, or a few more minutes. Sound comes from the service. */
@Composable
private fun TaskCompleteOverlay(
    taskName: String,
    overtimeSeconds: Int,
    onDone: () -> Unit,
    onAddTime: (Int) -> Unit,
) {
    val colors = FfTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .ffBackdrop()
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text("Time is up", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(taskName, style = MaterialTheme.typography.headlineMedium, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    "+${formatSeconds(overtimeSeconds)} over",
                    style = FfType.number.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                    color = colors.error,
                )
            }
            PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp))
            Spacer(Modifier.height(14.dp))
            Text("A few more minutes", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 5, 10, 15).forEach { minutes ->
                    FfChip(label = "$minutes min", selected = false, onClick = { onAddTime(minutes) })
                }
            }
        }
    }
}

@Composable
private fun MinutesDialog(
    title: String,
    options: List<Int>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val colors = FfTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column {
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { minutes ->
                        FfChip(label = "$minutes min", selected = false, onClick = { onSelect(minutes) })
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

@Composable
private fun MoveDialog(
    onMove: (hour: Int, minute: Int, tomorrow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var day by remember { mutableStateOf(0) }
    var pickTime by remember { mutableStateOf(false) }
    val colors = FfTheme.colors
    if (!pickTime) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.surfaceVariant,
            title = { Text("Move to when?", style = MaterialTheme.typography.titleLarge, color = colors.text) },
            text = {
                UnderlineTabs(
                    tabs = listOf(TabItem("Later today"), TabItem("Tomorrow")),
                    selectedIndex = day,
                    onSelect = { day = it },
                )
            },
            confirmButton = {
                TextButton(onClick = { pickTime = true }) {
                    Text("Pick a time", style = MaterialTheme.typography.labelLarge, color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                }
            },
        )
    } else {
        FfTimePickerDialog(
            title = if (day == 1) "Tomorrow at" else "Today at",
            hour = 18,
            minute = 0,
            confirmLabel = "Move",
            onConfirm = { h, m -> onMove(h, m, day == 1) },
            onDismiss = onDismiss,
        )
    }
}

// ── Done ──────────────────────────────────────────────────────────────────────

@Composable
fun CompletionScreen(
    stats: CompletionStats,
    routineName: String,
    onDone: () -> Unit,
) {
    val colors = FfTheme.colors
    val transition = rememberInfiniteTransition(label = "confetti")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .ffBackdrop(),
    ) {
        Confetti(transition, listOf(colors.accent, colors.success, colors.warning, colors.onContainer))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Routine done", style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
                Spacer(Modifier.height(4.dp))
                Text(routineName, style = MaterialTheme.typography.headlineMedium, color = colors.text)
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Steps", "${stats.tasksCompleted}", Modifier.weight(1f))
                    StatTile("Planned", TimeFormat.duration(stats.totalScheduledMinutes), Modifier.weight(1f))
                    StatTile(
                        "Over",
                        if (stats.totalOvertimeMinutes > 0) "+${stats.totalOvertimeMinutes} min" else "None",
                        Modifier.weight(1f),
                        valueColor = if (stats.totalOvertimeMinutes > 0) colors.warning else colors.text,
                    )
                }
            }
            PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, valueColor: Color = FfTheme.colors.text) {
    val colors = FfTheme.colors
    FfPaddedCard(modifier = modifier, spacing = 2.dp) {
        Text(value, style = FfType.number.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = valueColor)
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

@Composable
private fun Confetti(transition: InfiniteTransition, palette: List<Color>) {
    val count = 48
    val phases = (0 until count).map { i ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200 + (i * 47) % 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "particle_$i",
        )
    }
    val seeds = remember {
        (0 until count).map { Triple((it * 137 % 100) / 100f, (it * 73 % 80 + 10) / 100f, it % 4) }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        phases.forEachIndexed { i, phase ->
            val progress = phase.value
            val (xSeed, spread, colorIndex) = seeds[i]
            val x = (xSeed + spread * 0.25f * sin(progress * Math.PI.toFloat() * 2f)) * size.width
            val y = progress * (size.height + 40.dp.toPx()) - 20.dp.toPx()
            val alpha = when {
                progress < 0.1f -> progress / 0.1f
                progress > 0.85f -> (1f - progress) / 0.15f
                else -> 1f
            } * 0.8f
            val side = (3 + (i % 4)).dp.toPx()
            drawRect(
                color = palette[colorIndex % palette.size].copy(alpha = alpha),
                topLeft = Offset(x - side / 2f, y - side / 2f),
                size = Size(side, side),
            )
        }
    }
}
