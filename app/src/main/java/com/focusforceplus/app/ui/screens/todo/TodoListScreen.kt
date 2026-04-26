package com.focusforceplus.app.ui.screens.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.model.checklistFromJson
import com.focusforceplus.app.ui.theme.Warning
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onCreateTodo: () -> Unit,
    onEditTodo: (Long) -> Unit,
    viewModel: TodoListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show undo snackbar whenever a todo is deleted
    LaunchedEffect(uiState.deletedTodo) {
        val deleted = uiState.deletedTodo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "\"${deleted.title}\" deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete(deleted)
        } else {
            viewModel.clearDeletedTodo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTodo,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New todo")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search todos…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            // ── Filter chips + sort icon ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val priorityChips = listOf(
                        null to "All",
                        0 to "Low",
                        1 to "Medium",
                        2 to "High",
                    )
                    items(priorityChips) { (value, label) ->
                        val selected = uiState.filterPriority == value
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setFilterPriority(if (selected) null else value) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }

                var sortMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(
                            Icons.Filled.Sort,
                            contentDescription = "Sort",
                            tint = if (uiState.sortOrder != SortOrder.DEFAULT)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = order.label,
                                        color = if (uiState.sortOrder == order)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (uiState.sortOrder == order)
                                            FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    sortMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Open (${uiState.openTodos.size})") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Done (${uiState.completedTodos.size})") },
                )
            }

            val todos = if (selectedTab == 0) uiState.openTodos else uiState.completedTodos
            val now = System.currentTimeMillis()

            if (todos.isEmpty()) {
                EmptyTodosHint(
                    isCompletedTab = selectedTab == 1,
                    hasFilters = uiState.searchQuery.isNotEmpty() || uiState.filterPriority != null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    items(todos, key = { it.id }) { todo ->
                        SwipeToDeleteTodoItem(
                            todo = todo,
                            isOverdue = !todo.isCompleted
                                && todo.dueDateTime != null
                                && todo.dueDateTime < now,
                            onEdit = { onEditTodo(todo.id) },
                            onToggleComplete = { viewModel.toggleComplete(todo) },
                            onDelete = { viewModel.deleteTodo(todo) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

// ─── Swipe-to-delete wrapper ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteTodoItem(
    todo: TodoEntity,
    isOverdue: Boolean,
    onEdit: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = { DeleteBackground() },
    ) {
        TodoCard(
            todo = todo,
            isOverdue = isOverdue,
            onEdit = onEdit,
            onToggleComplete = onToggleComplete,
        )
    }
}

@Composable
private fun DeleteBackground() {
    val color by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.errorContainer,
        label = "swipe_bg",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(end = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

// ─── Todo card ────────────────────────────────────────────────────────────────

@Composable
private fun TodoCard(
    todo: TodoEntity,
    isOverdue: Boolean,
    onEdit: () -> Unit,
    onToggleComplete: () -> Unit,
) {
    val borderModifier = if (isOverdue) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = todo.isCompleted,
                onCheckedChange = { onToggleComplete() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (todo.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough
                                     else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (todo.dueDateTime != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = formatDueDate(todo.dueDateTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (todo.isRecurring && todo.recurringPattern != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = formatRecurring(todo.recurringPattern),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (!todo.checklistJson.isNullOrBlank()) {
                    val items = checklistFromJson(todo.checklistJson)
                    if (items.isNotEmpty()) {
                        val done = items.count { it.done }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "\u2713 $done / ${items.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done == items.size) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            PriorityBadge(priority = todo.priority)
        }
    }
}

@Composable
private fun PriorityBadge(priority: Int) {
    val (label, color) = when (priority) {
        0 -> "Low" to MaterialTheme.colorScheme.onSurfaceVariant
        2 -> "High" to MaterialTheme.colorScheme.error
        else -> "Medium" to Warning
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.18f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyTodosHint(
    isCompletedTab: Boolean,
    hasFilters: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when {
                    hasFilters -> "No matching todos"
                    isCompletedTab -> "No completed todos"
                    else -> "No open todos"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isCompletedTab && !hasFilters) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tap + to add your first todo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy \u00b7 HH:mm", Locale.ENGLISH)

private fun formatDueDate(millis: Long): String =
    LocalDateTime
        .ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        .format(dateFormatter)

private fun formatRecurring(pattern: String): String = when {
    pattern == "DAILY" -> "Repeats daily"
    pattern == "MONTHLY" -> "Repeats monthly"
    pattern.startsWith("WEEKLY_") ->
        "Repeats weekly \u00b7 ${pattern.removePrefix("WEEKLY_")}"
    else -> ""
}
