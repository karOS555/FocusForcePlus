package com.focusforceplus.app.ui.screens.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.data.db.entity.ListSuggestionEntity
import com.focusforceplus.app.data.repository.ListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListDetailUiState(
    val loading: Boolean = true,
    /** null once the list is gone (deleted elsewhere). */
    val list: ListEntity? = null,
    val open: List<ListItemEntity> = emptyList(),
    val ticked: List<ListItemEntity> = emptyList(),
    /** What this list held before and does not hold open right now. */
    val suggestions: List<ListSuggestionEntity> = emptyList(),
)

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    private val repository: ListRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val listId: Long = savedStateHandle.get<Long>("listId") ?: 0L

    val uiState: StateFlow<ListDetailUiState> = combine(
        repository.observeList(listId),
        repository.getItems(listId),
        repository.getSuggestions(listId, limit = 60),
    ) { list, items, suggestions ->
        val (ticked, open) = items.partition { it.isChecked }
        val openKeys = open.map { it.text.lowercase() }.toSet()
        ListDetailUiState(
            loading = false,
            list = list,
            open = open,
            ticked = ticked.sortedByDescending { it.checkedAt ?: 0L },
            suggestions = suggestions.filter { it.key !in openKeys },
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

    fun update(item: ListItemEntity, text: String, note: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.updateItem(item.copy(text = trimmed, note = note.trim().ifBlank { null }))
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

    fun rename(name: String, iconKey: String?) {
        val list = uiState.value.list ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.updateList(list.copy(name = trimmed, iconKey = iconKey)) }
    }

    fun moveToTrash(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.moveToTrash(listId)
            onDone()
        }
    }
}
