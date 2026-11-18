package com.focusforceplus.app.ui.screens.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.data.db.entity.ListSuggestionEntity
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.ListRepository
import com.focusforceplus.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Open items under one section; [name] null = items without a section. */
data class SectionGroup(val name: String?, val items: List<ListItemEntity>)

data class ListDetailUiState(
    val loading: Boolean = true,
    /** null once the list is gone (deleted elsewhere). */
    val list: ListEntity? = null,
    val groups: List<SectionGroup> = emptyList(),
    val ticked: List<ListItemEntity> = emptyList(),
    /** What this list held before and does not hold open right now. */
    val suggestions: List<ListSuggestionEntity> = emptyList(),
    /** Sections in their order, for moving an item and for reordering. */
    val sections: List<String> = emptyList(),
    /** Open todos that remind about this list. */
    val reminders: List<TodoEntity> = emptyList(),
) {
    val openCount: Int get() = groups.sumOf { it.items.size }
}

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    private val repository: ListRepository,
    todoRepository: TodoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val listId: Long = savedStateHandle.get<Long>("listId") ?: 0L

    val uiState: StateFlow<ListDetailUiState> = combine(
        repository.observeList(listId),
        repository.getItems(listId),
        repository.getSuggestions(listId, limit = 60),
        todoRepository.getOpenForList(listId),
    ) { list, items, suggestions, reminders ->
        val (ticked, open) = items.partition { it.isChecked }
        val openKeys = open.map { it.text.lowercase() }.toSet()
        // Sections in the list's order; any a list does not know yet go after them.
        val order = (list?.sections.orEmpty() + open.mapNotNull { it.section }).distinct()
        val groups = open.groupBy { it.section }
            .map { (name, groupItems) -> SectionGroup(name, groupItems) }
            .sortedBy { g -> if (g.name == null) Int.MAX_VALUE else order.indexOf(g.name) }
        ListDetailUiState(
            loading = false,
            list = list,
            groups = groups,
            ticked = ticked.sortedByDescending { it.checkedAt ?: 0L },
            suggestions = suggestions.filter { it.key !in openKeys },
            sections = order,
            reminders = reminders,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState())

    fun add(text: String) {
        // "Milk, eggs, bread" adds three at once.
        val parts = text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch { parts.forEach { repository.addItem(listId, it) } }
    }

    fun toggle(item: ListItemEntity) {
        viewModelScope.launch { repository.setChecked(item.id, !item.isChecked) }
    }

    fun update(item: ListItemEntity, text: String, quantity: String, note: String, section: String?) {
        val trimmed = text.trim().take(120)
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val updated = item.copy(
                text = trimmed,
                quantity = quantity.trim().take(20).ifBlank { null },
                note = note.trim().ifBlank { null },
            )
            repository.updateItem(updated)
            if (section != item.section) repository.setSection(updated, section)
        }
    }

    fun delete(item: ListItemEntity) {
        viewModelScope.launch { repository.deleteItem(item.id) }
    }

    fun uncheckAll() {
        viewModelScope.launch { repository.uncheckAll(listId) }
    }

    fun clearTicked() {
        viewModelScope.launch { repository.deleteChecked(listId) }
    }

    fun forget(suggestion: ListSuggestionEntity) {
        viewModelScope.launch { repository.forgetSuggestion(suggestion.id) }
    }

    fun moveSection(section: String, by: Int) {
        viewModelScope.launch { repository.moveSection(listId, section, by) }
    }

    fun removeSection(section: String) {
        viewModelScope.launch { repository.renameSection(listId, section, null) }
    }

    fun rename(name: String, iconKey: String?) {
        val list = uiState.value.list ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.updateList(list.copy(name = trimmed, iconKey = iconKey)) }
    }

    fun duplicate(onDone: (Long) -> Unit) {
        viewModelScope.launch { repository.duplicateList(listId)?.let(onDone) }
    }

    fun shareText(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(repository.shareText(listId)) }
    }

    fun moveToTrash(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.moveToTrash(listId)
            onDone()
        }
    }
}
