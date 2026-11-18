package com.focusforceplus.app.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.repository.ListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListRowState(
    val list: ListEntity,
    val open: Int,
    val total: Int,
)

data class ListsUiState(
    val loading: Boolean = true,
    val lists: List<ListRowState> = emptyList(),
)

/** A ready name and icon for the lists people make most. */
data class ListTemplate(val name: String, val iconKey: String)

val LIST_TEMPLATES = listOf(
    ListTemplate("Groceries", "local_grocery_store"),
    ListTemplate("Drugstore", "local_pharmacy"),
    ListTemplate("Packing", "luggage"),
    ListTemplate("Hardware store", "handyman"),
    ListTemplate("Gifts", "card_giftcard"),
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val repository: ListRepository,
) : ViewModel() {

    val uiState: StateFlow<ListsUiState> = combine(repository.getLists(), repository.getCounts()) { lists, counts ->
        val byList = counts.associateBy { it.listId }
        ListsUiState(
            loading = false,
            lists = lists.map { list ->
                val c = byList[list.id]
                ListRowState(list, open = c?.openCount ?: 0, total = c?.totalCount ?: 0)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() = _message.update { null }

    fun create(name: String, iconKey: String?, onCreated: (Long) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { onCreated(repository.createList(trimmed, iconKey)) }
    }

    fun rename(list: ListEntity, name: String, iconKey: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.updateList(list.copy(name = trimmed, iconKey = iconKey)) }
    }

    fun duplicate(list: ListEntity, onDone: (Long) -> Unit) {
        viewModelScope.launch { repository.duplicateList(list.id)?.let(onDone) }
    }

    fun moveToTrash(list: ListEntity) {
        viewModelScope.launch {
            repository.moveToTrash(list.id)
            _message.value = "\"${list.name}\" moved to the trash"
        }
    }
}
