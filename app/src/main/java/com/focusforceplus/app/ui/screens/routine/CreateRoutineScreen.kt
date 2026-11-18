package com.focusforceplus.app.ui.screens.routine

import androidx.compose.ui.platform.LocalContext
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.ui.components.ChoiceDialog
import com.focusforceplus.app.ui.components.Choice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.compliance.GuardAction
import com.focusforceplus.app.ui.common.DiscardChangesDialog
import com.focusforceplus.app.ui.common.IconPickerButton
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.FfTimePickerDialog
import com.focusforceplus.app.ui.components.FormScaffold
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.OutlineButton
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.StepperRow
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.guards.GuardEditorDialog
import com.focusforceplus.app.ui.guards.GuardRow
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType
import com.focusforceplus.app.util.TimeFormat

private val DAYS = listOf(
    "MO" to "Mon", "DI" to "Tue", "MI" to "Wed", "DO" to "Thu",
    "FR" to "Fri", "SA" to "Sat", "SO" to "Sun",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateRoutineScreen(
    onNavigateBack: () -> Unit,
    /** Called with the saved routine's id before navigating back (e.g. to link it). */
    onRoutineSaved: (Long) -> Unit = {},
    viewModel: RoutineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val templates by viewModel.taskTemplates.collectAsState()
    val lists by viewModel.lists.collectAsState()
    var pickHeadsUp by remember { mutableStateOf(false) }
    val blockGroups by viewModel.blockGroups.collectAsState()
    val context = LocalContext.current
    val others by viewModel.otherRoutines.collectAsState()
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val running = viewModel.isCurrentlyRunning

    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showCopyFrom by remember { mutableStateOf(false) }
    var editGuard by remember { mutableStateOf<GuardAction?>(null) }

    val attemptClose = { if (viewModel.hasUnsavedChanges) showDiscard = true else onNavigateBack() }
    BackHandler { attemptClose() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val totalMinutes = viewModel.tasks.sumOf { it.durationMinutes }
    val endMinutes = (state.startTimeHour * 60 + state.startTimeMinute + totalMinutes) % (24 * 60)

    FormScaffold(
        title = if (viewModel.isEditMode) "Edit routine" else "New routine",
        onClose = attemptClose,
        primaryLabel = if (viewModel.isEditMode) "Save changes" else "Save routine",
        onPrimary = {
            viewModel.saveRoutine { savedId ->
                onRoutineSaved(savedId)
                onNavigateBack()
            }
        },
        primaryLoading = state.isSaving,
        snackbarHostState = snackbar,
        actions = {
            if (viewModel.isEditMode) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete routine")
                }
            }
        },
    ) {
        if (running) {
            FfPaddedCard(color = colors.container) {
                Text(
                    "This routine is running right now. Changes apply to its next runs. " +
                        "Invincible mode and the limits stay as they are until this run ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onContainer,
                )
            }
        }

        // ── What ──────────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconPickerButton(selectedKey = state.iconKey, size = 56.dp, onSelect = viewModel::updateIconKey)
            FfTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = "Name",
                isError = state.nameError,
                supportingText = if (state.nameError) "Give it a name" else null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        FfTextField(
            value = state.description,
            onValueChange = viewModel::updateDescription,
            label = "Note for the alarm screen",
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        // ── When ──────────────────────────────────────────────────────────────
        FormSection(title = "When") {
            FfCard {
                ValueRow(
                    title = "Starts at",
                    value = TimeFormat.clock(state.startTimeHour * 60 + state.startTimeMinute),
                    onClick = { showTime = true },
                )
                RowDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("Days", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DAYS.forEach { (key, label) ->
                            FfChip(
                                label = label,
                                selected = key in state.activeDays,
                                onClick = { viewModel.toggleDay(key) },
                            )
                        }
                    }
                    if (state.daysError) {
                        Text("Pick at least one day", style = MaterialTheme.typography.bodySmall, color = colors.error)
                    }
                }
                RowDivider()
                ValueRow(
                    title = "Heads-up before",
                    value = if (state.preAlarmMinutes == 0) "Off" else "${state.preAlarmMinutes} min",
                    onClick = { pickHeadsUp = true },
                )
                RowDivider()
                SwitchRow(
                    title = "On schedule",
                    subtitle = if (state.isActive) "The alarm goes off on these days." else
                        "Paused. You can still start it by hand.",
                    checked = state.isActive,
                    onCheckedChange = viewModel::updateActive,
                )
            }
        }

        // ── Steps ─────────────────────────────────────────────────────────────
        FormSection(
            title = "Steps",
            hint = if (viewModel.tasks.isEmpty()) null else
                "${viewModel.tasks.size} steps · ${TimeFormat.duration(totalMinutes)} · " +
                    "ends at ${TimeFormat.clock(endMinutes)}",
        ) {
            StepList(
                tasks = viewModel.tasks,
                templates = templates,
                lists = lists,
                onUpdate = viewModel::updateTask,
                onDelete = viewModel::deleteTask,
                onSwap = viewModel::swapTasks,
                onTemplate = viewModel::applyTemplate,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(
                    text = "Add a step",
                    icon = Icons.Filled.Add,
                    onClick = viewModel::addTask,
                    modifier = Modifier.weight(1f),
                )
                if (others.isNotEmpty()) {
                    OutlineButton(
                        text = "Copy from…",
                        onClick = { showCopyFrom = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.tasksError) {
                Text("Add at least one step", style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
        }

        // ── While it runs ─────────────────────────────────────────────────────
        FormSection(title = "While it runs") {
            FfCard {
                SwitchRow(
                    title = "Invincible mode",
                    subtitle = if (running) "Locked while the routine runs." else
                        "Once started, it can only be finished or moved, not cancelled.",
                    checked = state.invincibleMode,
                    onCheckedChange = viewModel::updateInvincibleMode,
                    enabled = !running,
                )
                RowDivider()
                SwitchRow(
                    title = "Block distracting apps",
                    subtitle = "Your blocker rules apply while it runs.",
                    checked = state.appBlockerEnabled,
                    onCheckedChange = viewModel::updateAppBlocker,
                )
                if (state.appBlockerEnabled && blockGroups.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FfChip(
                                label = "All blocked apps",
                                selected = state.blockAllApps,
                                onClick = { viewModel.updateBlockAllApps(true) },
                            )
                            FfChip(
                                label = "Only some groups",
                                selected = !state.blockAllApps,
                                onClick = { viewModel.updateBlockAllApps(false) },
                            )
                        }
                        if (!state.blockAllApps) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                blockGroups.forEach { group ->
                                    FfChip(
                                        label = group,
                                        selected = group in state.selectedGroups,
                                        onClick = { viewModel.toggleBlockedGroup(group) },
                                    )
                                }
                            }
                            if (state.selectedGroups.isEmpty()) {
                                Text(
                                    "Pick at least one group, or block all apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.error,
                                )
                            }
                        }
                    }
                }
                RowDivider()
                val dndGranted = PermissionHelper.hasDndAccess(context)
                SwitchRow(
                    title = "Do Not Disturb",
                    subtitle = if (dndGranted) "Silences calls and notifications while it runs, except priority ones." else
                        "Needs Do Not Disturb access; switching it on opens the setting.",
                    checked = state.enableDnd,
                    onCheckedChange = { on ->
                        if (on && !dndGranted) PermissionHelper.openDndAccessSettings(context)
                        viewModel.updateDnd(on)
                    },
                )
            }
        }
        if (pickHeadsUp) {
            ChoiceDialog(
                title = "Heads-up before the start",
                choices = listOf(0, 5, 10, 15, 30).map { Choice(it, if (it == 0) "Off" else "$it min") },
                selected = state.preAlarmMinutes,
                onSelect = {
                    viewModel.updatePreAlarm(it)
                    pickHeadsUp = false
                },
                onDismiss = { pickHeadsUp = false },
            )
        }

        // ── Speed bumps ───────────────────────────────────────────────────────
        FormSection(
            title = "Speed bumps",
            hint = if (state.guardDefaults.enabled) null else "Speed bumps are switched off in Settings.",
        ) {
            FfCard {
                listOf(GuardAction.SNOOZE, GuardAction.RESCHEDULE).forEachIndexed { index, action ->
                    if (index > 0) RowDivider()
                    GuardRow(
                        title = if (action == GuardAction.SNOOZE) "Snoozing the alarm" else "Moving it",
                        guard = state.guardFor(action),
                        note = if (state.guardOverrides[action] == null) "default" else "just this routine",
                        enabled = state.guardDefaults.enabled,
                        onClick = { editGuard = action },
                    )
                }
            }
        }

        // ── Snoozes and moves ─────────────────────────────────────────────────
        FormSection(
            title = "Snoozes and moves",
            hint = if (running) "Locked while the routine runs." else
                "How often the alarm can be put off, per run.",
        ) {
            FfCard {
                StepperRow(
                    title = "Snoozes allowed",
                    value = state.maxSnoozeCount,
                    range = 1..3,
                    onChange = viewModel::updateMaxSnooze,
                    enabled = !running,
                )
                RowDivider()
                StepperRow(
                    title = "Moves allowed",
                    value = state.maxRescheduleCount,
                    range = 0..3,
                    onChange = viewModel::updateMaxReschedule,
                    valueLabel = { if (it == 0) "None" else it.toString() },
                    enabled = !running,
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showTime) {
        FfTimePickerDialog(
            title = "Starts at",
            hour = state.startTimeHour,
            minute = state.startTimeMinute,
            onConfirm = { h, m ->
                viewModel.updateStartTime(h, m)
                showTime = false
            },
            onDismiss = { showTime = false },
        )
    }

    if (showCopyFrom) {
        AlertDialog(
            onDismissRequest = { showCopyFrom = false },
            containerColor = colors.surfaceVariant,
            title = { Text("Copy steps from", style = MaterialTheme.typography.titleLarge, color = colors.text) },
            text = {
                Column {
                    Text(
                        "All steps of the routine you pick are added below the ones you have.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    others.forEach { (routine, count) ->
                        ListRow(
                            title = routine.name,
                            subtitle = "$count steps",
                            onClick = {
                                viewModel.copyStepsFrom(routine.id)
                                showCopyFrom = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCopyFrom = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                }
            },
        )
    }

    editGuard?.let { action ->
        GuardEditorDialog(
            title = if (action == GuardAction.SNOOZE) "Snoozing the alarm" else "Moving it",
            current = state.guardOverrides[action],
            default = state.defaultGuard(action),
            onSave = {
                viewModel.updateGuard(action, it)
                editGuard = null
            },
            onDismiss = { editGuard = null },
        )
    }

    if (showDelete) {
        DeleteDialog(
            what = "this routine",
            onConfirm = {
                showDelete = false
                viewModel.deleteRoutine(onNavigateBack)
            },
            onDismiss = { showDelete = false },
        )
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDiscard = { showDiscard = false; onNavigateBack() },
            onKeepEditing = { showDiscard = false },
        )
    }
}

// ── Steps with drag and drop ──────────────────────────────────────────────────

@Composable
private fun StepList(
    tasks: List<TaskUiItem>,
    templates: List<TaskTemplate>,
    lists: List<com.focusforceplus.app.data.db.entity.ListEntity>,
    onUpdate: (TaskUiItem) -> Unit,
    onDelete: (String) -> Unit,
    onSwap: (Int, Int) -> Unit,
    onTemplate: (String, TaskTemplate) -> Unit,
) {
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    val tops = remember { mutableStateMapOf<Int, Float>() }
    val heights = remember { mutableStateMapOf<Int, Float>() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tasks.forEachIndexed { index, task ->
            StepCard(
                index = index,
                task = task,
                templates = templates,
                lists = lists,
                dragging = index == draggingIndex,
                dragOffsetY = if (index == draggingIndex) draggingOffsetY else 0f,
                onDragStart = { draggingIndex = index; draggingOffsetY = 0f },
                onDrag = { deltaY ->
                    draggingOffsetY += deltaY
                    val myTop = (tops[index] ?: 0f) + draggingOffsetY
                    val myCenter = myTop + (heights[index] ?: 0f) / 2f
                    val target = tasks.indices.firstOrNull { i ->
                        i != index && run {
                            val top = tops[i] ?: return@firstOrNull false
                            val height = heights[i] ?: return@firstOrNull false
                            myCenter in top..(top + height)
                        }
                    }
                    if (target != null) {
                        onSwap(index, target)
                        draggingIndex = target
                        draggingOffsetY = 0f
                    }
                },
                onDragEnd = { draggingIndex = -1; draggingOffsetY = 0f },
                onPositioned = { top, height ->
                    tops[index] = top
                    heights[index] = height
                },
                onUpdate = onUpdate,
                onDelete = onDelete,
                onTemplate = { onTemplate(task.tempId, it) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCard(
    index: Int,
    task: TaskUiItem,
    templates: List<TaskTemplate>,
    lists: List<com.focusforceplus.app.data.db.entity.ListEntity>,
    dragging: Boolean,
    dragOffsetY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onPositioned: (top: Float, height: Float) -> Unit,
    onUpdate: (TaskUiItem) -> Unit,
    onDelete: (String) -> Unit,
    onTemplate: (TaskTemplate) -> Unit,
) {
    val colors = FfTheme.colors
    var showNote by rememberSaveable(task.tempId) { mutableStateOf(task.notes.isNotEmpty()) }
    var pickList by remember { mutableStateOf(false) }
    var nameFocused by remember { mutableStateOf(false) }

    // Steps from other routines that match what is being typed.
    val query = task.name.trim()
    val matches = if (!nameFocused || query.isEmpty()) emptyList() else templates
        .filter { it.name.contains(query, ignoreCase = true) && !it.name.equals(query, ignoreCase = true) }
        .take(4)

    FfCard(
        color = if (dragging) colors.surfaceVariant else colors.surface,
        modifier = Modifier
            .onGloballyPositioned { coords ->
                onPositioned(coords.positionInParent().y, coords.size.height.toFloat())
            }
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = if (dragging) 1.02f else 1f
                scaleY = if (dragging) 1.02f else 1f
            }
            .zIndex(if (dragging) 1f else 0f),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(task.tempId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDrag(amount.y)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Press and hold to reorder step ${index + 1}",
                        tint = colors.textFaint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconPickerButton(
                    selectedKey = task.iconKey,
                    size = 40.dp,
                    onSelect = { onUpdate(task.copy(iconKey = it)) },
                )
                Spacer(Modifier.width(8.dp))
                FfTextField(
                    value = task.name,
                    onValueChange = { onUpdate(task.copy(name = it)) },
                    label = "Step ${index + 1}",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { nameFocused = it.hasFocus },
                )
                IconButton(onClick = { onDelete(task.tempId) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove step ${index + 1}", tint = colors.textMuted)
                }
            }

            if (matches.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(start = 92.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    matches.forEach { template ->
                        FfChip(
                            label = "${template.name} · ${template.durationMinutes} min",
                            selected = false,
                            onClick = { onTemplate(template) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 84.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MinuteStepper(
                    value = task.durationMinutes,
                    onChange = { onUpdate(task.copy(durationMinutes = it)) },
                )
                Spacer(Modifier.weight(1f))
                if (lists.isNotEmpty() || task.listId != null) {
                    TextAction(
                        lists.firstOrNull { it.id == task.listId }?.name ?: "Add a list",
                        onClick = { pickList = true },
                        color = if (task.listId != null) colors.accent else colors.textSecondary,
                    )
                }
                TextAction(
                    if (showNote) "Hide note" else "Add a note",
                    onClick = { showNote = !showNote },
                    color = colors.textSecondary,
                )
            }
            if (pickList) {
                com.focusforceplus.app.ui.components.ChoiceDialog(
                    title = "List for this step",
                    choices = listOf(com.focusforceplus.app.ui.components.Choice(-1L, "None")) +
                        lists.map { com.focusforceplus.app.ui.components.Choice(it.id, it.name) },
                    selected = task.listId ?: -1L,
                    onSelect = { id ->
                        onUpdate(task.copy(listId = id.takeIf { it > 0L }))
                        pickList = false
                    },
                    onDismiss = { pickList = false },
                )
            }
            if (showNote) {
                FfTextField(
                    value = task.notes,
                    onValueChange = { onUpdate(task.copy(notes = it)) },
                    label = "Shown while this step runs",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MinuteStepper(value: Int, onChange: (Int) -> Unit) {
    val colors = FfTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Filled.Remove, "Shorter", enabled = value > 1) { onChange((value - 1).coerceAtLeast(1)) }
        Text(
            "$value min",
            style = FfType.rowTitle,
            color = colors.accent,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        StepButton(Icons.Filled.Add, "Longer", enabled = value < 240) { onChange((value + 1).coerceAtMost(240)) }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = FfTheme.colors
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (enabled) colors.text else colors.textFaint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
