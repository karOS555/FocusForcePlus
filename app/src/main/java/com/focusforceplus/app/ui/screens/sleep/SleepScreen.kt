package com.focusforceplus.app.ui.screens.sleep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.compliance.DAY_CODES
import com.focusforceplus.app.ui.common.routineDayLabel

/**
 * Sleep mode configuration: bedtime/wake-up schedule, wind-down reminder, night
 * blocking, DND, linked bedtime/morning routines, and native backup wake alarms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    onNavigateBack: () -> Unit,
    onCreateRoutine: (linkTo: String) -> Unit,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    val draft = s.draft
    val snackbarHostState = remember { SnackbarHostState() }

    var timePickerTarget by remember { mutableStateOf<String?>(null) } // "bedtime" | "wake"
    var routinePickerTarget by remember { mutableStateOf<String?>(null) } // "bedtime" | "morning"

    LaunchedEffect(s.message) {
        s.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep mode") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (s.windowActiveNow) {
                Text(
                    if (s.saved.invincibleMode)
                        "Sleep window is running and locked by Invincible Mode until wake-up."
                    else
                        "Sleep window is running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SleepCard {
                ToggleRow(
                    title = "Enable sleep mode",
                    subtitle = "Bedtime reminders, night blocking, and your wake-up flow",
                    checked = draft.enabled,
                    onChange = { on -> viewModel.updateDraft { it.copy(enabled = on) } },
                )
            }

            // ── Schedule ──────────────────────────────────────────────────────
            SleepCard(title = "Schedule") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Bedtime", color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { timePickerTarget = "bedtime" }) {
                        Text(formatMinutes(draft.bedtimeMinutes))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Wake-up", color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { timePickerTarget = "wake" }) {
                        Text(formatMinutes(draft.wakeMinutes))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val selectedDays = draft.activeDaysCsv
                        ?.split(',')?.filter { it.isNotBlank() }?.toSet()
                    DAY_CODES.forEach { day ->
                        val selected = selectedDays == null || day in selectedDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val current = selectedDays ?: DAY_CODES.toSet()
                                val next = if (day in current) current - day else current + day
                                viewModel.updateDraft {
                                    it.copy(
                                        activeDaysCsv = if (next.size == DAY_CODES.size || next.isEmpty()) null
                                        else DAY_CODES.filter { d -> d in next }.joinToString(","),
                                    )
                                }
                            },
                            label = { Text(routineDayLabel(day)) },
                        )
                    }
                }
                Text(
                    "Wind-down reminder before bedtime:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "1h")
                        .forEach { (minutes, label) ->
                            FilterChip(
                                selected = draft.windDownMinutes == minutes,
                                onClick = { viewModel.updateDraft { it.copy(windDownMinutes = minutes) } },
                                label = { Text(label) },
                            )
                        }
                }
            }

            // ── Linked routines ───────────────────────────────────────────────
            SleepCard(title = "Routines") {
                RoutineLinkRow(
                    label = "Bedtime routine",
                    value = s.routineName(s.saved.bedtimeRoutineId) ?: "None",
                    onClick = { routinePickerTarget = "bedtime" },
                )
                RoutineLinkRow(
                    label = "Morning routine",
                    value = s.routineName(s.saved.morningRoutineId) ?: "None",
                    onClick = { routinePickerTarget = "morning" },
                )
                Text(
                    "Linked routines fire with the full routine alarm at bedtime and wake-up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Night blocking + DND ──────────────────────────────────────────
            SleepCard(title = "During the night") {
                Text(
                    "Block these app groups from bedtime until wake-up:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.groupNames.isEmpty()) {
                    Text(
                        "No blocker groups yet — create one in the Blocker tab first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.groupNames.forEach { name ->
                            val selected = name in draft.blockedGroups
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val next = if (selected) draft.blockedGroups - name
                                               else draft.blockedGroups + name
                                    viewModel.updateDraft {
                                        it.copy(blockedGroupsCsv = next.joinToString("|").ifBlank { null })
                                    }
                                },
                                label = { Text(name) },
                            )
                        }
                    }
                }
                if (draft.blockedGroups.isNotEmpty() && !s.blockerMasterEnabled) {
                    Text(
                        "The app blocker is currently off — night blocking needs it enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                ToggleRow(
                    title = "Do Not Disturb",
                    subtitle = if (s.dndAccessGranted)
                        "Silence notifications from bedtime until wake-up"
                    else
                        "Needs Do Not Disturb access (Settings > Permissions)",
                    checked = draft.dndEnabled,
                    onChange = { on -> viewModel.updateDraft { it.copy(dndEnabled = on) } },
                )
            }

            // ── Native backup alarms ──────────────────────────────────────────
            SleepCard(title = "Backup wake-up alarms") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Set alarms in your clock app",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Created each night in Android's own clock app, so they ring " +
                                "even if FocusForce+ isn't running. Your clock app is never blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.nativeAlarmCount > 0,
                        onCheckedChange = { on ->
                            viewModel.updateDraft { it.copy(nativeAlarmCount = if (on) 2 else 0) }
                        },
                    )
                }
                if (draft.nativeAlarmCount > 0) {
                    Text(
                        "Number of alarms (in case you sleep through the first):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { count ->
                            FilterChip(
                                selected = draft.nativeAlarmCount == count,
                                onClick = { viewModel.updateDraft { it.copy(nativeAlarmCount = count) } },
                                label = { Text("$count") },
                            )
                        }
                    }
                    if (draft.nativeAlarmCount > 1) {
                        Text(
                            "Minutes between alarms:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15).forEach { gap ->
                                FilterChip(
                                    selected = draft.nativeAlarmGapMinutes == gap,
                                    onClick = { viewModel.updateDraft { it.copy(nativeAlarmGapMinutes = gap) } },
                                    label = { Text("$gap min") },
                                )
                            }
                        }
                    }
                }
            }

            // ── Invincible ────────────────────────────────────────────────────
            SleepCard {
                ToggleRow(
                    title = "Invincible Mode",
                    subtitle = "While the sleep window runs, the schedule can't be weakened " +
                        "or turned off until wake-up",
                    checked = draft.invincibleMode,
                    onChange = { on -> viewModel.updateDraft { it.copy(invincibleMode = on) } },
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Save sleep schedule", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // ── Time picker dialog ────────────────────────────────────────────────────
    timePickerTarget?.let { target ->
        val initial = if (target == "bedtime") draft.bedtimeMinutes else draft.wakeMinutes
        val timeState = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            title = { Text(if (target == "bedtime") "Bedtime" else "Wake-up time") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val minutes = timeState.hour * 60 + timeState.minute
                    viewModel.updateDraft {
                        if (target == "bedtime") it.copy(bedtimeMinutes = minutes)
                        else it.copy(wakeMinutes = minutes)
                    }
                    timePickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerTarget = null }) { Text("Cancel") }
            },
        )
    }

    // ── Routine picker dialog ─────────────────────────────────────────────────
    routinePickerTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { routinePickerTarget = null },
            title = { Text(if (target == "bedtime") "Bedtime routine" else "Morning routine") },
            text = {
                val linkedId = if (target == "bedtime") s.saved.bedtimeRoutineId
                               else s.saved.morningRoutineId
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RoutineChoiceRow("None", selected = linkedId == 0L) {
                        if (target == "bedtime") viewModel.setBedtimeRoutine(0L)
                        else viewModel.setMorningRoutine(0L)
                        routinePickerTarget = null
                    }
                    s.routines.forEach { routine ->
                        RoutineChoiceRow(routine.name, selected = linkedId == routine.id) {
                            if (target == "bedtime") viewModel.setBedtimeRoutine(routine.id)
                            else viewModel.setMorningRoutine(routine.id)
                            routinePickerTarget = null
                        }
                    }
                    RoutineChoiceRow("+ Create new routine", selected = false) {
                        routinePickerTarget = null
                        onCreateRoutine(target)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { routinePickerTarget = null }) { Text("Cancel") }
            },
        )
    }
}

// ── Building blocks ───────────────────────────────────────────────────────────

@Composable
private fun SleepCard(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RoutineChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RoutineLinkRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun formatMinutes(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)
