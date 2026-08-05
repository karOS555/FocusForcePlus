package com.focusforceplus.app.ui.screens.blocker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.compliance.DAY_CODES
import com.focusforceplus.app.ui.common.routineDayLabel
import com.focusforceplus.app.util.GroupTemplates
import com.focusforceplus.app.util.InstalledApp
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState

/** Everything the group editor collects — grew past a readable parameter list. */
data class GroupEditorResult(
    val name: String,
    val limitMinutes: Int?,
    val invincible: Boolean,
    val windowStart: Int?,
    val windowEnd: Int?,
    val windowDaysCsv: String?,
    val blockDuringRoutines: Boolean?,
    val blockDuringFocus: Boolean?,
    val members: Set<String>,
    val memberDisplayNames: Map<String, String>,
)

/**
 * Create/edit a blocker group: name, optional shared daily limit, blocking
 * schedule, session overrides, invincible, template quick-fill, and per-app
 * membership checkboxes. Read-only fields when the group is currently locked
 * (the VM still guards saves as defense in depth).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupEditorDialog(
    existing: BlockerGroupRow?,
    installedApps: List<InstalledApp>,
    currentMemberPackages: Set<String>,
    onSave: (GroupEditorResult) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val locked = existing?.locked == true
    var name by remember { mutableStateOf(existing?.group?.name ?: "") }
    var limitEnabled by remember { mutableStateOf(existing?.group?.sharedDailyLimitMinutes != null) }
    var limitMinutes by remember { mutableStateOf(existing?.group?.sharedDailyLimitMinutes ?: 120) }
    var invincible by remember { mutableStateOf(existing?.group?.invincibleMode ?: false) }
    var scheduleEnabled by remember {
        mutableStateOf(existing?.group?.windowStartMinutes != null && existing.group.windowEndMinutes != null)
    }
    var windowStart by remember { mutableStateOf(existing?.group?.windowStartMinutes ?: 9 * 60) }
    var windowEnd by remember { mutableStateOf(existing?.group?.windowEndMinutes ?: 17 * 60) }
    val selectedDays = remember {
        val fromCsv = existing?.group?.windowDaysCsv
            ?.split(',')?.filter { it.isNotBlank() }?.toSet()
        mutableStateMapOf<String, Boolean>().apply {
            DAY_CODES.forEach { put(it, fromCsv == null || it in fromCsv) }
        }
    }
    var timePickerTarget by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var duringRoutines by remember { mutableStateOf(existing?.group?.blockDuringRoutines) }
    var duringFocus by remember { mutableStateOf(existing?.group?.blockDuringFocus) }
    var search by remember { mutableStateOf("") }

    // Membership as a live map: package -> selected.
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(currentMemberPackages) {
        currentMemberPackages.forEach { selected[it] = true }
    }

    val displayNames = remember(installedApps) { installedApps.associate { it.packageName to it.appName } }

    val filtered = remember(search, installedApps) {
        if (search.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(search, ignoreCase = true) ||
                it.packageName.contains(search, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New group" else "Edit \"${existing.group.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (locked) {
                    Text(
                        "Locked by Invincible Mode until the shared limit resets at midnight. " +
                            "You can still tighten it; loosening needs your Tamper Protection window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Templates (only meaningful when creating).
                if (existing == null) {
                    Text(
                        "Quick fill from a template:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GroupTemplates.ALL.forEach { template ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    if (name.isBlank()) name = template.name
                                    GroupTemplates.matchInstalled(template, installedApps)
                                        .forEach { selected[it.packageName] = true }
                                },
                                label = { Text(template.name) },
                            )
                        }
                    }
                }

                // Shared limit.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Shared daily limit",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "One combined budget for all apps in the group",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = limitEnabled,
                        onCheckedChange = { limitEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                if (limitEnabled) {
                    Text(
                        "${limitMinutes / 60}h ${limitMinutes % 60}m per day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Slider(
                        value = limitMinutes.toFloat(),
                        onValueChange = { limitMinutes = (it.toInt() / 5) * 5 },
                        valueRange = 15f..480f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Blocking schedule.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Blocking schedule",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Block every app in the group during a daily time window. " +
                                "Overnight windows like 22:00-06:00 work too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = scheduleEnabled,
                        onCheckedChange = { scheduleEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                if (scheduleEnabled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { timePickerTarget = "start" }) {
                            Text(formatWindowMinutes(windowStart))
                        }
                        Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { timePickerTarget = "end" }) {
                            Text(formatWindowMinutes(windowEnd))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DAY_CODES.forEach { day ->
                            FilterChip(
                                selected = selectedDays[day] == true,
                                onClick = { selectedDays[day] = !(selectedDays[day] ?: false) },
                                label = { Text(routineDayLabel(day)) },
                            )
                        }
                    }
                }

                // Session behaviour for every member app — set once here instead of
                // per app, and apps added later inherit it.
                Text(
                    "While a routine runs",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                SessionOverrideChips(
                    value = duringRoutines,
                    onChange = { duringRoutines = it },
                )
                Text(
                    "While a focus session runs",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                SessionOverrideChips(
                    value = duringFocus,
                    onChange = { duringFocus = it },
                )

                // Invincible needs a bounded condition (limit or schedule) so the
                // lock always has a natural end — see PLAY_STORE_COMPLIANCE 2.1.
                val invincibleAvailable = limitEnabled || scheduleEnabled
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Invincible Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (invincibleAvailable) MaterialTheme.colorScheme.onBackground
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (invincibleAvailable) {
                                "While the shared limit is hit or the schedule is running, " +
                                    "the group cannot be weakened or turned off"
                            } else {
                                "Add a shared limit or a schedule first — Invincible Mode " +
                                    "needs an end point it can unlock at"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = invincible && invincibleAvailable,
                        enabled = invincibleAvailable,
                        onCheckedChange = { invincible = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }

                // Member selection.
                Text(
                    "Apps in this group (${selected.count { it.value }})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected[app.packageName] = !(selected[app.packageName] ?: false) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected[app.packageName] ?: false,
                                onCheckedChange = { selected[app.packageName] = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Text(
                                app.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete group", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val members = selected.filterValues { it }.keys.toSet()
                val days = selectedDays.filterValues { it }.keys
                val daysCsv = if (days.isEmpty() || days.size == DAY_CODES.size) null
                              else DAY_CODES.filter { it in days }.joinToString(",")
                onSave(
                    GroupEditorResult(
                        name = name,
                        limitMinutes = if (limitEnabled) limitMinutes else null,
                        invincible = invincible && (limitEnabled || scheduleEnabled),
                        windowStart = if (scheduleEnabled) windowStart else null,
                        windowEnd = if (scheduleEnabled) windowEnd else null,
                        windowDaysCsv = if (scheduleEnabled) daysCsv else null,
                        blockDuringRoutines = duringRoutines,
                        blockDuringFocus = duringFocus,
                        members = members,
                        memberDisplayNames = displayNames,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    timePickerTarget?.let { target ->
        val initial = if (target == "start") windowStart else windowEnd
        val timeState = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            title = { Text(if (target == "start") "Window start" else "Window end") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val minutes = timeState.hour * 60 + timeState.minute
                    if (target == "start") windowStart = minutes else windowEnd = minutes
                    timePickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerTarget = null }) { Text("Cancel") }
            },
        )
    }
}

private fun formatWindowMinutes(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

/**
 * Tri-state chooser for a group-level session override. "Per app" (null) keeps
 * each member's own flag, the other two force the behaviour for the whole group.
 */
@Composable
private fun SessionOverrideChips(
    value: Boolean?,
    onChange: (Boolean?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf<Pair<Boolean?, String>>(
            null to "Per app",
            true to "Block",
            false to "Allow",
        ).forEach { (option, label) ->
            FilterChip(
                selected = value == option,
                onClick = { onChange(option) },
                label = { Text(label) },
            )
        }
    }
}
