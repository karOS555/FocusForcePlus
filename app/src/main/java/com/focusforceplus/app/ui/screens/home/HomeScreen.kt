package com.focusforceplus.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.theme.Blue900
import com.focusforceplus.app.ui.theme.Error
import com.focusforceplus.app.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onStartRoutine: (Long) -> Unit,
    onOpenActiveRoutine: (Long) -> Unit,
    onOpenActiveFocus: (Long) -> Unit,
    onGoToRoutines: () -> Unit,
    onGoToTodos: () -> Unit,
    onGoToFocus: () -> Unit,
    onGoToBlocker: () -> Unit,
    onNewTodo: () -> Unit,
    onNewRoutine: () -> Unit,
    onOpenStats: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()

    // Screen time changes constantly — refresh whenever the dashboard comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshUsage()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Greeting ─────────────────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = greeting(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = todayLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenStats) {
                    Icon(
                        Icons.Filled.BarChart,
                        contentDescription = "Statistics",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Live session banner ──────────────────────────────────────────────
        val activeFocus = s.activeFocusSession
        val runningRoutine = s.todayRoutines.firstOrNull { it.status == RoutineTodayStatus.RUNNING }
        if (activeFocus != null) {
            item {
                LiveSessionBanner(
                    icon = Icons.Filled.SelfImprovement,
                    title = "Focus running: ${activeFocus.name}",
                    subtitle = if (activeFocus.pausedRemainingSeconds != null) "Paused - tap to open"
                               else "Tap to open the timer",
                    onClick = { onOpenActiveFocus(activeFocus.sessionId) },
                )
            }
        } else if (runningRoutine != null) {
            item {
                LiveSessionBanner(
                    icon = Icons.Filled.Timer,
                    title = "Routine running: ${runningRoutine.routine.name}",
                    subtitle = "Tap to jump back in",
                    onClick = { onOpenActiveRoutine(runningRoutine.routine.id) },
                )
            }
        }

        // ── Today stats ──────────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Screen time",
                    value = if (s.usage.permissionGranted) formatHoursMinutes(s.usage.totalScreenTimeMinutes)
                            else "—",
                    hint = if (s.usage.permissionGranted) "today" else "needs usage access",
                    onClick = onGoToBlocker,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Open todos",
                    value = "${s.openTodoCount}",
                    hint = if (s.openTodoCount == 0) "all clear" else "waiting for you",
                    onClick = onGoToTodos,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Blocked apps",
                    value = "${s.blockedAppCount}",
                    hint = if (s.blockerEnabled) "blocking on" else "blocking paused",
                    onClick = onGoToBlocker,
                )
            }
        }

        if (s.usage.permissionGranted && s.usage.topBlockedAppName != null) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Most used blocked app today: ${s.usage.topBlockedAppName} " +
                            "(${formatHoursMinutes(s.usage.topBlockedAppMinutes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Quick actions ────────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onGoToFocus,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start focus")
                }
                FilledTonalButton(
                    onClick = onNewTodo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New todo")
                }
            }
        }

        // ── Today's routines ─────────────────────────────────────────────────
        item {
            SectionHeader(
                title = "Today's routines",
                actionLabel = "Manage",
                onAction = onGoToRoutines,
            )
        }
        if (s.todayRoutines.isEmpty()) {
            item {
                EmptyHintCard(
                    text = "No routines scheduled for today.",
                    actionLabel = "Create routine",
                    onAction = onNewRoutine,
                )
            }
        } else {
            items(s.todayRoutines.size) { index ->
                val item = s.todayRoutines[index]
                TodayRoutineRow(
                    item = item,
                    onStart = {
                        if (item.status == RoutineTodayStatus.RUNNING) {
                            onOpenActiveRoutine(item.routine.id)
                        } else {
                            onStartRoutine(item.routine.id)
                        }
                    },
                )
            }
        }

        // ── Urgent todos ─────────────────────────────────────────────────────
        item {
            SectionHeader(
                title = "Next todos",
                actionLabel = "View all",
                onAction = onGoToTodos,
            )
        }
        if (s.urgentTodos.isEmpty()) {
            item {
                EmptyHintCard(
                    text = "Nothing open. Enjoy the calm - or plan ahead.",
                    actionLabel = "New todo",
                    onAction = onNewTodo,
                )
            }
        } else {
            items(s.urgentTodos.size) { index ->
                val todo = s.urgentTodos[index]
                UrgentTodoRow(
                    todo = todo,
                    onComplete = { viewModel.completeTodo(todo) },
                    onClick = onGoToTodos,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun LiveSessionBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(MaterialTheme.colorScheme.primary, Blue900)
                ),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    hint: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun TodayRoutineRow(
    item: TodayRoutine,
    onStart: () -> Unit,
) {
    val routine = item.routine
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onStart),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = RoutineIcons.find(routine.iconKey)?.vector ?: Icons.Filled.Schedule
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(38.dp)
                        .padding(8.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    routine.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val statusText = when (item.status) {
                    RoutineTodayStatus.RUNNING -> "Running now"
                    RoutineTodayStatus.DONE_TODAY -> "Completed today ✓"
                    RoutineTodayStatus.UPCOMING ->
                        "Scheduled at %02d:%02d".format(routine.startTimeHour, routine.startTimeMinute)
                    RoutineTodayStatus.EARLIER_TODAY ->
                        "Was scheduled at %02d:%02d".format(routine.startTimeHour, routine.startTimeMinute)
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (item.status) {
                        RoutineTodayStatus.RUNNING -> MaterialTheme.colorScheme.primary
                        RoutineTodayStatus.DONE_TODAY -> Color(0xFF34d399)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onStart) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Start routine",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun UrgentTodoRow(
    todo: TodoEntity,
    onComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val overdue = todo.dueDateTime != null && todo.dueDateTime < now
    val priorityColor = when (todo.priority) {
        2 -> Error
        1 -> Warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (overdue) Error.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = false,
            onCheckedChange = { onComplete() },
            colors = CheckboxDefaults.colors(
                uncheckedColor = priorityColor,
                checkedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                todo.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val dueLabel = when {
                todo.dueDateTime == null -> null
                overdue -> "Overdue - ${formatDue(todo.dueDateTime)}"
                else -> "Due ${formatDue(todo.dueDateTime)}"
            }
            if (dueLabel != null) {
                Text(
                    dueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) Error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = priorityColor.copy(alpha = 0.35f),
            modifier = Modifier
                .padding(end = 6.dp)
                .size(14.dp),
        )
    }
}

@Composable
private fun EmptyHintCard(text: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ── Formatting ────────────────────────────────────────────────────────────────

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Good night"
}

private fun todayLine(): String =
    SimpleDateFormat("EEEE, MMM d", Locale.ENGLISH).format(Date())

private fun formatHoursMinutes(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

private fun formatDue(millis: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.ENGLISH).format(Date(millis))
