package com.focusforceplus.app.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListKind
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
) {
    /** What is left, in the words of the list's kind. */
    val summary: String
        get() = when {
            total == 0 -> "Empty"
            list.isChecklist && open == 0 -> "All $total ticked"
            list.isChecklist -> "${total - open} of $total ticked"
            open == 0 -> "Everything is in the basket"
            open == 1 -> "1 item to buy"
            else -> "$open items to buy"
        }
}

data class ListsUiState(
    val loading: Boolean = true,
    val lists: List<ListRowState> = emptyList(),
)

/**
 * A list to start from: a name, an icon, its kind and, for a checklist, what is
 * usually on it, section by section (a null section name means "no section").
 * A shopping list needs no items: it fills up from what is typed and bought.
 */
data class ListTemplate(
    val name: String,
    val iconKey: String,
    val kind: String,
    val items: List<Pair<String?, List<String>>> = emptyList(),
)

val LIST_TEMPLATES = listOf(
    ListTemplate("Groceries", "local_grocery_store", ListKind.SHOPPING),
    ListTemplate("Drugstore", "local_pharmacy", ListKind.SHOPPING),
    ListTemplate("Hardware store", "handyman", ListKind.SHOPPING),
    ListTemplate(
        "Packing for a trip", "luggage", ListKind.CHECKLIST,
        listOf(
            "Papers and money" to listOf("ID or passport", "Tickets", "Wallet", "Keys"),
            "Clothes" to listOf("Underwear", "Socks", "T-shirts", "Trousers", "Jumper", "Jacket", "Sleepwear", "Shoes"),
            "Bathroom" to listOf("Toothbrush", "Toothpaste", "Deodorant", "Shampoo", "Medication"),
            "Tech" to listOf("Phone charger", "Power bank", "Headphones"),
        ),
    ),
    ListTemplate(
        "Leaving the house", "home", ListKind.CHECKLIST,
        listOf(null to listOf("Keys", "Wallet", "Phone", "Windows closed", "Stove off", "Lights off", "Door locked")),
    ),
    ListTemplate(
        "Gym bag", "fitness_center", ListKind.CHECKLIST,
        listOf(null to listOf("Sports clothes", "Shoes", "Towel", "Water bottle", "Shower gel", "Padlock", "Headphones")),
    ),
    ListTemplate(
        "Weekly clean", "cleaning_services", ListKind.CHECKLIST,
        listOf(
            "Kitchen" to listOf("Wipe the surfaces", "Clean the hob", "Empty the bin"),
            "Bathroom" to listOf("Toilet", "Sink", "Shower"),
            "Everywhere" to listOf("Vacuum", "Mop", "Dust", "Change the bed sheets"),
        ),
    ),
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

    fun create(name: String, iconKey: String?, kind: String, template: ListTemplate?, onCreated: (Long) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // A template's items only fit the kind it was made for.
            val starter = template?.takeIf { it.kind == kind }?.items.orEmpty()
            onCreated(repository.createList(trimmed, iconKey, kind, starter))
        }
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
