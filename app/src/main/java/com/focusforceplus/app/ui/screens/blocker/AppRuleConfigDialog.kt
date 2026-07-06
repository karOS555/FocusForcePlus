package com.focusforceplus.app.ui.screens.blocker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.compliance.isRuleLocked
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import java.util.Calendar

/**
 * Per-app rule editor. When the rule is invincible-locked (ACTIVE state) all fields
 * are read-only and a lock banner explains when it releases — Golden Rule #11.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppRuleConfigDialog(
    row: BlockerAppRow,
    existingGroups: List<String>,
    onSave: (BlockedAppEntity) -> Unit,
    onDelete: (BlockedAppEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val base = row.rule ?: BlockedAppEntity(
        packageName = row.packageName,
        appName = row.appName,
        isBlocked = true,
    )
    val minutesOfDay = remember {
        val cal = Calendar.getInstance()
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
    val locked = remember(base) { isRuleLocked(base, minutesOfDay) }

    var enabled by remember { mutableStateOf(base.isBlocked) }
    var limitEnabled by remember { mutableStateOf(base.dailyLimitMinutes != null) }
    var limitMinutes by remember { mutableStateOf(base.dailyLimitMinutes ?: 30) }
    var windowEnabled by remember { mutableStateOf(base.windowStartMinutes != null && base.windowEndMinutes != null) }
    var windowStart by remember { mutableStateOf(base.windowStartMinutes ?: 9 * 60) }
    var windowEnd by remember { mutableStateOf(base.windowEndMinutes ?: 17 * 60) }
    var invincible by remember { mutableStateOf(base.invincibleMode) }
    var duringRoutines by remember { mutableStateOf(base.blockDuringRoutines) }
    var duringFocus by remember { mutableStateOf(base.blockDuringFocus) }
    var groupName by remember { mutableStateOf(base.groupName ?: "") }

    var timePickerTarget by remember { mutableStateOf<String?>(null) } // "start" | "end"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.appName, maxLines = 1) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (locked) {
                    Text(
                        "Locked by Invincible Mode while active. Unlocks at its natural " +
                            "end (midnight for daily limits, the window end for time windows).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                ConfigToggleRow(
                    title = "Block this app",
                    checked = enabled,
                    enabled = !locked,
                    onChange = { enabled = it },
                )

                // ── Daily limit ────────────────────────────────────────────────
                ConfigToggleRow(
                    title = "Daily time limit",
                    subtitle = "Allowed until the limit is used up, then blocked until midnight",
                    checked = limitEnabled,
                    enabled = !locked,
                    onChange = { limitEnabled = it },
                )
                if (limitEnabled) {
                    Column {
                        Text(
                            "$limitMinutes minutes per day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Slider(
                            value = limitMinutes.toFloat(),
                            onValueChange = { limitMinutes = it.toInt().coerceIn(5, 180) },
                            valueRange = 5f..180f,
                            steps = 34,
                            enabled = !locked,
                        )
                    }
                }

                // ── Time window ────────────────────────────────────────────────
                ConfigToggleRow(
                    title = "Blocking window",
                    subtitle = "Blocked during a fixed daily time range (max 12 hours). " +
                        "Overnight windows like 22:00-06:00 work too.",
                    checked = windowEnabled,
                    enabled = !locked,
                    onChange = { windowEnabled = it },
                )
                if (windowEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { timePickerTarget = "start" },
                            enabled = !locked,
                        ) { Text(formatMinutes(windowStart)) }
                        Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(
                            onClick = { timePickerTarget = "end" },
                            enabled = !locked,
                        ) { Text(formatMinutes(windowEnd)) }
                    }
                }

                // ── Sessions ──────────────────────────────────────────────────
                ConfigToggleRow(
                    title = "Block during routines",
                    checked = duringRoutines,
                    enabled = !locked,
                    onChange = { duringRoutines = it },
                )
                ConfigToggleRow(
                    title = "Block during focus sessions",
                    checked = duringFocus,
                    enabled = !locked,
                    onChange = { duringFocus = it },
                )

                // ── Invincible ────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Invincible Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "While the limit is hit or the window is running, this rule " +
                                "cannot be weakened or turned off. Needs a limit or window " +
                                "— an always-on block never locks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = invincible,
                        onCheckedChange = { invincible = it },
                        enabled = !locked && (limitEnabled || windowEnabled),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }

                // ── Group ─────────────────────────────────────────────────────
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group (optional)") },
                    placeholder = { Text("e.g. Social Media") },
                    supportingText = if (existingGroups.isNotEmpty()) {
                        { Text("Existing: ${existingGroups.joinToString(", ")}") }
                    } else null,
                    singleLine = true,
                    enabled = !locked,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (row.rule != null) {
                    TextButton(
                        onClick = { onDelete(base) },
                        enabled = !locked,
                    ) {
                        Text("Remove rule", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        base.copy(
                            isBlocked = enabled,
                            dailyLimitMinutes = if (limitEnabled) limitMinutes else null,
                            windowStartMinutes = if (windowEnabled) windowStart else null,
                            windowEndMinutes = if (windowEnabled) windowEnd else null,
                            // Invincible without a bounded condition would be an endless
                            // lock — silently meaningless, so persist it only when bounded.
                            invincibleMode = invincible && (limitEnabled || windowEnabled),
                            blockDuringRoutines = duringRoutines,
                            blockDuringFocus = duringFocus,
                            groupName = groupName.trim().ifBlank { null },
                        )
                    )
                },
                enabled = !locked,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (locked) "Close" else "Cancel") }
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
                TextButton(onClick = {
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

@Composable
private fun ConfigToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
