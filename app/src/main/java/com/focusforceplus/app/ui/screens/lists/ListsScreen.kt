package com.focusforceplus.app.ui.screens.lists

import com.focusforceplus.app.ui.components.FfFab
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.focusforceplus.app.data.db.entity.ListKind
import com.focusforceplus.app.ui.common.IconPickerButton
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.EmptyState
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PrimaryButton
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
        containerColor = colors.screen,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.lists.isNotEmpty()) {
                FfFab(onClick = { creating = true }, contentDescription = "New list")
            }
        },
    ) { padding ->
        if (!state.loading && state.lists.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                title = "No lists yet",
                text = "Two kinds. A shopping list sorts what you add by aisle and remembers it for " +
                    "next time. A checklist is for what you pack or check again and again.",
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
                            subtitle = row.summary,
                            onClick = { onOpenList(row.list.id) },
                            onLongClick = { menuFor = row.list },
                            leading = {
                                IconCircle(
                                    icon = RoutineIcons.find(row.list.iconKey)?.vector
                                        ?: if (row.list.isChecklist) Icons.AutoMirrored.Filled.FormatListBulleted
                                        else Icons.Filled.ShoppingCart,
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
        NewListSheet(
            onCreate = { name, icon, kind, template ->
                creating = false
                viewModel.create(name, icon, kind, template, onOpenList)
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
                ListRow(title = "Duplicate", onClick = { menuFor = null; viewModel.duplicate(list, onOpenList) })
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

/**
 * A new list: first what it is for, because that decides how it behaves, then a
 * name, with a few ready-made ones to start from.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewListSheet(
    onCreate: (name: String, iconKey: String?, kind: String, template: ListTemplate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var kind by remember { mutableStateOf(ListKind.SHOPPING) }
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf<String?>(null) }
    var template by remember { mutableStateOf<ListTemplate?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New list", style = MaterialTheme.typography.titleLarge, color = colors.text)
            KindPicker(
                kind = kind,
                onPick = {
                    kind = it
                    // A template of the other kind no longer fits.
                    if (template?.kind != it) template = null
                },
            )
            Column {
                Text("Start from", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LIST_TEMPLATES.filter { it.kind == kind }.forEach { option ->
                        FfChip(
                            label = option.name,
                            selected = template == option,
                            onClick = {
                                template = option
                                name = option.name
                                icon = option.iconKey
                            },
                        )
                    }
                }
                template?.takeIf { it.items.isNotEmpty() }?.let { chosen ->
                    val count = chosen.items.sumOf { it.second.size }
                    Text(
                        "Comes with $count items you can change.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPickerButton(selectedKey = icon, size = 52.dp, onSelect = { icon = it })
                Spacer(Modifier.padding(start = 10.dp))
                FfTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = "Name",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                text = "Create",
                onClick = { onCreate(name, icon, kind, template) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Name and icon of a list. */
@Composable
internal fun ListNameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, iconKey: String?) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialIcon: String? = null,
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
