package com.focusforceplus.app.ui.screens.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.ui.common.IconPickerButton
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.EmptyState
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.theme.FfTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onOpenList: (Long) -> Unit = {},
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ListEntity?>(null) }
    var renaming by remember { mutableStateOf<ListEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ListEntity?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.lists.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { creating = true },
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    shape = MaterialTheme.shapes.large,
                ) { Icon(Icons.Filled.Add, contentDescription = "New list") }
            }
        },
    ) { padding ->
        if (!state.loading && state.lists.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                title = "No lists yet",
                text = "For things you buy or pack again and again. Ticked items stay on the " +
                    "list, so next time it is a few taps instead of typing it all again.",
                actionLabel = "Create a list",
                onAction = { creating = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "lists") {
                FfCard {
                    state.lists.forEachIndexed { index, row ->
                        if (index > 0) RowDivider()
                        ListRow(
                            title = row.list.name,
                            subtitle = when {
                                row.total == 0 -> "Empty"
                                row.open == 0 -> "All ${row.total} ticked"
                                else -> "${row.open} of ${row.total} to go"
                            },
                            onClick = { onOpenList(row.list.id) },
                            onLongClick = { menuFor = row.list },
                            leading = {
                                IconCircle(
                                    icon = RoutineIcons.find(row.list.iconKey)?.vector
                                        ?: Icons.AutoMirrored.Filled.FormatListBulleted,
                                )
                            },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.textFaint,
                                )
                            },
                        )
                    }
                }
            }
            item(key = "hint") {
                Text(
                    "Press and hold a list to rename or delete it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                )
            }
        }
    }

    if (creating) {
        ListNameDialog(
            title = "New list",
            confirmLabel = "Create",
            showTemplates = true,
            onConfirm = { name, icon ->
                creating = false
                viewModel.create(name, icon, onOpenList)
            },
            onDismiss = { creating = false },
        )
    }

    renaming?.let { list ->
        ListNameDialog(
            title = "Rename list",
            confirmLabel = "Save",
            initialName = list.name,
            initialIcon = list.iconKey,
            onConfirm = { name, icon ->
                renaming = null
                viewModel.rename(list, name, icon)
            },
            onDismiss = { renaming = null },
        )
    }

    menuFor?.let { list ->
        ModalBottomSheet(
            onDismissRequest = { menuFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceVariant,
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    list.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                ListRow(title = "Open", onClick = { menuFor = null; onOpenList(list.id) })
                ListRow(title = "Rename", onClick = { menuFor = null; renaming = list })
                ListRow(title = "Delete", titleColor = colors.error, onClick = { menuFor = null; deleteTarget = list })
            }
        }
    }

    deleteTarget?.let { list ->
        DeleteDialog(
            what = "\"${list.name}\"",
            onConfirm = {
                viewModel.moveToTrash(list)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** Name and icon of a list, with a few ready-made ones when creating. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListNameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, iconKey: String?) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialIcon: String? = null,
    showTemplates: Boolean = false,
) {
    val colors = FfTheme.colors
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconPickerButton(selectedKey = icon, size = 52.dp, onSelect = { icon = it })
                    Spacer(Modifier.padding(start = 10.dp))
                    FfTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Name",
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showTemplates) {
                    Spacer(Modifier.height(14.dp))
                    Text("Or start from", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LIST_TEMPLATES.forEach { template ->
                            FfChip(
                                label = template.name,
                                selected = name == template.name,
                                onClick = {
                                    name = template.name
                                    icon = template.iconKey
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, icon) }, enabled = name.isNotBlank()) {
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (name.isNotBlank()) colors.accent else colors.textFaint,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}
