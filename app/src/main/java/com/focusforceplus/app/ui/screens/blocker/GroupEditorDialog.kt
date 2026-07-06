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
import com.focusforceplus.app.util.GroupTemplates
import com.focusforceplus.app.util.InstalledApp

/**
 * Create/edit a blocker group: name, optional shared daily limit + invincible,
 * template quick-fill, and per-app membership checkboxes. Read-only fields when
 * the group is currently locked (the VM still guards saves as defense in depth).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupEditorDialog(
    existing: BlockerGroupRow?,
    installedApps: List<InstalledApp>,
    currentMemberPackages: Set<String>,
    onSave: (name: String, limit: Int?, invincible: Boolean, members: Set<String>, names: Map<String, String>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val locked = existing?.locked == true
    var name by remember { mutableStateOf(existing?.group?.name ?: "") }
    var limitEnabled by remember { mutableStateOf(existing?.group?.sharedDailyLimitMinutes != null) }
    var limitMinutes by remember { mutableStateOf(existing?.group?.sharedDailyLimitMinutes ?: 120) }
    var invincible by remember { mutableStateOf(existing?.group?.invincibleMode ?: false) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Invincible Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                "Once the shared limit is hit, the group locks until midnight",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = invincible,
                            onCheckedChange = { invincible = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
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
                onSave(
                    name,
                    if (limitEnabled) limitMinutes else null,
                    invincible,
                    members,
                    displayNames,
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
