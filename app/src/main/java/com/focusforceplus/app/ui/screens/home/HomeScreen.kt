package com.focusforceplus.app.ui.screens.home

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
import com.focusforceplus.app.compliance.GuardSlot
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
import com.focusforceplus.app.ui.guards.GuardDialog
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
    var pausingBlocker by remember { mutableStateOf(false) }

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

    // Switching the blocker off from here is the same pause as on its own screen,
    // so the same speed bump stands in front of it.
    val pauseGuard = s.guards[GuardSlot.BLOCKER_PAUSE]
    fun switchBlocker(on: Boolean) {
        if (on || pauseGuard.isNone) viewModel.setBlockerEnabled(on) else pausingBlocker = true
    }

    val activeFocus = s.activeFocusSession
    val plan by viewModel.today.collectAsState()
    val runningRoutine = s.todayRoutines.firstOrNull { it.status == RoutineTodayStatus.RUNNING }

    Scaffold(
        containerColor = colors.screen,
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
                    DayProgress(done = plan.doneToday, planned = plan.plannedToday)
                }
            }

            // ── Going on right now ────────────────────────────────────────────
            // The night only counts while the Sleep module is switched on.
            val sleeping = s.sleepWindowActive && s.isOn(AppModule.SLEEP)
            if (activeFocus != null || runningRoutine != null || sleeping) {
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
                        if (sleeping) {
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

            // ── Up next: the one thing to look at when nothing runs ───────────
            if (activeFocus == null && runningRoutine == null && !sleeping) {
                item(key = "next") {
                    UpNextCard(
                        item = plan.next,
                        now = plan.asOf,
                        onStartRoutine = onStartRoutine,
                        onOpenActiveRoutine = onOpenActiveRoutine,
                        onTick = { tickOff(it) },
                        onOpenTodo = onEditTodo,
                        onNewTodo = onNewTodo,
                    )
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

            // ── Today: routines, todos and calendar in one timeline ───────────
            item(key = "today") {
                Column {
                    SectionHeader("Today", actionLabel = if (s.isOn(AppModule.TODOS)) "All todos" else null, onAction = onGoToTodos)
                    FfCard {
                        if (plan.items.isEmpty()) {
                            ListRow(
                                title = "Nothing else today",
                                subtitle = "Plan something",
                                subtitleColor = colors.accent,
                                onClick = onNewTodo,
                                leading = { IconCircle(icon = Icons.Filled.TaskAlt) },
                            )
                        }
                        plan.items.take(TODAY_ROWS).forEachIndexed { index, item ->
                            if (index > 0) RowDivider()
                            TimelineRow(
                                item = item,
                                isNext = item == plan.next,
                                onStartRoutine = onStartRoutine,
                                onOpenActiveRoutine = onOpenActiveRoutine,
                                onTick = { tickOff(it) },
                                onOpenTodo = onEditTodo,
                            )
                        }
                        if (plan.items.size > TODAY_ROWS) {
                            RowDivider()
                            ListRow(
                                title = "${plan.items.size - TODAY_ROWS} more today",
                                titleColor = colors.accent,
                                onClick = onGoToTodos,
                            )
                        }
                    }
                }
            }

            // ── Anytime: todos without a date ─────────────────────────────────
            if (plan.anytime.isNotEmpty()) {
                item(key = "anytime") {
                    Column {
                        SectionHeader("Anytime")
                        FfCard {
                            plan.anytime.forEachIndexed { index, todo ->
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
                                    subtitleColor = if ((!s.blockerReady || s.blockerStalled) && s.blockerEnabled) colors.warning else colors.textMuted,
                                    onClick = onGoToBlocker,
                                    leading = { IconCircle(icon = Icons.Filled.Block) },
                                    trailing = {
                                        FfSwitch(checked = s.blockerEnabled, onCheckedChange = ::switchBlocker)
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

    if (pausingBlocker) {
        GuardDialog(
            guard = pauseGuard,
            copy = GuardCopy.BLOCKER_PAUSE,
            onPassed = {
                pausingBlocker = false
                viewModel.setBlockerEnabled(false)
            },
            onDismiss = { pausingBlocker = false },
        )
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
    s.blockerStalled -> "Its service is not running, tap to see"
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

private const val TODAY_ROWS = 8

/** "3 of 7 done" with a thin bar; nothing on a day without plans. */
@Composable
private fun DayProgress(done: Int, planned: Int) {
    if (planned <= 0) return
    val colors = FfTheme.colors
    val fraction = (done.toFloat() / planned).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.surfaceHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (done >= planned) colors.success else colors.accent),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (done >= planned) "All $planned done today" else "$done of $planned done today",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textMuted,
        )
    }
}

/** The next thing with a time, big enough to act on without looking for it. */
@Composable
private fun UpNextCard(
    item: TodayItem?,
    now: Long,
    onStartRoutine: (Long) -> Unit,
    onOpenActiveRoutine: (Long) -> Unit,
    onTick: (TodoEntity) -> Unit,
    onOpenTodo: (Long) -> Unit,
    onNewTodo: () -> Unit,
) {
    val colors = FfTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    if (item == null) return
    val whenText = when {
        item is TodayItem.Routine && item.today.status == RoutineTodayStatus.RUNNING -> "Running now"
        item.at <= now -> "Now"
        else -> "In " + TimeFormat.duration(((item.at - now) / 60_000L).toInt().coerceAtLeast(1))
    }
    FfPaddedCard(spacing = 4.dp) {
        Text("Up next · $whenText", style = MaterialTheme.typography.labelLarge, color = colors.accent)
        Text(itemTitle(item), style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 2)
        Text(itemDetail(item, withTime = true), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        Spacer(Modifier.height(8.dp))
        when (item) {
            is TodayItem.Routine -> {
                val running = item.today.status == RoutineTodayStatus.RUNNING
                PrimaryButton(
                    text = if (running) "Open" else "Start now",
                    icon = Icons.Filled.PlayArrow,
                    onClick = { if (running) onOpenActiveRoutine(item.today.routine.id) else onStartRoutine(item.today.routine.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is TodayItem.Todo -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(text = "Done", icon = Icons.Filled.TaskAlt, onClick = { onTick(item.todo) }, modifier = Modifier.weight(1f))
                TonalButton(text = "Open", onClick = { onOpenTodo(item.todo.id) }, modifier = Modifier.weight(1f))
            }
            is TodayItem.Event -> TonalButton(
                text = "Open in calendar",
                onClick = { openEvent(context, item.event) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One row of today: the time on the left, what it is, and what you can do with it. */
@Composable
private fun TimelineRow(
    item: TodayItem,
    isNext: Boolean,
    onStartRoutine: (Long) -> Unit,
    onOpenActiveRoutine: (Long) -> Unit,
    onTick: (TodoEntity) -> Unit,
    onOpenTodo: (Long) -> Unit,
) {
    val colors = FfTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val late = (item is TodayItem.Todo && item.overdue) ||
        (item is TodayItem.Routine && item.today.status == RoutineTodayStatus.EARLIER_TODAY)
    val timeLabel = when {
        item is TodayItem.Event && item.event.allDay -> "All day"
        item is TodayItem.Todo && item.at < startOfToday() -> TimeFormat.day(item.at)
        else -> TimeFormat.clock(item.at)
    }
    val open: () -> Unit = when (item) {
        is TodayItem.Routine -> {
            { if (item.today.status == RoutineTodayStatus.RUNNING) onOpenActiveRoutine(item.today.routine.id) else onStartRoutine(item.today.routine.id) }
        }
        is TodayItem.Todo -> { { onOpenTodo(item.todo.id) } }
        is TodayItem.Event -> { { openEvent(context, item.event) } }
    }
    ListRow(
        title = itemTitle(item),
        subtitle = itemDetail(item, withTime = false),
        subtitleColor = when {
            late -> if (item is TodayItem.Todo) colors.error else colors.warning
            item is TodayItem.Routine && item.today.status == RoutineTodayStatus.RUNNING -> colors.accent
            else -> colors.textMuted
        },
        onClick = open,
        leading = {
            Column(Modifier.width(58.dp), horizontalAlignment = Alignment.Start) {
                Text(
                    timeLabel,
                    style = if (timeLabel.length > 5) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                    color = when {
                        late -> if (item is TodayItem.Todo) colors.error else colors.warning
                        isNext -> colors.accent
                        else -> colors.textSecondary
                    },
                    maxLines = 1,
                )
                if (item is TodayItem.Event) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(width = 18.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color(item.event.color)),
                    )
                }
            }
        },
        trailing = {
            when (item) {
                is TodayItem.Routine -> IconButton(onClick = open) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${item.today.routine.name}", tint = colors.accent)
                }
                is TodayItem.Todo -> CompletionRing(
                    checked = false,
                    onClick = { onTick(item.todo) },
                    color = colors.priority(item.todo.priority),
                    contentDescription = "Mark ${item.todo.title} as done",
                )
                is TodayItem.Event -> Unit
            }
        },
    )
}

private fun itemTitle(item: TodayItem): String = when (item) {
    is TodayItem.Routine -> item.today.routine.name
    is TodayItem.Todo -> item.todo.title
    is TodayItem.Event -> item.event.title
}

private fun itemDetail(item: TodayItem, withTime: Boolean): String = when (item) {
    is TodayItem.Routine -> {
        val today = item.today
        val time = TimeFormat.clock(today.routine.startTimeHour * 60 + today.routine.startTimeMinute)
        when (today.status) {
            RoutineTodayStatus.RUNNING -> "Routine · running now"
            RoutineTodayStatus.EARLIER_TODAY -> "Routine · was due at $time, start it now"
            else -> listOfNotNull(
                "Routine",
                time.takeIf { withTime },
                today.totalMinutes.takeIf { it > 0 }?.let { TimeFormat.duration(it) },
            ).joinToString(" · ")
        }
    }
    is TodayItem.Todo -> {
        val due = item.todo.dueDateTime ?: 0L
        when {
            item.overdue -> "Todo · overdue since " + TimeFormat.dayAndClock(due)
            withTime -> "Todo · " + TimeFormat.clock(due)
            else -> listOfNotNull("Todo", item.todo.checklistJson?.let { checklistProgress(it) }).joinToString(" · ")
        }
    }
    is TodayItem.Event -> {
        val e = item.event
        listOfNotNull(
            "Calendar",
            (if (e.allDay) "all day" else TimeFormat.clock(e.begin) + " to " + TimeFormat.clock(e.end)).takeIf { withTime || !e.allDay },
            e.location,
        ).joinToString(" · ")
    }
}

private fun checklistProgress(json: String): String? {
    val items = com.focusforceplus.app.data.model.checklistFromJson(json)
    if (items.isEmpty()) return null
    return "${items.count { it.done }} of ${items.size} checked"
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Opens an event in the calendar app. */
private fun openEvent(context: android.content.Context, event: com.focusforceplus.app.data.calendar.CalendarEvent) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, event.eventId),
    ).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, event.end)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
