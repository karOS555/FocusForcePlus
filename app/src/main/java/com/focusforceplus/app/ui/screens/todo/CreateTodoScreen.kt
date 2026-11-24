package com.focusforceplus.app.ui.screens.todo

import com.focusforceplus.app.ui.components.ChoiceDialog
import com.focusforceplus.app.ui.components.Choice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.compliance.ComplianceLimits
import com.focusforceplus.app.compliance.GuardAction
import com.focusforceplus.app.data.model.ChecklistItem
import com.focusforceplus.app.ui.common.DiscardChangesDialog
import com.focusforceplus.app.ui.common.IconPickerButton
import com.focusforceplus.app.ui.components.CompletionRing
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.guards.GuardEditorDialog
import com.focusforceplus.app.ui.guards.GuardRow
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.FormScaffold
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.ReorderableColumn
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.StepperRow
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.staysVisibleWhenFocused
import com.focusforceplus.app.ui.components.submitsOnEnter
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType
import com.focusforceplus.app.util.TimeFormat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateTodoScreen(
    onNavigateBack: () -> Unit,
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var editGuard by remember { mutableStateOf<GuardAction?>(null) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val attemptClose = { if (viewModel.hasUnsavedChanges) showDiscard = true else onNavigateBack() }
    BackHandler { attemptClose() }

    FormScaffold(
        title = if (viewModel.isEditMode) "Edit todo" else "New todo",
        onClose = attemptClose,
        primaryLabel = if (viewModel.isEditMode) "Save changes" else "Save todo",
        onPrimary = { viewModel.saveTodo(onNavigateBack) },
        primaryLoading = state.isSaving,
        actions = {
            if (viewModel.isEditMode) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete todo")
                }
            }
        },
    ) {
        // ── What ──────────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconPickerButton(
                selectedKey = state.iconKey,
                size = 56.dp,
                onSelect = viewModel::updateIcon,
            )
            FfTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                label = "What needs doing?",
                isError = state.titleError,
                supportingText = if (state.titleError) "Give it a title" else null,
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
            label = "Notes",
            singleLine = false,
            minLines = 2,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        // ── When ──────────────────────────────────────────────────────────────
        FormSection(title = "When") {
            FfCard {
                ListRow(
                    title = "Due",
                    onClick = { showDatePicker = true },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.dueDateTime?.let { TimeFormat.dayAndClock(it) } ?: "No date",
                                style = FfType.rowTitle,
                                color = colors.accent,
                            )
                            if (state.dueDateTime != null) {
                                IconButton(onClick = { viewModel.updateDueDateTime(null) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove due date",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    },
                )
                RowDivider()
                SwitchRow(
                    title = "Repeat",
                    subtitle = when {
                        !state.isRecurring -> null
                        state.dueDateTime == null -> "Needs a due date to know when to come back"
                        else -> "A new one appears when this one is done"
                    },
                    checked = state.isRecurring,
                    onCheckedChange = viewModel::updateRecurring,
                )
                if (state.isRecurring) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly").forEach { (type, label) ->
                            FfChip(
                                label = label,
                                selected = state.recurringType == type,
                                onClick = { viewModel.updateRecurringType(type) },
                            )
                        }
                    }
                    if (state.recurringType == "WEEKLY") {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").forEach { day ->
                                FfChip(
                                    label = day.lowercase().replaceFirstChar { it.uppercase() },
                                    selected = day in state.recurringDays,
                                    onClick = { viewModel.toggleRecurringDay(day) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Priority ──────────────────────────────────────────────────────────
        FormSection(
            title = "Priority",
            hint = "The priority decides how hard this todo is to ignore.",
        ) {
            FfCard {
                PriorityRow(
                    priority = 0, selected = state.priority == 0, onSelect = viewModel::updatePriority,
                    title = "Low",
                    detail = "One notification when it is due. Swipe it away whenever you like.",
                )
                RowDivider()
                PriorityRow(
                    priority = 1, selected = state.priority == 1, onSelect = viewModel::updatePriority,
                    title = "Medium",
                    detail = "Comes up on screen without sound, then stays in your notifications until it is done.",
                )
                RowDivider()
                PriorityRow(
                    priority = 2, selected = state.priority == 2, onSelect = viewModel::updatePriority,
                    title = "High",
                    detail = "Rings like an alarm and comes back every few minutes. No dismiss; snoozes and moves are limited.",
                )
            }
        }

        // ── Speed bumps ───────────────────────────────────────────────────────
        FormSection(
            title = "Speed bumps",
            hint = if (state.guardDefaults.enabled) {
                "Small steps in front of the quick taps, so they stay a decision. " +
                    "The defaults for each priority are in Settings."
            } else {
                "Speed bumps are switched off in Settings."
            },
        ) {
            FfCard {
                GuardAction.entries.forEachIndexed { index, action ->
                    if (index > 0) RowDivider()
                    GuardRow(
                        title = guardTitle(action),
                        guard = state.guardFor(action),
                        note = if (state.guardOverrides[action] == null) "default" else "just this todo",
                        enabled = state.guardDefaults.enabled,
                        onClick = { editGuard = action },
                    )
                }
            }
        }

        // ── Snoozes and moves ─────────────────────────────────────────────────
        if (state.priority == 2) {
            FormSection(
                title = "Snoozes and moves",
                hint = "Once they are used up, the alarm only stops when the todo is done. " +
                    "You can still edit or delete it here at any time.",
            ) {
                FfCard {
                    StepperRow(
                        title = "Snoozes allowed",
                        value = state.maxSnoozeCount,
                        range = 1..ComplianceLimits.MAX_TODO_SNOOZE_LIMIT,
                        onChange = viewModel::updateMaxSnoozeCount,
                    )
                    RowDivider()
                    StepperRow(
                        title = "Moves allowed",
                        value = state.maxRescheduleCount.coerceIn(1, ComplianceLimits.MAX_TODO_RESCHEDULE_LIMIT),
                        range = 1..ComplianceLimits.MAX_TODO_RESCHEDULE_LIMIT,
                        onChange = viewModel::updateMaxRescheduleCount,
                    )
                }
            }
        }

        // ── List ──────────────────────────────────────────────────────────────
        if (state.lists.isNotEmpty() || state.listId != null) {
            var pickList by remember { mutableStateOf(false) }
            FormSection(
                title = "List",
                hint = "Its open items show up on the alarm, ready to tick off.",
            ) {
                FfCard {
                    ValueRow(
                        title = "List for this todo",
                        value = state.lists.firstOrNull { it.id == state.listId }?.name ?: "None",
                        onClick = { pickList = true },
                    )
                }
            }
            if (pickList) {
                ChoiceDialog(
                    title = "List for this todo",
                    choices = listOf(Choice(-1L, "None")) + state.lists.map { Choice(it.id, it.name) },
                    selected = state.listId ?: -1L,
                    onSelect = { id ->
                        viewModel.setList(id.takeIf { it > 0L })
                        pickList = false
                    },
                    onDismiss = { pickList = false },
                )
            }
        }

        // ── Checklist ─────────────────────────────────────────────────────────
        FormSection(title = "Checklist") {
            ChecklistEditor(
                items = state.checklistItems,
                keys = state.checklistKeys,
                onToggle = viewModel::toggleChecklistItem,
                onRemove = viewModel::removeChecklistItem,
                onAdd = viewModel::addChecklistItem,
                onDraft = viewModel::updateChecklistDraft,
                onEdit = viewModel::editChecklistItem,
                onMove = viewModel::moveChecklistItem,
            )
        }

        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }

    // ── Date, then time ───────────────────────────────────────────────────────
    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        // The date picker thinks in UTC midnights; feed it the local date as such.
        val initialUtc = state.dueDateTime?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        pendingDateMillis = dateState.selectedDateMillis
                        showDatePicker = false
                        showTimePicker = true
                    },
                ) { Text("Next", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = colors.textSecondary) }
            },
        ) { DatePicker(state = dateState) }
    }

    if (showTimePicker) {
        val zone = ZoneId.systemDefault()
        val current = state.dueDateTime?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        val timeState = rememberTimePickerState(
            initialHour = current?.hour ?: 9,
            initialMinute = current?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = colors.surfaceVariant,
            title = { Text("At what time?", style = MaterialTheme.typography.titleLarge, color = colors.text) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dateUtc = pendingDateMillis
                    if (dateUtc != null) {
                        val date = Instant.ofEpochMilli(dateUtc).atZone(ZoneOffset.UTC).toLocalDate()
                        val due = date.atTime(timeState.hour, timeState.minute)
                            .atZone(zone).toInstant().toEpochMilli()
                        viewModel.updateDueDateTime(due)
                    }
                    showTimePicker = false
                }) { Text("Set", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = colors.textSecondary) }
            },
        )
    }

    editGuard?.let { action ->
        GuardEditorDialog(
            title = guardTitle(action),
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
            what = "this todo",
            onConfirm = {
                showDelete = false
                viewModel.deleteTodo(onNavigateBack)
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

private fun guardTitle(action: GuardAction): String = when (action) {
    GuardAction.DONE -> "Marking it done"
    GuardAction.SNOOZE -> "Snoozing"
    GuardAction.RESCHEDULE -> "Rescheduling"
}

@Composable
private fun PriorityRow(
    priority: Int,
    selected: Boolean,
    onSelect: (Int) -> Unit,
    title: String,
    detail: String,
) {
    val colors = FfTheme.colors
    ListRow(
        title = title,
        subtitle = detail,
        // All of it: this is where the difference between the three is explained.
        subtitleMaxLines = 6,
        onClick = { onSelect(priority) },
        leading = {
            CompletionRing(
                checked = false,
                onClick = { onSelect(priority) },
                color = colors.priority(priority),
                contentDescription = "$title priority",
            )
        },
        trailing = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(20.dp))
            } else {
                Spacer(Modifier.size(20.dp))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistEditor(
    items: List<ChecklistItem>,
    keys: List<Long>,
    onToggle: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: (String) -> Unit,
    /** What stands in the add field and has not been added yet. */
    onDraft: (String) -> Unit,
    onEdit: (Int, String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val colors = FfTheme.colors
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val addRow = remember { BringIntoViewRequester() }
    var draft by remember { mutableStateOf("") }
    val submit = {
        // A pasted note with one thing per line becomes one item per line.
        draft.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach(onAdd)
        draft = ""
        onDraft("")
        // The new row pushes the field down, with the keyboard open usually out of
        // sight: follow it, so the next item can be typed straight away.
        scope.launch {
            repeat(2) { withFrameNanos { } }
            addRow.bringIntoView()
        }
        Unit
    }
    FfCard {
        if (items.isNotEmpty()) {
            // Each row is known by an id that travels with it. A key made from
            // its position changed with every move and ended the drag after one row.
            val indexed = items.withIndex().toList()
            ReorderableColumn(
                items = indexed,
                key = { keys.getOrElse(it.index) { _ -> -1L - it.index } },
                // As tall as the text: an item is shown whole, however long it is.
                rowHeight = null,
                onMove = onMove,
            ) { entry, handle, _ ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(start = 2.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = handle.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = colors.textFaint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    CompletionRing(
                        checked = entry.value.done,
                        onClick = { onToggle(entry.index) },
                        contentDescription = entry.value.text,
                    )
                    Spacer(Modifier.width(4.dp))
                    // The item itself is the field: tap it to change the wording.
                    BasicTextField(
                        value = entry.value.text,
                        onValueChange = { onEdit(entry.index, it) },
                        textStyle = FfType.rowTitle.copy(
                            color = if (entry.value.done) colors.textMuted else colors.text,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        modifier = Modifier
                            .weight(1f)
                            // Around the padding, so the row keeps some air above the
                            // keyboard when it is brought into view.
                            .staysVisibleWhenFocused()
                            .padding(vertical = 10.dp),
                    )
                    IconButton(onClick = { onRemove(entry.index) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove ${entry.value.text}",
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            RowDivider()
        }
        Row(
            modifier = Modifier
                .bringIntoViewRequester(addRow)
                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FfTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onDraft(it)
                },
                placeholder = "Add an item",
                // Several lines, so a long item stays readable while it is typed.
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f).submitsOnEnter(submit),
            )
            IconButton(onClick = submit, enabled = draft.isNotBlank()) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add item",
                    tint = if (draft.isNotBlank()) colors.accent else colors.textFaint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
