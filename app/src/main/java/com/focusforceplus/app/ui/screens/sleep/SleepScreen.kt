package com.focusforceplus.app.ui.screens.sleep

import com.focusforceplus.app.ui.theme.ffBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.focusforceplus.app.compliance.DAY_CODES
import com.focusforceplus.app.ui.components.Choice
import com.focusforceplus.app.ui.components.ChoiceDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FfTimePickerDialog
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.TimeFormat

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private const val NEW_ROUTINE = -1L

/**
 * Sleep: when the night starts and ends, what happens in between, and how you wake
 * up. Changes are a draft until saved, because weakening a running invincible
 * night is checked against its lock when saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepScreen(
    onNavigateBack: () -> Unit,
    onCreateRoutine: (linkTo: String) -> Unit,
    topLevel: Boolean = false,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    val draft = s.draft
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var timeFor by remember { mutableStateOf<String?>(null) } // "bedtime" | "wake"
    var routineFor by remember { mutableStateOf<String?>(null) } // "bedtime" | "morning"

    LaunchedEffect(s.message) {
        s.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val changed = !s.isLoading && draft != s.saved

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (s.windowActiveNow) {
                    FfPaddedCard(color = colors.container) {
                        Text(
                            if (s.saved.invincibleMode) {
                                "The night is running and locked by invincible mode until wake-up."
                            } else {
                                "The night is running."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onContainer,
                        )
                    }
                }

                FfCard {
                    SwitchRow(
                        title = "Sleep mode",
                        subtitle = "A wind-down reminder, a quiet night, and your wake-up.",
                        checked = draft.enabled,
                        onCheckedChange = { on -> viewModel.updateDraft { it.copy(enabled = on) } },
                    )
                }

                FormSection(title = "When") {
                    FfCard {
                        ValueRow(
                            title = "Bedtime",
                            value = TimeFormat.clock(draft.bedtimeMinutes),
                            onClick = { timeFor = "bedtime" },
                        )
                        RowDivider()
                        ValueRow(
                            title = "Wake-up",
                            value = TimeFormat.clock(draft.wakeMinutes),
                            onClick = { timeFor = "wake" },
                        )
                        RowDivider()
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text("Nights", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            val selected = draft.activeDaysCsv?.split(',')?.filter { it.isNotBlank() }?.toSet()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DAY_CODES.forEachIndexed { i, day ->
                                    val on = selected == null || day in selected
                                    FfChip(
                                        label = DAY_LABELS[i],
                                        selected = on,
                                        onClick = {
                                            val current = selected ?: DAY_CODES.toSet()
                                            val next = if (day in current) current - day else current + day
                                            viewModel.updateDraft {
                                                it.copy(
                                                    activeDaysCsv = if (next.size == DAY_CODES.size || next.isEmpty()) null
                                                    else DAY_CODES.filter { d -> d in next }.joinToString(","),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        RowDivider()
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(
                                "Wind-down reminder before bedtime",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 h")
                                    .forEach { (minutes, label) ->
                                        FfChip(
                                            label = label,
                                            selected = draft.windDownMinutes == minutes,
                                            onClick = { viewModel.updateDraft { it.copy(windDownMinutes = minutes) } },
                                        )
                                    }
                            }
                        }
                    }
                }

                FormSection(
                    title = "Routines",
                    hint = "A linked routine starts with its full alarm at bedtime or at wake-up.",
                ) {
                    FfCard {
                        ValueRow(
                            title = "At bedtime",
                            value = s.routineName(s.saved.bedtimeRoutineId) ?: "None",
                            onClick = { routineFor = "bedtime" },
                        )
                        RowDivider()
                        ValueRow(
                            title = "In the morning",
                            value = s.routineName(s.saved.morningRoutineId) ?: "None",
                            onClick = { routineFor = "morning" },
                        )
                    }
                }

                FormSection(
                    title = "During the night",
                    hint = when {
                        s.groupNames.isEmpty() -> "Blocks you create in the blocker can be switched on for the night here."
                        draft.blockedGroups.isNotEmpty() && !s.blockerMasterEnabled ->
                            "The blocker is paused, so these blocks will not apply until it is on."
                        else -> null
                    },
                ) {
                    FfCard {
                        if (s.groupNames.isNotEmpty()) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text("Block", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                                Spacer(Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    s.groupNames.forEach { name ->
                                        val on = name in draft.blockedGroups
                                        FfChip(
                                            label = name,
                                            selected = on,
                                            onClick = {
                                                val next = if (on) draft.blockedGroups - name else draft.blockedGroups + name
                                                viewModel.updateDraft {
                                                    it.copy(blockedGroupsCsv = next.joinToString("|").ifBlank { null })
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            RowDivider()
                        }
                        SwitchRow(
                            title = "Do Not Disturb",
                            subtitle = if (s.dndAccessGranted) "Silences notifications until wake-up." else
                                "Needs Do Not Disturb access (Settings > Permissions).",
                            checked = draft.dndEnabled,
                            onCheckedChange = { on -> viewModel.updateDraft { it.copy(dndEnabled = on) } },
                        )
                    }
                }

                FormSection(
                    title = "Backup alarms",
                    hint = "Set each night in your phone's clock app, so they ring even if " +
                        "FocusForce+ is not running. The clock app is never blocked.",
                ) {
                    FfCard {
                        SwitchRow(
                            title = "Alarms in the clock app",
                            checked = draft.nativeAlarmCount > 0,
                            onCheckedChange = { on ->
                                viewModel.updateDraft { it.copy(nativeAlarmCount = if (on) 2 else 0) }
                            },
                        )
                        if (draft.nativeAlarmCount > 0) {
                            RowDivider()
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text("How many", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                                Spacer(Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 2, 3).forEach { count ->
                                        FfChip(
                                            label = "$count",
                                            selected = draft.nativeAlarmCount == count,
                                            onClick = { viewModel.updateDraft { it.copy(nativeAlarmCount = count) } },
                                        )
                                    }
                                }
                                if (draft.nativeAlarmCount > 1) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("Time between them", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                                    Spacer(Modifier.height(6.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(5, 10, 15).forEach { gap ->
                                            FfChip(
                                                label = "$gap min",
                                                selected = draft.nativeAlarmGapMinutes == gap,
                                                onClick = { viewModel.updateDraft { it.copy(nativeAlarmGapMinutes = gap) } },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                FormSection(title = "Lock") {
                    FfCard {
                        SwitchRow(
                            title = "Invincible mode",
                            subtitle = "While the night runs, the schedule cannot be weakened or switched off.",
                            checked = draft.invincibleMode,
                            onCheckedChange = { on -> viewModel.updateDraft { it.copy(invincibleMode = on) } },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Saving only appears once there is something to save.
            if (changed) {
                Column(
                    Modifier
                        .ffBar()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    PrimaryButton(text = "Save changes", onClick = viewModel::save, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (topLevel) {
        Scaffold(
            containerColor = colors.screen,
            snackbarHost = { SnackbarHost(snackbar) },
            content = body,
        )
    } else {
        PageScaffold(title = "Sleep", onBack = onNavigateBack, snackbarHostState = snackbar, content = body)
    }

    timeFor?.let { target ->
        val initial = if (target == "bedtime") draft.bedtimeMinutes else draft.wakeMinutes
        FfTimePickerDialog(
            title = if (target == "bedtime") "Bedtime" else "Wake-up",
            hour = initial / 60,
            minute = initial % 60,
            onConfirm = { h, m ->
                val minutes = h * 60 + m
                viewModel.updateDraft {
                    if (target == "bedtime") it.copy(bedtimeMinutes = minutes) else it.copy(wakeMinutes = minutes)
                }
                timeFor = null
            },
            onDismiss = { timeFor = null },
        )
    }

    routineFor?.let { target ->
        val linked = if (target == "bedtime") s.saved.bedtimeRoutineId else s.saved.morningRoutineId
        ChoiceDialog(
            title = if (target == "bedtime") "Routine at bedtime" else "Routine in the morning",
            choices = listOf(Choice(0L, "None")) +
                s.routines.map { Choice(it.id, it.name) } +
                Choice(NEW_ROUTINE, "Create a new routine"),
            selected = linked,
            onSelect = { id ->
                routineFor = null
                when (id) {
                    NEW_ROUTINE -> onCreateRoutine(target)
                    else -> if (target == "bedtime") viewModel.setBedtimeRoutine(id) else viewModel.setMorningRoutine(id)
                }
            },
            onDismiss = { routineFor = null },
        )
    }
}
