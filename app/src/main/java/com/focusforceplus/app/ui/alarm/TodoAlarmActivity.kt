package com.focusforceplus.app.ui.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.data.model.ChecklistItem
import com.focusforceplus.app.service.TodoAlarmService
import com.focusforceplus.app.ui.components.CompletionRing
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.OutlineButton
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.TonalButton
import com.focusforceplus.app.ui.components.UnderlineTabs
import com.focusforceplus.app.ui.components.TabItem
import com.focusforceplus.app.ui.guards.GuardCopy
import com.focusforceplus.app.ui.guards.GuardDialog
import com.focusforceplus.app.ui.guards.GuardFlow
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType
import com.focusforceplus.app.ui.theme.FocusForceTheme
import com.focusforceplus.app.util.TodoAlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The full-screen answer to a due todo. It shows over the lock screen, and it is
 * also what opens when a todo's notification is tapped.
 */
@AndroidEntryPoint
class TodoAlarmActivity : ComponentActivity() {

    companion object {
        /** Id of the todo whose alarm screen is in front right now, or 0. */
        @Volatile
        var visibleTodoId: Long = 0L
            private set

        private val waiting = ConcurrentLinkedQueue<Long>()

        /**
         * Whether the alarm screen should be opened for [todoId] now. While it is in
         * front with another todo it should not: swapping the todo under someone who
         * is in the middle of answering one would make them answer the wrong one.
         * The new todo then waits its turn and is shown as soon as the current one
         * has been answered.
         */
        fun claimScreen(todoId: Long): Boolean {
            val visible = visibleTodoId
            if (visible == 0L || visible == todoId) return true
            if (todoId !in waiting) waiting.add(todoId)
            return false
        }

        internal fun nextWaiting(): Long? = waiting.poll()

        fun intent(context: Context, todoId: Long): Intent =
            Intent(context, TodoAlarmActivity::class.java).apply {
                // The data URI keeps the PendingIntents of different todos apart.
                data = Uri.parse("focusforce://todo/$todoId")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(TodoAlarmScheduler.EXTRA_TODO_ID, todoId)
            }
    }

    private val viewModel: TodoAlarmViewModel by viewModels()

    private var shownTodoId: Long = 0L
    private var inFront = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
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
        shownTodoId = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_ID, 0L)

        setContent {
            FocusForceTheme {
                val state by viewModel.uiState.collectAsState()
                val shownId by viewModel.currentId.collectAsState()
                val sounding by TodoAlarmService.sounding.collectAsState()

                LaunchedEffect(shownId) { onShown(shownId) }
                // The display stays awake while this todo is ringing. After that the
                // screen is still here when the phone is picked up, but it no longer
                // keeps the display lit on a table all night.
                val ringingHere = shownId != 0L && sounding == shownId
                LaunchedEffect(ringingHere) {
                    if (ringingHere) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                // Answered: on to a todo that came due in the meantime, or close.
                LaunchedEffect(state.finished) {
                    if (state.finished && !viewModel.advance()) finish()
                }
                // A high-priority alarm does not go away on Back; Home always works.
                BackHandler(enabled = state.priority == 2 && !state.snoozed) { }
                TodoAlarmScreen(
                    state = state,
                    onDone = viewModel::done,
                    onSnooze = viewModel::snooze,
                    onReschedule = { hour, minute, tomorrow ->
                        viewModel.reschedule(absoluteMillis(hour, minute, tomorrow))
                    },
                    onDismiss = viewModel::dismiss,
                    onToggleChecklistItem = viewModel::toggleChecklistItem,
                    linkedList = viewModel.linkedList.collectAsState().value,
                    onTickListItem = viewModel::tickListItem,
                    onOpenList = { listId ->
                        startActivity(
                            android.content.Intent(this, com.focusforceplus.app.MainActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra(com.focusforceplus.app.MainActivity.EXTRA_OPEN_ROUTE, "list/$listId"),
                        )
                    },
                )
            }
        }
    }

    /** singleTask: a second alarm arrives here instead of opening a second screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_ID, 0L)
        if (id > 0L) {
            viewModel.show(id)
            onShown(id)
        }
    }

    /** Keeps track of which todo is in front, and tells the ringing service. */
    private fun onShown(id: Long) {
        val previous = shownTodoId
        if (previous == id) return
        shownTodoId = id
        if (inFront) visibleTodoId = id
        TodoAlarmService.screenChanged(this, previous)
        TodoAlarmService.screenChanged(this, id)
    }

    override fun onStart() {
        super.onStart()
        inFront = true
        visibleTodoId = shownTodoId
        TodoAlarmService.screenChanged(this, shownTodoId)
    }

    override fun onStop() {
        inFront = false
        if (visibleTodoId == shownTodoId) visibleTodoId = 0L
        // Leaving the screen ends the turn-taking; whatever was waiting still has
        // its notification and its own next ring.
        waiting.clear()
        TodoAlarmService.screenChanged(this, shownTodoId)
        super.onStop()
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

private enum class Deferral { SNOOZE, RESCHEDULE }

@Composable
private fun TodoAlarmScreen(
    state: TodoAlarmUiState,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
    onReschedule: (hour: Int, minute: Int, tomorrow: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onToggleChecklistItem: (Int) -> Unit,
    linkedList: com.focusforceplus.app.ui.screens.lists.LinkedList? = null,
    onTickListItem: (com.focusforceplus.app.data.db.entity.ListItemEntity) -> Unit = {},
    onOpenList: (Long) -> Unit = {},
) {
    val colors = FfTheme.colors
    val todo = state.todo

    // Done starts as a plain button. Only once it is pressed do the steps of its
    // speed bump (asking again, a countdown, the slider) take the buttons' place.
    var finishing by remember(todo?.id) { mutableStateOf(false) }
    // Which deferral is waiting behind its speed bump, and which one has passed it.
    var gateFor by remember { mutableStateOf<Deferral?>(null) }
    var pickerFor by remember { mutableStateOf<Deferral?>(null) }
    var confirmDismiss by remember { mutableStateOf(false) }

    var clock by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentTime()
            delay(1_000)
        }
    }

    fun guardOf(deferral: Deferral) =
        if (deferral == Deferral.SNOOZE) state.snoozeGuard else state.rescheduleGuard

    fun request(deferral: Deferral) {
        if (guardOf(deferral).isNone) pickerFor = deferral else gateFor = deferral
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
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
            if (todo != null) {
                Text(
                    text = buildAnnotatedString {
                        append("Todo · ")
                        withStyle(SpanStyle(color = colors.priority(todo.priority), fontWeight = FontWeight.Medium)) {
                            append(
                                when (todo.priority) {
                                    2 -> "High priority"
                                    1 -> "Medium priority"
                                    else -> "Low priority"
                                }
                            )
                        }
                        if (state.snoozed) append(" · snoozed")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }

            // The middle part scrolls, so a long checklist never pushes the
            // actions off the screen.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = todo?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                        lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
                    ),
                    color = colors.text,
                )
                if (!todo?.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = todo?.description.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted,
                    )
                }
                if (state.checklist.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    AlarmChecklist(state.checklist, onToggleChecklistItem)
                }
                linkedList?.let { linked ->
                    Spacer(Modifier.height(18.dp))
                    com.focusforceplus.app.ui.screens.lists.ListTickCard(
                        linked = linked,
                        onTick = onTickListItem,
                        onOpen = { onOpenList(linked.list.id) },
                    )
                }
            }

            if (todo != null && finishing) {
                GuardFlow(
                    guard = state.doneGuard,
                    copy = GuardCopy.DONE,
                    onPassed = onDone,
                    onCancel = { finishing = false },
                )
            } else if (todo != null) {
                PrimaryButton(
                    text = "Done",
                    onClick = { if (state.doneGuard.isNone) onDone() else finishing = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        text = when {
                            state.snoozed -> "Snoozed"
                            state.canSnooze -> "Snooze \u00B7 ${state.remainingSnoozes} left"
                            else -> "No snoozes left"
                        },
                        onClick = { request(Deferral.SNOOZE) },
                        enabled = state.canSnooze,
                        modifier = Modifier.weight(1f),
                    )
                    TonalButton(
                        text = when {
                            !state.canReschedule -> "No moves left"
                            todo.priority == 2 -> "Reschedule \u00B7 ${state.remainingReschedules} left"
                            else -> "Reschedule"
                        },
                        onClick = { request(Deferral.RESCHEDULE) },
                        enabled = state.canReschedule,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = footnote(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textFaint,
                        modifier = Modifier.weight(1f),
                    )
                    if (todo.priority != 2 && !state.snoozed) {
                        TextAction("Dismiss", onClick = { confirmDismiss = true }, color = colors.textMuted)
                    }
                }
            }
        }
    }

    gateFor?.let { deferral ->
        GuardDialog(
            guard = guardOf(deferral),
            copy = if (deferral == Deferral.SNOOZE) GuardCopy.SNOOZE else GuardCopy.RESCHEDULE,
            subject = todo?.title,
            onPassed = {
                gateFor = null
                pickerFor = deferral
            },
            onDismiss = { gateFor = null },
        )
    }

    when (pickerFor) {
        Deferral.SNOOZE -> SnoozeDialog(
            onPick = { minutes ->
                pickerFor = null
                onSnooze(minutes)
            },
            onDismiss = { pickerFor = null },
        )
        Deferral.RESCHEDULE -> RescheduleDialog(
            onConfirm = { hour, minute, tomorrow ->
                pickerFor = null
                onReschedule(hour, minute, tomorrow)
            },
            onDismiss = { pickerFor = null },
        )
        null -> Unit
    }

    if (confirmDismiss) {
        AlertDialog(
            onDismissRequest = { confirmDismiss = false },
            containerColor = colors.surfaceVariant,
            title = { Text("Dismiss for now?", style = MaterialTheme.typography.titleLarge, color = colors.text) },
            text = {
                Text(
                    "The todo stays open and stays in your notifications. It will not come " +
                        "back on its own until your next daily reminder time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDismiss = false; onDismiss() }) {
                    Text("Dismiss", style = MaterialTheme.typography.labelLarge, color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDismiss = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                }
            },
        )
    }
}

/** One honest line under the buttons: what happens next, and that there is a way out. */
private fun footnote(state: TodoAlarmUiState): String {
    val todo = state.todo ?: return ""
    return when {
        todo.priority == 2 && !state.canSnooze && !state.canReschedule ->
            "No snoozes or moves left. You can still edit or delete this todo in the app."
        todo.priority == 2 ->
            "Left unanswered, it rings again. You can always edit this todo in the app."
        else -> "It stays in your notifications until it is done."
    }
}

@Composable
private fun AlarmChecklist(items: List<ChecklistItem>, onToggle: (Int) -> Unit) {
    val colors = FfTheme.colors
    Column {
        Text(
            text = "${items.count { it.done }} of ${items.size} checked",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(index) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompletionRing(
                    checked = item.done,
                    onClick = { onToggle(index) },
                    color = colors.textFaint,
                    contentDescription = item.text,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.text,
                    style = FfType.rowTitle,
                    color = if (item.done) colors.textMuted else colors.text,
                )
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleDialog(
    onConfirm: (hour: Int, minute: Int, tomorrow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var day by remember { mutableIntStateOf(0) }
    val time = rememberTimePickerState(is24Hour = true)
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
                TimePicker(
                    state = time,
                    colors = TimePickerDefaults.colors(
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
