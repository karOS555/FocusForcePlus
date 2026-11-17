package com.focusforceplus.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.model.checklistFromJson
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.components.CompletionRing
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.EmptyState
import com.focusforceplus.app.ui.components.GroupLabel
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.SearchField
import com.focusforceplus.app.ui.components.TabItem
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.UnderlineTabs
import com.focusforceplus.app.ui.components.rowPosition
import com.focusforceplus.app.ui.guards.GuardCopy
import com.focusforceplus.app.ui.guards.GuardFlow
import com.focusforceplus.app.ui.theme.FfDimens
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onCreateTodo: () -> Unit,
    onEditTodo: (Long) -> Unit,
    viewModel: TodoListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = TodoTab.entries[tabIndex]
    val snackbar = remember { SnackbarHostState() }

    var guardFor by remember { mutableStateOf<TodoEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<TodoEntity?>(null) }
    var purgeTarget by remember { mutableStateOf<TodoEntity?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<TodoEntity?>(null) }

    LaunchedEffect(state.justTrashed) {
        val trashed = state.justTrashed ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "\"${trashed.title}\" moved to the trash",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoTrash() else viewModel.clearUndo()
    }

    fun onRingTapped(todo: TodoEntity) {
        when {
            todo.isCompleted -> viewModel.reopen(todo)
            viewModel.doneGuardFor(todo).isNone -> viewModel.complete(todo)
            else -> guardFor = todo
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == TodoTab.OPEN) {
                FloatingActionButton(
                    onClick = onCreateTodo,
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    shape = MaterialTheme.shapes.large,
                ) { Icon(Icons.Filled.Add, contentDescription = "New todo") }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            UnderlineTabs(
                tabs = listOf(
                    TabItem("Open", state.openCount),
                    TabItem("Done", state.doneCount),
                    TabItem("Trash", state.trashed.size),
                ),
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it },
            )
            Spacer(Modifier.height(12.dp))
            SearchField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.padding(horizontal = FfDimens.screenPadding),
                trailing = {
                    FilterMenu(
                        sortOrder = state.sortOrder,
                        filterPriority = state.filterPriority,
                        showSort = tab == TodoTab.OPEN,
                        onSort = viewModel::setSortOrder,
                        onFilter = viewModel::setFilterPriority,
                    )
                },
            )

            when (tab) {
                TodoTab.OPEN -> OpenList(
                    state = state,
                    onEdit = onEditTodo,
                    onRing = ::onRingTapped,
                    onLongPress = { menuFor = it },
                    onCreate = onCreateTodo,
                )
                TodoTab.DONE -> DoneList(
                    state = state,
                    onEdit = onEditTodo,
                    onRing = ::onRingTapped,
                    onLongPress = { menuFor = it },
                )
                TodoTab.TRASH -> TrashList(
                    state = state,
                    onRestore = viewModel::restore,
                    onPurge = { purgeTarget = it },
                    onEmpty = { confirmEmptyTrash = true },
                )
            }
        }
    }

    // ── Long-press menu ───────────────────────────────────────────────────────
    menuFor?.let { todo ->
        ModalBottomSheet(
            onDismissRequest = { menuFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceVariant,
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    maxLines = 2,
                )
                ListRow(title = "Edit", onClick = { menuFor = null; onEditTodo(todo.id) })
                ListRow(
                    title = if (todo.isCompleted) "Mark as not done" else "Mark as done",
                    onClick = { menuFor = null; onRingTapped(todo) },
                )
                ListRow(
                    title = "Delete",
                    titleColor = colors.error,
                    onClick = { menuFor = null; deleteTarget = todo },
                )
            }
        }
    }

    // ── Guarded "done" ────────────────────────────────────────────────────────
    guardFor?.let { todo ->
        ModalBottomSheet(
            onDismissRequest = { guardFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            // The path slider is dragged in every direction; the sheet must not
            // take that as a swipe to close.
            sheetGesturesEnabled = false,
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp),
            ) {
                Text(todo.title, style = MaterialTheme.typography.headlineSmall, color = colors.text)
                Spacer(Modifier.height(16.dp))
                GuardFlow(
                    guard = viewModel.doneGuardFor(todo),
                    copy = GuardCopy.DONE,
                    onPassed = {
                        viewModel.complete(todo)
                        guardFor = null
                    },
                    onCancel = { guardFor = null },
                )
            }
        }
    }

    deleteTarget?.let { todo ->
        DeleteDialog(
            what = "\"${todo.title}\"",
            retentionDays = state.retentionDays,
            onConfirm = {
                viewModel.moveToTrash(todo)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    purgeTarget?.let { todo ->
        DeleteDialog(
            what = "\"${todo.title}\" for good",
            goesToTrash = false,
            onConfirm = {
                viewModel.deleteForever(todo)
                purgeTarget = null
            },
            onDismiss = { purgeTarget = null },
        )
    }

    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = "Empty the trash?",
            message = "${state.trashed.size} todo(s) will be removed for good. This cannot be undone.",
            confirmLabel = "Empty trash",
            destructive = true,
            onConfirm = {
                viewModel.emptyTrash()
                confirmEmptyTrash = false
            },
            onDismiss = { confirmEmptyTrash = false },
        )
    }
}

// ── Lists ─────────────────────────────────────────────────────────────────────

private val ListPadding = PaddingValues(
    start = FfDimens.screenPadding,
    end = FfDimens.screenPadding,
    top = 8.dp,
    bottom = 96.dp,
)

@Composable
private fun OpenList(
    state: TodoListUiState,
    onEdit: (Long) -> Unit,
    onRing: (TodoEntity) -> Unit,
    onLongPress: (TodoEntity) -> Unit,
    onCreate: () -> Unit,
) {
    val colors = FfTheme.colors
    if (state.openGroups.isEmpty()) {
        if (state.loading) return
        EmptyState(
            icon = Icons.Filled.TaskAlt,
            title = if (state.hasFilters) "Nothing matches" else "Nothing open",
            text = if (state.hasFilters) "Try another search or filter." else "Add what you want to get done.",
            actionLabel = if (state.hasFilters) null else "New todo",
            onAction = if (state.hasFilters) null else onCreate,
        )
        return
    }
    val now = System.currentTimeMillis()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListPadding) {
        state.openGroups.forEachIndexed { groupIndex, group ->
            if (group.label != null) {
                item(key = "label_${group.label}") {
                    if (groupIndex > 0) Spacer(Modifier.height(8.dp))
                    GroupLabel(group.label, color = if (group.overdue) colors.error else colors.textMuted)
                }
            }
            itemsIndexed(group.todos, key = { _, todo -> todo.id }) { index, todo ->
                val overdue = todo.dueDateTime != null && todo.dueDateTime < now
                ListRow(
                    title = todo.title,
                    subtitle = openDetail(todo, now),
                    subtitleColor = if (overdue) colors.error else colors.textMuted,
                    position = rowPosition(index, group.todos.size),
                    onClick = { onEdit(todo.id) },
                    onLongClick = { onLongPress(todo) },
                    modifier = Modifier.animateItem(),
                    leading = { TodoIcon(todo) },
                    trailing = {
                        CompletionRing(
                            checked = false,
                            onClick = { onRing(todo) },
                            color = colors.priority(todo.priority),
                            contentDescription = "Mark ${todo.title} as done",
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DoneList(
    state: TodoListUiState,
    onEdit: (Long) -> Unit,
    onRing: (TodoEntity) -> Unit,
    onLongPress: (TodoEntity) -> Unit,
) {
    if (state.done.isEmpty()) {
        if (state.loading) return
        EmptyState(
            icon = Icons.Filled.CheckCircle,
            title = if (state.hasFilters) "Nothing matches" else "Nothing done yet",
            text = if (state.hasFilters) null else "Finished todos are kept here.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListPadding) {
        itemsIndexed(state.done, key = { _, todo -> todo.id }) { index, todo ->
            ListRow(
                title = todo.title,
                subtitle = todo.completedAt?.let { "Done ${TimeFormat.dayAndClock(it)}" },
                struck = true,
                position = rowPosition(index, state.done.size),
                onClick = { onEdit(todo.id) },
                onLongClick = { onLongPress(todo) },
                modifier = Modifier.animateItem(),
                leading = { TodoIcon(todo) },
                trailing = {
                    CompletionRing(
                        checked = true,
                        onClick = { onRing(todo) },
                        contentDescription = "Mark ${todo.title} as not done",
                    )
                },
            )
        }
    }
}

@Composable
private fun TrashList(
    state: TodoListUiState,
    onRestore: (TodoEntity) -> Unit,
    onPurge: (TodoEntity) -> Unit,
    onEmpty: () -> Unit,
) {
    val colors = FfTheme.colors
    if (state.trashed.isEmpty()) {
        if (state.loading) return
        EmptyState(
            icon = Icons.Filled.DeleteOutline,
            title = "The trash is empty",
            text = "Deleted todos wait here for ${state.retentionDays} days before they are gone.",
        )
        return
    }
    val retentionMillis = state.retentionDays * 24L * 60L * 60L * 1000L
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListPadding) {
        item(key = "trash_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Kept for ${state.retentionDays} days, then removed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f).padding(start = 2.dp),
                )
                TextAction("Empty trash", onClick = onEmpty, color = colors.error)
            }
        }
        itemsIndexed(state.trashed, key = { _, todo -> todo.id }) { index, todo ->
            val goneIn = todo.deletedAt?.let { TimeFormat.daysUntil(it + retentionMillis) }
            ListRow(
                title = todo.title,
                subtitle = when (goneIn) {
                    null -> null
                    0 -> "Removed within a day"
                    1 -> "Removed in 1 day"
                    else -> "Removed in $goneIn days"
                },
                position = rowPosition(index, state.trashed.size),
                onLongClick = { onPurge(todo) },
                onClick = { onRestore(todo) },
                modifier = Modifier.animateItem(),
                leading = { TodoIcon(todo) },
                trailing = { TextAction("Restore", onClick = { onRestore(todo) }) },
            )
        }
        item(key = "trash_hint") {
            Text(
                "Hold a todo to delete it for good.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
                modifier = Modifier.padding(start = 2.dp, top = 10.dp),
            )
        }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun TodoIcon(todo: TodoEntity) {
    IconCircle(icon = RoutineIcons.find(todo.iconKey)?.vector ?: Icons.Filled.TaskAlt)
}

/** The one line under an open todo: when, how often, how far. */
private fun openDetail(todo: TodoEntity, now: Long): String? {
    val parts = mutableListOf<String>()
    val snoozedUntil = todo.snoozedUntil?.takeIf { it > now }
    when {
        snoozedUntil != null -> parts += "Snoozed until ${TimeFormat.clock(snoozedUntil)}"
        todo.dueDateTime != null -> parts += TimeFormat.dayAndClock(todo.dueDateTime)
    }
    if (todo.isRecurring) {
        parts += when {
            todo.recurringPattern == "DAILY" -> "repeats daily"
            todo.recurringPattern == "MONTHLY" -> "repeats monthly"
            todo.recurringPattern?.startsWith("WEEKLY_") == true -> "repeats weekly"
            else -> "repeats"
        }
    }
    val checklist = checklistFromJson(todo.checklistJson)
    if (checklist.isNotEmpty()) parts += "${checklist.count { it.done }} of ${checklist.size} checked"
    return parts.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun FilterMenu(
    sortOrder: SortOrder,
    filterPriority: Int?,
    showSort: Boolean,
    onSort: (SortOrder) -> Unit,
    onFilter: (Int?) -> Unit,
) {
    val colors = FfTheme.colors
    var open by remember { mutableStateOf(false) }
    val active = filterPriority != null || sortOrder != SortOrder.DEFAULT
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.Transparent)
                .then(Modifier),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.IconButton(onClick = { open = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort and filter",
                    tint = if (active) colors.accent else colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.surfaceVariant,
        ) {
            MenuHeading("Show")
            listOf(null to "All priorities", 2 to "High", 1 to "Medium", 0 to "Low").forEach { (value, label) ->
                MenuChoice(label, selected = filterPriority == value) {
                    onFilter(value)
                    open = false
                }
            }
            if (showSort) {
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 4.dp))
                MenuHeading("Sort")
                SortOrder.entries.forEach { order ->
                    MenuChoice(order.label, selected = sortOrder == order) {
                        onSort(order)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = FfTheme.colors.textMuted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun MenuChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = FfTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) colors.accent else colors.text,
            )
        },
        onClick = onClick,
    )
}
