package com.focusforceplus.app.ui.screens.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.data.db.entity.ListSuggestionEntity
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.ListCatalog
import com.focusforceplus.app.data.repository.ListRepository
import com.focusforceplus.app.data.repository.ListText
import com.focusforceplus.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Items under one section; [name] null = items without a section. */
data class SectionGroup(val name: String?, val items: List<ListItemEntity>)

data class ListDetailUiState(
    val loading: Boolean = true,
    /** null once the list is gone (deleted elsewhere). */
    val list: ListEntity? = null,
    /**
     * A shopping list: what is still to buy, by aisle. A checklist: everything, by
     * section, ticked or not, because there a ticked item keeps its place.
     */
    val groups: List<SectionGroup> = emptyList(),
    /** A shopping list: what is in the basket, the last one first. Empty for a checklist. */
    val basket: List<ListItemEntity> = emptyList(),
    val total: Int = 0,
    val ticked: Int = 0,
    /** What this list held before and does not hold open right now, most used first. */
    val buyAgain: List<ListSuggestionEntity> = emptyList(),
    /** Sections in their order, for moving an item and for reordering. */
    val sections: List<String> = emptyList(),
    /** Open todos that remind about this list. */
    val reminders: List<TodoEntity> = emptyList(),
) {
    val isChecklist: Boolean get() = list?.isChecklist == true
    val open: Int get() = total - ticked
}

/** Something that was just done and can be taken back, said in a few words. */
data class ListUndo(val message: String, val id: Long)

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
        repository.getSuggestions(listId, limit = 80),
        todoRepository.getOpenForList(listId),
    ) { list, items, suggestions, reminders ->
        val checklist = list?.isChecklist == true
        val (tickedItems, openItems) = items.partition { it.isChecked }
        // A checklist keeps ticked items where they are; a shopping list moves them to the basket.
        val shown = if (checklist) items.sortedWith(compareBy({ it.sortOrder }, { it.createdAt })) else openItems
        // Sections in the list's order; any a list does not know yet go after them.
        val order = (list?.sections.orEmpty() + items.mapNotNull { it.section }).distinct()
        val groups = shown.groupBy { it.section }
            .map { (name, groupItems) -> SectionGroup(name, groupItems) }
            .sortedBy { g -> if (g.name == null) Int.MAX_VALUE else order.indexOf(g.name) }
        val onList = openItems.map { it.text.lowercase() }.toSet()
        ListDetailUiState(
            loading = false,
            list = list,
            groups = groups,
            basket = if (checklist) emptyList() else tickedItems.sortedByDescending { it.checkedAt ?: 0L },
            total = items.size,
            ticked = tickedItems.size,
            buyAgain = suggestions.filter { it.key !in onList },
            sections = order,
            reminders = reminders,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState())

    // ── Adding ────────────────────────────────────────────────────────────────

    /**
     * What to offer while [query] is being typed: first what this list held
     * before, then common things from the catalogue (a shopping list only), each
     * once, and nothing that is on the list already.
     */
    fun matches(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val state = uiState.value
        val own = state.buyAgain.filter { it.text.contains(q, ignoreCase = true) }.map { it.text }
        val onList = state.groups.flatMap { it.items }.filter { !it.isChecked }.map { it.text.lowercase() }.toSet()
        val known = if (state.isChecklist) emptyList() else ListCatalog.suggest(q, limit = 8)
        return (own + known).distinctBy { it.lowercase() }.filter { it.lowercase() !in onList }.take(8)
    }

    /** "Milk, eggs, bread", or a pasted list with one item per line, adds them all. */
    fun add(text: String) {
        val parts = ListText.splitItems(text)
        viewModelScope.launch { parts.forEach { repository.addItem(listId, it) } }
    }

    // ── Items ─────────────────────────────────────────────────────────────────

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
                note = note.trim().take(200).ifBlank { null },
            )
            repository.updateItem(updated)
            if (section != item.section) repository.setSection(updated, section)
        }
    }

    fun delete(item: ListItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item.id)
            offerUndo("Removed \"${item.text}\"") { repository.restoreItems(listOf(item)) }
        }
    }

    // ── Whole list ────────────────────────────────────────────────────────────

    /** Shopping is done: the basket is emptied, and what was in it stays under "Buy again". */
    fun emptyBasket() {
        viewModelScope.launch {
            val bought = repository.emptyBasket(listId)
            if (bought.isNotEmpty()) {
                val what = if (bought.size == 1) "1 item" else "${bought.size} items"
                offerUndo("$what taken off the list") { repository.restoreItems(bought) }
            }
        }
    }

    /** A checklist starts over: everything is unticked. */
    fun startOver() {
        viewModelScope.launch {
            val ticked = repository.startOver(listId)
            if (ticked.isNotEmpty()) offerUndo("Everything is unticked") { repository.tickAgain(ticked) }
        }
    }

    fun forget(suggestion: ListSuggestionEntity) {
        viewModelScope.launch { repository.forgetSuggestion(suggestion.id) }
    }

    fun setSectionOrder(order: List<String>) {
        viewModelScope.launch { repository.setSectionOrder(listId, order) }
    }

    fun removeSection(section: String) {
        viewModelScope.launch { repository.renameSection(listId, section, null) }
    }

    fun rename(name: String, iconKey: String?) {
        val list = uiState.value.list ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.updateList(list.copy(name = trimmed.take(60), iconKey = iconKey)) }
    }

    fun setKind(kind: String) {
        viewModelScope.launch { repository.setKind(listId, kind) }
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

    // ── Undo ──────────────────────────────────────────────────────────────────

    private val _undo = MutableStateFlow<ListUndo?>(null)

    /** The last thing that can be taken back; the screen offers it for a few seconds. */
    val undo: StateFlow<ListUndo?> = _undo.asStateFlow()

    private var undoAction: (suspend () -> Unit)? = null
    private var undoCounter = 0L

    private fun offerUndo(message: String, action: suspend () -> Unit) {
        undoAction = action
        _undo.value = ListUndo(message, ++undoCounter)
    }

    /** Takes back what [offer] stands for, unless something newer has replaced it. */
    fun undo(offer: ListUndo) {
        if (_undo.value?.id != offer.id) return
        val action = undoAction ?: return
        undoAction = null
        _undo.value = null
        viewModelScope.launch { action() }
    }

    fun undoShown(offer: ListUndo) {
        if (_undo.value?.id == offer.id) _undo.value = null
    }
}
