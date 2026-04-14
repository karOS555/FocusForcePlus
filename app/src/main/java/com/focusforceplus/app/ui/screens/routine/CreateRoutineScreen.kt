package com.focusforceplus.app.ui.screens.routine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.ui.common.IconPickerButton
import com.focusforceplus.app.ui.common.IconPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoutineScreen(
    onNavigateBack: () -> Unit,
    viewModel: RoutineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (viewModel.isEditMode) "Edit Routine" else "New Routine")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.isEditMode) {
                        IconButton(onClick = { viewModel.deleteRoutine(onNavigateBack) }) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = "Delete routine",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Name + Icon row ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconPickerButton(
                    selectedKey = uiState.iconKey,
                    size        = 56.dp,
                    onSelect    = viewModel::updateIconKey,
                )
                OutlinedTextField(
                    value         = uiState.name,
                    onValueChange = viewModel::updateName,
                    label         = { Text("Routine name *") },
                    isError       = uiState.nameError,
                    supportingText = if (uiState.nameError) ({ Text("Required") }) else null,
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value         = uiState.description,
                onValueChange = viewModel::updateDescription,
                label         = { Text("Description (optional)") },
                placeholder   = { Text("Shown on the alarm screen as a reminder") },
                minLines      = 2,
                maxLines      = 4,
                modifier      = Modifier.fillMaxWidth(),
            )

            TimeSection(
                hour   = uiState.startTimeHour,
                minute = uiState.startTimeMinute,
                onTimeSelected = viewModel::updateStartTime,
            )

            SectionTitle("Days *")
            WeekdaySelectorRow(
                selectedDays = uiState.activeDays,
                hasError     = uiState.daysError,
                onToggle     = viewModel::toggleDay,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingsToggleRow(
                title    = "Invincible Mode",
                subtitle = "Routine cannot be stopped or skipped",
                checked  = uiState.invincibleMode,
                onCheckedChange = viewModel::updateInvincibleMode,
            )

            SettingsToggleRow(
                title    = "App Blocker",
                subtitle = "Block distracting apps during this routine",
                checked  = uiState.appBlockerEnabled,
                onCheckedChange = viewModel::updateAppBlocker,
            )

            SnoozeDropdown(value = uiState.maxSnoozeCount, onChange = viewModel::updateMaxSnooze)

            Spacer(Modifier.height(0.dp))

            RescheduleDropdown(value = uiState.maxRescheduleCount, onChange = viewModel::updateMaxReschedule)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionTitle("Tasks")
            TasksSection(
                tasks    = viewModel.tasks,
                onAdd    = viewModel::addTask,
                onUpdate = viewModel::updateTask,
                onDelete = viewModel::deleteTask,
                onSwap   = viewModel::swapTasks,
            )
            if (uiState.tasksError) {
                Text(
                    text  = "Add at least one task before saving.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick  = { viewModel.saveRoutine(onNavigateBack) },
                enabled  = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save Routine", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Section helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked        = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

// ─── Weekday selector ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdaySelectorRow(
    selectedDays: Set<String>,
    hasError: Boolean,
    onToggle: (String) -> Unit,
) {
    val days = listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { day ->
                FilterChip(
                    selected = day in selectedDays,
                    onClick  = { onToggle(day) },
                    label    = { Text(day) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor     = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        if (hasError) {
            Text(
                "Select at least one day",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

// ─── Time picker ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSection(hour: Int, minute: Int, onTimeSelected: (Int, Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            SectionTitle("Start Time")
            Text(
                text  = "%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = { showDialog = true }) {
            Icon(
                Icons.Filled.AccessTime,
                contentDescription = "Change time",
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
    }

    if (showDialog) {
        val pickerState = rememberTimePickerState(
            initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            dismissButton    = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
            confirmButton    = {
                Button(onClick = {
                    onTimeSelected(pickerState.hour, pickerState.minute)
                    showDialog = false
                }) { Text("OK") }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

// ─── Snooze / Reschedule dropdowns ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnoozeDropdown(value: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = "${value}x  snooze allowed",
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Snooze limit") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier      = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(1, 2, 3).forEach { count ->
                DropdownMenuItem(
                    text    = { Text("${count}x snooze") },
                    onClick = { onChange(count); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleDropdown(value: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = "${value}x reschedule allowed",
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Reschedule limit") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier      = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(0, 1, 2, 3).forEach { count ->
                DropdownMenuItem(
                    text    = { Text(if (count == 0) "No reschedule" else "${count}x reschedule") },
                    onClick = { onChange(count); expanded = false },
                )
            }
        }
    }
}

// ─── Task list with drag & drop ───────────────────────────────────────────────

@Composable
private fun TasksSection(
    tasks: List<TaskUiItem>,
    onAdd: () -> Unit,
    onUpdate: (TaskUiItem) -> Unit,
    onDelete: (String) -> Unit,
    onSwap: (Int, Int) -> Unit,
) {
    var draggingIndex  by remember { mutableStateOf(-1) }
    var draggingOffsetY by remember { mutableStateOf(0f) }
    val itemTopOffsets = remember { mutableStateMapOf<Int, Float>() }
    val itemHeights    = remember { mutableStateMapOf<Int, Float>() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tasks.forEachIndexed { index, task ->
            DraggableTaskItem(
                task        = task,
                isDragging  = index == draggingIndex,
                dragOffsetY = if (index == draggingIndex) draggingOffsetY else 0f,
                onDragStart = { draggingIndex = index; draggingOffsetY = 0f },
                onDrag      = { deltaY ->
                    draggingOffsetY += deltaY
                    val myTop    = (itemTopOffsets[index] ?: 0f) + draggingOffsetY
                    val myCenter = myTop + (itemHeights[index] ?: 0f) / 2f
                    val target = tasks.indices.firstOrNull { i ->
                        i != index && run {
                            val top    = itemTopOffsets[i] ?: return@firstOrNull false
                            val height = itemHeights[i]   ?: return@firstOrNull false
                            myCenter in top..(top + height)
                        }
                    }
                    if (target != null) {
                        onSwap(index, target)
                        draggingIndex  = target
                        draggingOffsetY = 0f
                    }
                },
                onDragEnd   = { draggingIndex = -1; draggingOffsetY = 0f },
                onPositioned = { top, height ->
                    itemTopOffsets[index] = top
                    itemHeights[index]    = height
                },
                onUpdate = onUpdate,
                onDelete = onDelete,
            )
        }

        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add task")
        }
    }
}

// ─── Single task card ─────────────────────────────────────────────────────────

@Composable
private fun DraggableTaskItem(
    task: TaskUiItem,
    isDragging: Boolean,
    dragOffsetY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onPositioned: (top: Float, height: Float) -> Unit,
    onUpdate: (TaskUiItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    // Notes section is auto-expanded when the task already has content.
    var showNotes      by rememberSaveable(task.tempId) { mutableStateOf(task.notes.isNotEmpty()) }
    var showNotesInfo  by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                onPositioned(
                    coords.positionInParent().y,
                    coords.size.height.toFloat(),
                )
            }
            .graphicsLayer {
                translationY  = dragOffsetY
                shadowElevation = if (isDragging) 12.dp.toPx() else 0f
                scaleX        = if (isDragging) 1.02f else 1f
                scaleY        = if (isDragging) 1.02f else 1f
            }
            .zIndex(if (isDragging) 1f else 0f),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {

            // ── Row 1: drag handle · icon · name · delete ─────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Drag handle (long-press)
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Hold to reorder",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .pointerInput(task.tempId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart   = { onDragStart() },
                                onDragEnd     = { onDragEnd() },
                                onDragCancel  = { onDragEnd() },
                                onDrag        = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                },
                            )
                        },
                )

                // Icon selector
                IconPickerButton(
                    selectedKey = task.iconKey,
                    size        = 40.dp,
                    onSelect    = { key -> onUpdate(task.copy(iconKey = key)) },
                )

                // Task name
                OutlinedTextField(
                    value         = task.name,
                    onValueChange = { onUpdate(task.copy(name = it)) },
                    label         = { Text("Task name") },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                )

                // Delete
                IconButton(
                    onClick  = { onDelete(task.tempId) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete task",
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Row 2: duration stepper · notes toggle ────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Duration stepper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalButton(
                        onClick         = {
                            if (task.durationMinutes > 1)
                                onUpdate(task.copy(durationMinutes = task.durationMinutes - 1))
                        },
                        modifier        = Modifier.size(36.dp),
                        contentPadding  = PaddingValues(0.dp),
                        shape           = RoundedCornerShape(8.dp),
                    ) {
                        Text("\u2212", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text      = "${task.durationMinutes} min",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.width(60.dp),
                    )
                    FilledTonalButton(
                        onClick        = { onUpdate(task.copy(durationMinutes = task.durationMinutes + 1)) },
                        modifier       = Modifier.size(36.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape          = RoundedCornerShape(8.dp),
                    ) {
                        Text("+", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Notes toggle + info icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick        = { showNotes = !showNotes },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (showNotes) "Hide note" else "Add note",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    IconButton(
                        onClick  = { showNotesInfo = !showNotesInfo },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "About notes",
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Info text (toggled by "?")
            AnimatedVisibility(visible = showNotesInfo) {
                Text(
                    text     = "Notes are shown on-screen while the task is running. " +
                               "Use them for checklists, reminders, or step-by-step instructions.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            // Notes text field (expandable)
            AnimatedVisibility(
                visible = showNotes,
                enter   = expandVertically(),
                exit    = shrinkVertically(),
            ) {
                OutlinedTextField(
                    value         = task.notes,
                    onValueChange = { onUpdate(task.copy(notes = it)) },
                    label         = { Text("Note (optional)") },
                    placeholder   = { Text("e.g. Remember to do X, checklist items...") },
                    minLines      = 2,
                    maxLines      = 5,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }

    // Notes info dialog (from "?" icon) — optional fuller explanation
    if (showIconPicker) {
        IconPickerDialog(
            selectedKey = task.iconKey,
            onSelect    = { key -> onUpdate(task.copy(iconKey = key)); showIconPicker = false },
            onDismiss   = { showIconPicker = false },
        )
    }
}
