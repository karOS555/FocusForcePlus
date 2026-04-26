package com.focusforceplus.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.ui.theme.Error
import com.focusforceplus.app.ui.theme.Warning
import com.focusforceplus.app.util.TodoAlarmHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var editingSlot by remember { mutableIntStateOf(-1) }
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── General ───────────────────────────────────────────────────────
            item {
                SettingsSection(title = "General") {
                    PlaceholderRow("App theme")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    PlaceholderRow("Language")
                }
            }

            // ── Routines ──────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Routines") {
                    PlaceholderRow("Default snooze duration")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    PlaceholderRow("Pre-alarm lead time")
                }
            }

            // ── Todos ─────────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Todos") {
                    // Daily reminders
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Daily reminders",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Notify you about open and overdue todos at these times.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))

                        uiState.digestTimes.forEachIndexed { index, (hour, minute) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.AccessTime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "%02d:%02d".format(hour, minute),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { editingSlot = index; showTimePicker = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Text("Edit", style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.weight(1f))
                                if (uiState.digestTimes.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.removeDigestTime(index) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.digestTimes.size < TodoAlarmHelper.MAX_DIGEST_SLOTS) {
                            TextButton(
                                onClick = {
                                    editingSlot = TodoAlarmHelper.MAX_DIGEST_SLOTS
                                    showTimePicker = true
                                },
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add reminder time")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Default priority
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Default priority for new todos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Low", 1 to "Medium", 2 to "High").forEach { (value, label) ->
                                val accentColor = when (value) {
                                    0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                    2 -> Error
                                    else -> Warning
                                }
                                FilterChip(
                                    selected = uiState.defaultPriority == value,
                                    onClick = { viewModel.setDefaultPriority(value) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                        selectedLabelColor = accentColor,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = uiState.defaultPriority == value,
                                        selectedBorderColor = accentColor,
                                    ),
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Auto-delete completed
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Auto-delete completed todos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Automatically remove completed todos after a set time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Never", 7 to "7 days", 30 to "30 days", 90 to "90 days")
                                .forEach { (days, label) ->
                                    FilterChip(
                                        selected = uiState.autoDeleteDays == days,
                                        onClick = { viewModel.setAutoDeleteDays(days) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    )
                                }
                        }
                    }
                }
            }

            // ── App Blocker ───────────────────────────────────────────────────
            item {
                SettingsSection(title = "App Blocker") {
                    PlaceholderRow("Block schedule")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    PlaceholderRow("Whitelist apps")
                }
            }

            // ── Focus Mode ────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Focus Mode") {
                    PlaceholderRow("Default session duration")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    PlaceholderRow("Break reminders")
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ── Time picker dialog ─────────────────────────────────────────────────────
    // key(editingSlot) forces rememberTimePickerState to re-initialize with the
    // correct hour/minute whenever a different slot is opened.
    if (showTimePicker) {
        val isAdding = editingSlot !in uiState.digestTimes.indices
        val initialHour   = if (!isAdding) uiState.digestTimes[editingSlot].first   else 9
        val initialMinute = if (!isAdding) uiState.digestTimes[editingSlot].second  else 0
        key(editingSlot) {
            val timePickerState = rememberTimePickerState(
                initialHour   = initialHour,
                initialMinute = initialMinute,
                is24Hour      = true,
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false; editingSlot = -1 },
                title = { Text(if (isAdding) "Add reminder time" else "Edit reminder time") },
                text = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        TimePicker(state = timePickerState)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (isAdding) {
                            viewModel.addDigestTime(timePickerState.hour, timePickerState.minute)
                        } else {
                            viewModel.updateDigestTime(editingSlot, timePickerState.hour, timePickerState.minute)
                        }
                        showTimePicker = false
                        editingSlot = -1
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false; editingSlot = -1 }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) { content() }
    }
}

@Composable
private fun PlaceholderRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Coming soon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}
