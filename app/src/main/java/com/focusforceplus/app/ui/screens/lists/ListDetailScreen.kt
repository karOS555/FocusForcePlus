package com.focusforceplus.app.ui.screens.lists

import com.focusforceplus.app.util.TimeFormat
import com.focusforceplus.app.ui.components.IconCircle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.ui.components.CompletionRing
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.GroupLabel
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.theme.FfTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
    onRemind: (listId: Long, title: String) -> Unit = { _, _ -> },
    onEditTodo: (Long) -> Unit = {},
    onOpenList: (Long) -> Unit = {},
    viewModel: ListDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    val context = LocalContext.current
    var sortingSections by remember { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var shopping by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ListItemEntity?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    // The list was deleted elsewhere: nothing to show.
    LaunchedEffect(state.loading, state.list) {
        if (!state.loading && state.list == null) onBack()
    }

    // Shopping mode keeps the display on while the phone is in your hand.
    val view = LocalView.current
    DisposableEffect(shopping) {
        view.keepScreenOn = shopping
        onDispose { view.keepScreenOn = false }
    }

    fun submit() {
        if (input.isNotBlank()) {
            viewModel.add(input)
            input = ""
        }
    }

    PageScaffold(
        title = state.list?.name.orEmpty(),
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = colors.textSecondary)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = colors.surfaceVariant,
                ) {
                    DropdownMenuItem(
                        text = { Text(if (shopping) "Stop shopping mode" else "Shopping mode") },
                        onClick = { menuOpen = false; shopping = !shopping },
                    )
                    DropdownMenuItem(
                        text = { Text("Remind me") },
                        onClick = {
                            menuOpen = false
                            state.list?.let { onRemind(it.id, it.name) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Share list") },
                        enabled = state.openCount > 0,
                        onClick = {
                            menuOpen = false
                            viewModel.shareText { text ->
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                        "Share list",
                                    )
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Order of sections") },
                        enabled = state.sections.size > 1,
                        onClick = { menuOpen = false; sortingSections = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Untick everything") },
                        enabled = state.ticked.isNotEmpty(),
                        onClick = { menuOpen = false; viewModel.uncheckAll() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove ticked items") },
                        enabled = state.ticked.isNotEmpty(),
                        onClick = { menuOpen = false; confirmClear = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { menuOpen = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { menuOpen = false; viewModel.duplicate(onOpenList) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete list", color = colors.error) },
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // ── Add ───────────────────────────────────────────────────────────
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FfTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = "Add an item (or several, with commas)",
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { submit() }, enabled = input.isNotBlank()) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add",
                            tint = if (input.isNotBlank()) colors.accent else colors.textFaint,
                        )
                    }
                }
                // Earlier items, matching what is being typed, or the most used ones.
                val query = input.trim()
                val shown = state.suggestions
                    .filter { query.isEmpty() || it.text.contains(query, ignoreCase = true) }
                    .take(if (query.isEmpty()) 12 else 6)
                if (shown.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        shown.forEach { suggestion ->
                            FfChip(
                                label = suggestion.text,
                                selected = false,
                                onClick = {
                                    viewModel.add(suggestion.text)
                                    input = ""
                                },
                            )
                        }
                    }
                }
            }

            // ── Items ─────────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.reminders.isNotEmpty()) {
                    item(key = "reminders") {
                        FfCard {
                            state.reminders.forEachIndexed { index, todo ->
                                if (index > 0) RowDivider()
                                ListRow(
                                    title = todo.title,
                                    subtitle = todo.dueDateTime?.let { "Reminder " + TimeFormat.dayAndClock(it) }
                                        ?: "Reminder, no date",
                                    onClick = { onEditTodo(todo.id) },
                                    leading = { IconCircle(icon = Icons.Filled.Alarm) },
                                )
                            }
                        }
                    }
                }
                if (state.openCount == 0 && state.ticked.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        Text(
                            "Nothing on it yet. Type above; what you add is remembered for next time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
                        )
                    }
                }
                val showHeaders = state.groups.size > 1 || state.groups.any { it.name != null }
                state.groups.forEach { group ->
                    if (showHeaders) {
                        item(key = "section-${group.name}") {
                            GroupLabel(
                                group.name ?: "Other",
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    item(key = "items-${group.name}") {
                        FfCard {
                            group.items.forEachIndexed { index, item ->
                                if (index > 0) RowDivider()
                                ItemRow(item, shopping, onToggle = { viewModel.toggle(item) }, onEdit = { editing = item })
                            }
                        }
                    }
                }
                if (state.ticked.isNotEmpty()) {
                    item(key = "ticked-label") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GroupLabel("Ticked · ${state.ticked.size}", modifier = Modifier.weight(1f))
                            TextAction("Untick all", onClick = viewModel::uncheckAll)
                        }
                    }
                    item(key = "ticked") {
                        FfCard {
                            state.ticked.forEachIndexed { index, item ->
                                if (index > 0) RowDivider()
                                ItemRow(item, shopping, onToggle = { viewModel.toggle(item) }, onEdit = { editing = item })
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        EditItemDialog(
            item = item,
            sections = state.sections,
            onSave = { text, quantity, note, section ->
                viewModel.update(item, text, quantity, note, section)
                editing = null
            },
            onDelete = {
                viewModel.delete(item)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (renaming) {
        val list = state.list
        if (list != null) {
            ListNameDialog(
                title = "Rename list",
                confirmLabel = "Save",
                initialName = list.name,
                initialIcon = list.iconKey,
                onConfirm = { name, icon ->
                    renaming = false
                    viewModel.rename(name, icon)
                },
                onDismiss = { renaming = false },
            )
        }
    }

    if (sortingSections) {
        SectionOrderDialog(
            sections = state.sections,
            onMove = viewModel::moveSection,
            onRemove = viewModel::removeSection,
            onDismiss = { sortingSections = false },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Remove ticked items?",
            message = "They leave the list. You can still add them again from the suggestions.",
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.clearTicked()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }

    if (confirmDelete) {
        DeleteDialog(
            what = "\"${state.list?.name.orEmpty()}\"",
            onConfirm = {
                confirmDelete = false
                viewModel.moveToTrash(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ItemRow(
    item: ListItemEntity,
    large: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = FfTheme.colors
    ListRow(
        title = item.text,
        subtitle = item.note,
        struck = item.isChecked,
        titleColor = if (item.isChecked) colors.textMuted else colors.text,
        onClick = onToggle,
        onLongClick = onEdit,
        modifier = if (large) Modifier.padding(vertical = 6.dp) else Modifier,
        leading = {
            CompletionRing(
                checked = item.isChecked,
                onClick = onToggle,
                color = colors.textFaint,
                contentDescription = item.text,
            )
        },
        trailing = item.quantity?.let { amount ->
            {
                Text(
                    amount,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (item.isChecked) colors.textFaint else colors.accent,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditItemDialog(
    item: ListItemEntity,
    sections: List<String>,
    onSave: (text: String, quantity: String, note: String, section: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var text by remember { mutableStateOf(item.text) }
    var quantity by remember { mutableStateOf(item.quantity.orEmpty()) }
    var note by remember { mutableStateOf(item.note.orEmpty()) }
    var section by remember { mutableStateOf(item.section) }
    var newSection by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text("Edit item", style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FfTextField(value = text, onValueChange = { text = it }, label = "Item")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FfTextField(
                        value = quantity,
                        onValueChange = { quantity = it.take(20) },
                        label = "Amount",
                        modifier = Modifier.weight(0.4f),
                    )
                    FfTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = "Note",
                        modifier = Modifier.weight(0.6f),
                    )
                }
                Text("Section", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FfChip(label = "None", selected = section == null && newSection.isBlank(), onClick = { section = null; newSection = "" })
                    sections.forEach { name ->
                        FfChip(label = name, selected = section == name && newSection.isBlank(), onClick = { section = name; newSection = "" })
                    }
                }
                FfTextField(
                    value = newSection,
                    onValueChange = { newSection = it.take(40) },
                    placeholder = "New section, like \"Produce\"",
                )
                Text(
                    "Next time this item is added, it goes into the same section.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                TextAction("Delete this item", onClick = onDelete, color = colors.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text, quantity, note, newSection.trim().ifBlank { section }) },
                enabled = text.isNotBlank(),
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

/** The order of sections, e.g. the way through your shop. */
@Composable
private fun SectionOrderDialog(
    sections: List<String>,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text("Order of sections", style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Put them in the order you walk through the shop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                sections.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.text, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onMove(name, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move $name up", tint = colors.textSecondary)
                        }
                        IconButton(onClick = { onMove(name, 1) }, enabled = index < sections.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move $name down", tint = colors.textSecondary)
                        }
                        IconButton(onClick = { onRemove(name) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove section $name", tint = colors.textMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
    )
}
