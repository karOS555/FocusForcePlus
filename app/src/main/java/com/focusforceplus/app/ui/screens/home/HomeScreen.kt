package com.focusforceplus.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforceplus.app.compliance.GuardAction
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.components.CompletionRing
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FfSwitch
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.NumberTile
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SectionHeader
import com.focusforceplus.app.ui.components.TonalButton
import com.focusforceplus.app.ui.guards.GuardCopy
import com.focusforceplus.app.ui.guards.GuardFlow
import com.focusforceplus.app.ui.navigation.AppModule
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType
import com.focusforceplus.app.util.TimeFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    onGoToLists: () -> Unit = {},
    onOpenList: (Long) -> Unit = {},
    onGoToSleep: () -> Unit = {},
    onEditTodo: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var finishing by remember { mutableStateOf<TodoEntity?>(null) }

    // Screen time and permissions change all the time; refresh on every return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshUsage()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(s.message) {
        s.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    fun tickOff(todo: TodoEntity) {
        if (s.guards.forTodo(todo, GuardAction.DONE).isNone) viewModel.completeTodo(todo) else finishing = todo
    }

    val activeFocus = s.activeFocusSession
    val runningRoutine = s.todayRoutines.firstOrNull { it.status == RoutineTodayStatus.RUNNING }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Hello ─────────────────────────────────────────────────────────
            item(key = "hello") {
                Column(Modifier.padding(horizontal = 2.dp)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineMedium, color = colors.text)
                    Text(todayLine(), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
            }

            // ── Going on right now ────────────────────────────────────────────
            if (activeFocus != null || runningRoutine != null || s.sleepWindowActive) {
                item(key = "now") {
                    FfCard {
                        var first = true
                        if (activeFocus != null) {
                            ListRow(
                                title = activeFocus.name,
                                subtitle = if (activeFocus.pausedRemainingSeconds != null) "Focus · paused" else "Focus · running",
                                subtitleColor = colors.accent,
                                leading = { IconCircle(icon = Icons.Filled.SelfImprovement) },
                                onClick = { onOpenActiveFocus(activeFocus.sessionId) },
                            )
                            first = false
                        }
                        if (runningRoutine != null) {
                            if (!first) RowDivider()
                            ListRow(
                                title = runningRoutine.routine.name,
                                subtitle = "Routine · running",
                                subtitleColor = colors.accent,
                                leading = {
                                    IconCircle(icon = RoutineIcons.find(runningRoutine.routine.iconKey)?.vector ?: Icons.Filled.ViewDay)
                                },
                                onClick = { onOpenActiveRoutine(runningRoutine.routine.id) },
                            )
                            first = false
                        }
                        if (s.sleepWindowActive) {
                            if (!first) RowDivider()
                            ListRow(
                                title = "Sleep",
                                subtitle = "Until ${TimeFormat.clock(s.sleep.wakeMinutes)}",
                                subtitleColor = colors.accent,
                                leading = { IconCircle(icon = Icons.Filled.Bedtime) },
                                onClick = onGoToSleep,
                            )
                        }
                    }
                }
            }

            // ── Three numbers ─────────────────────────────────────────────────
            item(key = "numbers") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberTile(
                        value = if (s.usage.permissionGranted) TimeFormat.compact(s.usage.totalScreenTimeMinutes) else "–",
                        label = "Screen time",
                        onClick = onOpenStats,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.isOn(AppModule.TODOS)) {
                        NumberTile(
                            value = "${s.openTodoCount}",
                            label = "Open todos",
                            onClick = onGoToTodos,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (s.isOn(AppModule.FOCUS)) {
                        NumberTile(
                            value = TimeFormat.compact(s.focusedMinutesToday),
                            label = "Focused",
                            onClick = onGoToFocus,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Two buttons ───────────────────────────────────────────────────
            if (s.isOn(AppModule.FOCUS) || s.isOn(AppModule.TODOS)) {
                item(key = "actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (s.isOn(AppModule.FOCUS)) {
                            PrimaryButton(
                                text = "Start focus",
                                icon = Icons.Filled.Bolt,
                                onClick = onGoToFocus,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (s.isOn(AppModule.TODOS)) {
                            TonalButton(
                                text = "New todo",
                                icon = Icons.Filled.Add,
                                onClick = onNewTodo,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── Today's routines ──────────────────────────────────────────────
            if (s.isOn(AppModule.ROUTINES)) {
                item(key = "routines") {
                    Column {
                        SectionHeader("Today's routines", actionLabel = "Manage", onAction = onGoToRoutines)
                        if (s.todayRoutines.isEmpty()) {
                            FfCard {
                                ListRow(
                                    title = "Nothing planned for today",
                                    subtitle = "Create a routine",
                                    subtitleColor = colors.accent,
                                    onClick = onNewRoutine,
                                    leading = { IconCircle(icon = Icons.Filled.ViewDay) },
                                )
                            }
                        } else {
                            FfCard {
                                s.todayRoutines.forEachIndexed { index, today ->
                                    if (index > 0) RowDivider()
                                    RoutineRow(
                                        today = today,
                                        onOpen = {
                                            if (today.status == RoutineTodayStatus.RUNNING) {
                                                onOpenActiveRoutine(today.routine.id)
                                            } else {
                                                onStartRoutine(today.routine.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Next todos ────────────────────────────────────────────────────
            if (s.isOn(AppModule.TODOS)) {
                item(key = "todos") {
                    Column {
                        SectionHeader("Next todos", actionLabel = "View all", onAction = onGoToTodos)
                        FfCard {
                            if (s.urgentTodos.isEmpty()) {
                                ListRow(
                                    title = "Nothing open",
                                    subtitle = "Add a todo",
                                    subtitleColor = colors.accent,
                                    onClick = onNewTodo,
                                    leading = { IconCircle(icon = Icons.Filled.TaskAlt) },
                                )
                            }
                            s.urgentTodos.take(4).forEachIndexed { index, todo ->
                                if (index > 0) RowDivider()
                                TodoRow(todo = todo, onOpen = { onEditTodo(todo.id) }, onTick = { tickOff(todo) })
                            }
                        }
                    }
                }
            }

            // ── Lists with something on them ──────────────────────────────────
            val openLists = s.lists.filter { it.open > 0 }
            if (s.isOn(AppModule.LISTS) && openLists.isNotEmpty()) {
                item(key = "lists") {
                    Column {
                        SectionHeader("Lists", actionLabel = "All lists", onAction = onGoToLists)
                        FfCard {
                            openLists.take(3).forEachIndexed { index, row ->
                                if (index > 0) RowDivider()
                                ListRow(
                                    title = row.list.name,
                                    subtitle = "${row.open} to go",
                                    onClick = { onOpenList(row.list.id) },
                                    leading = {
                                        IconCircle(
                                            icon = RoutineIcons.find(row.list.iconKey)?.vector
                                                ?: Icons.AutoMirrored.Filled.FormatListBulleted,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ── Blocker and sleep, switchable from here ───────────────────────
            if (s.isOn(AppModule.BLOCKER) || s.isOn(AppModule.SLEEP)) {
                item(key = "modes") {
                    Column {
                        SectionHeader("Running in the background")
                        FfCard {
                            if (s.isOn(AppModule.BLOCKER)) {
                                ListRow(
                                    title = "App blocker",
                                    subtitle = blockerStatus(s),
                                    subtitleColor = if (!s.blockerReady && s.blockerEnabled) colors.warning else colors.textMuted,
                                    onClick = onGoToBlocker,
                                    leading = { IconCircle(icon = Icons.Filled.Block) },
                                    trailing = {
                                        FfSwitch(checked = s.blockerEnabled, onCheckedChange = viewModel::setBlockerEnabled)
                                    },
                                )
                            }
                            if (s.isOn(AppModule.BLOCKER) && s.isOn(AppModule.SLEEP)) RowDivider()
                            if (s.isOn(AppModule.SLEEP)) {
                                ListRow(
                                    title = "Sleep",
                                    subtitle = sleepStatus(s),
                                    onClick = onGoToSleep,
                                    leading = { IconCircle(icon = Icons.Filled.Bedtime) },
                                    trailing = {
                                        FfSwitch(checked = s.sleep.enabled, onCheckedChange = viewModel::setSleepEnabled)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Ticking off from here goes through the same speed bump as everywhere else.
    finishing?.let { todo ->
        ModalBottomSheet(
            onDismissRequest = { finishing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            sheetGesturesEnabled = false,
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp),
            ) {
                Text(todo.title, style = MaterialTheme.typography.headlineSmall, color = colors.text)
                Spacer(Modifier.height(16.dp))
                GuardFlow(
                    guard = s.guards.forTodo(todo, GuardAction.DONE),
                    copy = GuardCopy.DONE,
                    onPassed = {
                        viewModel.completeTodo(todo)
                        finishing = null
                    },
                    onCancel = { finishing = null },
                )
            }
        }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun RoutineRow(today: TodayRoutine, onOpen: () -> Unit) {
    val colors = FfTheme.colors
    val routine = today.routine
    val start = TimeFormat.clock(routine.startTimeHour * 60 + routine.startTimeMinute)
    val detail = when (today.status) {
        RoutineTodayStatus.RUNNING -> "Running now"
        RoutineTodayStatus.DONE_TODAY -> today.doneAt?.let { "Done at ${TimeFormat.clock(it)}" } ?: "Done today"
        RoutineTodayStatus.UPCOMING -> buildString {
            append("Starts at $start")
            if (today.totalMinutes > 0) append(" · ${TimeFormat.duration(today.totalMinutes)}")
        }
        RoutineTodayStatus.EARLIER_TODAY -> "Was due at $start"
    }
    ListRow(
        title = routine.name,
        subtitle = detail,
        subtitleColor = when (today.status) {
            RoutineTodayStatus.RUNNING -> colors.accent
            RoutineTodayStatus.EARLIER_TODAY -> colors.warning
            else -> colors.textMuted
        },
        titleColor = if (today.status == RoutineTodayStatus.DONE_TODAY) colors.textSecondary else colors.text,
        onClick = onOpen,
        leading = { IconCircle(icon = RoutineIcons.find(routine.iconKey)?.vector ?: Icons.Filled.ViewDay) },
        trailing = {
            if (today.status != RoutineTodayStatus.DONE_TODAY) {
                IconButton(onClick = onOpen) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${routine.name}", tint = colors.accent)
                }
            }
        },
    )
}

@Composable
private fun TodoRow(todo: TodoEntity, onOpen: () -> Unit, onTick: () -> Unit) {
    val colors = FfTheme.colors
    val now = System.currentTimeMillis()
    val due = todo.dueDateTime
    val overdue = due != null && due < now
    ListRow(
        title = todo.title,
        subtitle = when {
            due == null -> "No date"
            overdue -> "Overdue since ${TimeFormat.dayAndClock(due)}"
            else -> TimeFormat.dayAndClock(due)
        },
        subtitleColor = if (overdue) colors.error else colors.textMuted,
        onClick = onOpen,
        leading = { IconCircle(icon = RoutineIcons.find(todo.iconKey)?.vector ?: Icons.Filled.TaskAlt) },
        trailing = {
            CompletionRing(
                checked = false,
                onClick = onTick,
                color = colors.priority(todo.priority),
                contentDescription = "Mark ${todo.title} as done",
            )
        },
    )
}

// ── Words ─────────────────────────────────────────────────────────────────────

private fun blockerStatus(s: HomeUiState): String = when {
    !s.blockerEnabled -> "Paused"
    !s.blockerReady -> "Needs the accessibility service to work"
    s.blockedAppCount == 0 -> "On, but nothing to block yet"
    else -> buildString {
        append("On · ${s.blockedAppCount} apps")
        if (s.blockerGroupCount > 0) append(" in ${s.blockerGroupCount} groups")
    }
}

private fun sleepStatus(s: HomeUiState): String = when {
    !s.sleep.enabled -> "Off"
    s.sleepWindowActive -> "On now, until ${TimeFormat.clock(s.sleep.wakeMinutes)}"
    else -> "${TimeFormat.clock(s.sleep.bedtimeMinutes)} to ${TimeFormat.clock(s.sleep.wakeMinutes)}"
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Good night"
}

private fun todayLine(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH))
