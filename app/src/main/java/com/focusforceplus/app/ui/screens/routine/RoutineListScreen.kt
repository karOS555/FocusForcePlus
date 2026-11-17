package com.focusforceplus.app.ui.screens.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.common.routineDaysSummary
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.EmptyState
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.GroupLabel
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Long) -> Unit,
    onStartRoutine: (Long) -> Unit = {},
    viewModel: RoutineListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }

    var menuFor by remember { mutableStateOf<RoutineRow?>(null) }
    var deleteTarget by remember { mutableStateOf<RoutineEntity?>(null) }
    var pausePrompt by remember { mutableStateOf<RoutineEntity?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    fun togglePause(routine: RoutineEntity) {
        // Pausing a running, invincible routine only pauses future runs; say so.
        if (routine.isActive && routine.invincibleMode && viewModel.isRunning(routine.id)) {
            pausePrompt = routine
        } else {
            viewModel.toggleActive(routine)
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateRoutine,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) { Icon(Icons.Filled.Add, contentDescription = "New routine") }
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.ViewDay,
                title = "No routines yet",
                text = "A routine is a few steps with a timer each, started by an alarm. " +
                    "Mornings and evenings are a good place to begin.",
                actionLabel = "Create a routine",
                onAction = onCreateRoutine,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "Today" to state.today,
                "Other days" to state.otherDays,
                "Paused" to state.paused,
            ).forEach { (label, rows) ->
                if (rows.isEmpty()) return@forEach
                item(key = "label-$label") { GroupLabel(label) }
                item(key = "card-$label") {
                    FfCard {
                        rows.forEachIndexed { index, row ->
                            if (index > 0) RowDivider()
                            RoutineListRow(
                                row = row,
                                onOpen = { onEditRoutine(row.routine.id) },
                                onMenu = { menuFor = row },
                                onStart = { onStartRoutine(row.routine.id) },
                            )
                        }
                    }
                }
            }
            item(key = "hint") {
                Text(
                    "Press and hold a routine to start it now, copy it or pause it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                    modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                )
            }
        }
    }

    menuFor?.let { row ->
        val routine = row.routine
        ModalBottomSheet(
            onDismissRequest = { menuFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceVariant,
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    maxLines = 2,
                )
                ListRow(title = "Start now", onClick = { menuFor = null; onStartRoutine(routine.id) })
                ListRow(title = "Edit", onClick = { menuFor = null; onEditRoutine(routine.id) })
                ListRow(title = "Duplicate", onClick = { menuFor = null; viewModel.duplicate(routine) })
                ListRow(
                    title = if (routine.isActive) "Pause the schedule" else "Resume the schedule",
                    onClick = { menuFor = null; togglePause(routine) },
                )
                if (row.pendingRescheduleMillis != null) {
                    ListRow(
                        title = "Undo the move to ${TimeFormat.clock(row.pendingRescheduleMillis)}",
                        onClick = { menuFor = null; viewModel.cancelPendingReschedule(routine.id) },
                    )
                }
                ListRow(
                    title = "Delete",
                    titleColor = colors.error,
                    onClick = { menuFor = null; deleteTarget = routine },
                )
            }
        }
    }

    deleteTarget?.let { routine ->
        DeleteDialog(
            what = "\"${routine.name}\"",
            onConfirm = {
                viewModel.deleteRoutine(routine)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    pausePrompt?.let { routine ->
        ConfirmDialog(
            title = "Pause future runs?",
            message = "Future runs of \"${routine.name}\" are paused. The run that is going on " +
                "now continues until you finish or move it.",
            confirmLabel = "Pause future runs",
            onConfirm = {
                viewModel.toggleActive(routine)
                pausePrompt = null
            },
            onDismiss = { pausePrompt = null },
        )
    }
}

@Composable
private fun RoutineListRow(
    row: RoutineRow,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = FfTheme.colors
    val routine = row.routine
    val time = TimeFormat.clock(routine.startTimeHour * 60 + routine.startTimeMinute)
    val detail = when {
        row.running -> "Running now"
        row.pendingRescheduleMillis != null -> "Moved to ${TimeFormat.dayAndClock(row.pendingRescheduleMillis)}"
        else -> buildList {
            add(time)
            add(routineDaysSummary(routine.activeDays))
            if (row.totalMinutes > 0) add(TimeFormat.duration(row.totalMinutes))
            if (routine.invincibleMode) add("invincible")
        }.joinToString(" · ")
    }
    ListRow(
        title = routine.name,
        subtitle = detail,
        subtitleColor = if (row.running || row.pendingRescheduleMillis != null) colors.accent else colors.textMuted,
        titleColor = if (routine.isActive) colors.text else colors.textSecondary,
        onClick = onOpen,
        onLongClick = onMenu,
        leading = {
            IconCircle(
                icon = RoutineIcons.find(routine.iconKey)?.vector ?: Icons.Filled.ViewDay,
                container = if (routine.isActive) colors.container else colors.surfaceHigh,
                tint = if (routine.isActive) colors.onContainer else colors.textMuted,
            )
        },
        trailing = {
            IconButton(onClick = onStart) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Start ${routine.name} now",
                    tint = colors.accent,
                )
            }
        },
    )
}
