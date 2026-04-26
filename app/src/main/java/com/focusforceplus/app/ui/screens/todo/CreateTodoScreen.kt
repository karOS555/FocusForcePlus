package com.focusforceplus.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.data.model.ChecklistItem
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.ui.theme.Error
import com.focusforceplus.app.ui.theme.Warning
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LowColor    = Color(0xFF94a3b8)
private val MediumColor = Warning
private val HighColor   = Error

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateTodoScreen(
    onNavigateBack: () -> Unit,
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Holds the date chosen in DatePickerDialog before the TimePicker is shown.
    // Pre-seeded with the existing dueDateTime so it's never null for edit mode.
    var pendingDateMillis by remember { mutableStateOf<Long?>(uiState.dueDateTime) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.dueDateTime,
    )
    // Sync datePickerState when the todo loads asynchronously (edit mode: dueDateTime
    // starts null and arrives after the first recomposition).
    LaunchedEffect(uiState.dueDateTime) {
        val due = uiState.dueDateTime
        if (due != null) {
            datePickerState.selectedDateMillis = due
            if (pendingDateMillis == null) pendingDateMillis = due
        }
    }

    val initialHour = uiState.dueDateTime?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).hour
    } ?: 9
    val initialMinute = uiState.dueDateTime?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).minute
    } ?: 0
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Edit Todo" else "New Todo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Title ──────────────────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Title") },
                isError = uiState.titleError,
                supportingText = if (uiState.titleError) {
                    { Text("Title is required") }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Description ────────────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Description (optional)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Checklist ──────────────────────────────────────────────────────
            SectionLabel("Checklist")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Enable checklist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Switch(
                    checked = uiState.checklistEnabled,
                    onCheckedChange = viewModel::toggleChecklist,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            if (uiState.checklistEnabled) {
                ChecklistEditor(
                    items    = uiState.checklistItems,
                    onToggle = viewModel::toggleChecklistItem,
                    onRemove = viewModel::removeChecklistItem,
                    onAdd    = viewModel::addChecklistItem,
                    onMove   = viewModel::moveChecklistItem,
                )
            }

            // ── Due date ───────────────────────────────────────────────────────
            SectionLabel("Due date")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = uiState.dueDateTime?.let { formatDateTime(it) } ?: "Set due date",
                    )
                }
                if (uiState.dueDateTime != null) {
                    IconButton(onClick = { viewModel.updateDueDateTime(null) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear due date",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Priority ───────────────────────────────────────────────────────
            SectionLabel("Priority")
            PrioritySelector(
                selected = uiState.priority,
                onSelect = viewModel::updatePriority,
            )

            // ── High priority settings ─────────────────────────────────────────
            if (uiState.priority == 2) {
                HighPrioritySettings(
                    maxSnoozeCount = uiState.maxSnoozeCount,
                    maxRescheduleCount = uiState.maxRescheduleCount,
                    onSnoozeChange = viewModel::updateMaxSnoozeCount,
                    onRescheduleChange = viewModel::updateMaxRescheduleCount,
                )
            }

            // ── Recurring ──────────────────────────────────────────────────────
            SectionLabel("Recurring")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Repeat this todo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Switch(
                    checked = uiState.isRecurring,
                    onCheckedChange = viewModel::updateRecurring,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }

            if (uiState.isRecurring) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "DAILY" to "Daily",
                        "WEEKLY" to "Weekly",
                        "MONTHLY" to "Monthly",
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = uiState.recurringType == type,
                            onClick = { viewModel.updateRecurringType(type) },
                            label = { Text(label) },
                        )
                    }
                }

                if (uiState.recurringType == "WEEKLY") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").forEach { day ->
                            FilterChip(
                                selected = day in uiState.recurringDays,
                                onClick = { viewModel.toggleRecurringDay(day) },
                                label = { Text(day) },
                            )
                        }
                    }
                }
            }

            // ── Error ──────────────────────────────────────────────────────────
            uiState.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // ── Save ───────────────────────────────────────────────────────────
            Button(
                onClick = { viewModel.saveTodo(onNavigateBack) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(12.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (viewModel.isEditMode) "Save changes" else "Save",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Date picker dialog ─────────────────────────────────────────────────────
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Use the picker's selection; fall back to already-pending date
                        val picked = datePickerState.selectedDateMillis ?: pendingDateMillis
                        if (picked != null) {
                            pendingDateMillis = picked
                            showDatePicker = false
                            showTimePicker = true
                        }
                    },
                    enabled = datePickerState.selectedDateMillis != null || pendingDateMillis != null,
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Time picker dialog ─────────────────────────────────────────────────────
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dateMs = pendingDateMillis ?: return@TextButton
                    viewModel.updateDueDateTime(combineDateAndTime(dateMs, timePickerState.hour, timePickerState.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Priority selector with description cards ──────────────────────────────────

@Composable
private fun PrioritySelector(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PriorityCard(
            value = 0,
            label = "Low",
            accentColor = LowColor,
            description = "Normal notification when due. No alarm screen. Dismiss and Done available directly.",
            selected = selected == 0,
            onClick = { onSelect(0) },
        )
        PriorityCard(
            value = 1,
            label = "Medium",
            accentColor = MediumColor,
            description = "Silent alarm screen (no sound/vibration). Snooze, reschedule, and dismiss available. Stays as a soft reminder after dismissal.",
            selected = selected == 1,
            onClick = { onSelect(1) },
        )
        PriorityCard(
            value = 2,
            label = "High",
            accentColor = HighColor,
            description = "Loud alarm screen with sound and vibration. No dismiss. Done requires confirmation. Non-dismissable ongoing notification until completed.",
            selected = selected == 2,
            onClick = { onSelect(2) },
        )
    }
}

@Composable
private fun PriorityCard(
    value: Int,
    label: String,
    accentColor: Color,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor     = if (selected) accentColor.copy(alpha = 0.08f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label, fontWeight = FontWeight.SemiBold) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = accentColor.copy(alpha = 0.2f),
                selectedLabelColor     = accentColor,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                selectedBorderColor = accentColor,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text  = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ── High priority snooze/reschedule limit settings ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighPrioritySettings(
    maxSnoozeCount: Int,
    maxRescheduleCount: Int,
    onSnoozeChange: (Int) -> Unit,
    onRescheduleChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                HighColor.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp),
            )
            .background(HighColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "High priority limits",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = HighColor,
        )
        CountDropdown(
            label = "Max snoozes",
            value = maxSnoozeCount,
            options = (1..5).toList(),
            onChange = onSnoozeChange,
        )
        CountDropdown(
            label = "Max reschedules",
            value = maxRescheduleCount,
            options = (1..5).toList(),
            onChange = onRescheduleChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountDropdown(
    label: String,
    value: Int,
    options: List<Int>,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.width(100.dp),
        ) {
            OutlinedTextField(
                value = value.toString(),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.toString()) },
                        onClick = { onChange(opt); expanded = false },
                    )
                }
            }
        }
    }
}

// ── Checklist editor ──────────────────────────────────────────────────────────

@Composable
private fun ChecklistEditor(
    items: List<ChecklistItem>,
    onToggle: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    var newItemText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    IconButton(
                        onClick = { if (index > 0) onMove(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(
                        onClick = { if (index < items.lastIndex) onMove(index, index + 1) },
                        enabled = index < items.lastIndex,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = if (index < items.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { onToggle(index) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                placeholder = { Text("New item") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = { onAdd(newItemText); newItemText = "" },
                enabled = newItemText.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy \u00b7 HH:mm", Locale.ENGLISH)

private fun formatDateTime(millis: Long): String =
    LocalDateTime
        .ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        .format(dateFormatter)

private fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Long {
    val ldt = LocalDateTime
        .ofInstant(Instant.ofEpochMilli(dateMillis), ZoneId.systemDefault())
        .withHour(hour)
        .withMinute(minute)
        .withSecond(0)
        .withNano(0)
    return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
