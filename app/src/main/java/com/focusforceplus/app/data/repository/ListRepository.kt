package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.ListCounts
import com.focusforceplus.app.data.db.dao.ListDao
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.data.db.entity.ListKind
import com.focusforceplus.app.data.db.entity.ListSuggestionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListRepository @Inject constructor(
    private val listDao: ListDao,
) {
    // ── Lists ─────────────────────────────────────────────────────────────────

    fun getLists(): Flow<List<ListEntity>> = listDao.getLists()

    suspend fun getListsOnce(): List<ListEntity> = listDao.getListsOnce()

    fun observeList(id: Long): Flow<ListEntity?> = listDao.observeList(id)

    suspend fun getListById(id: Long): ListEntity? = listDao.getListById(id)

    fun getCounts(): Flow<List<ListCounts>> = listDao.getCounts()

    /**
     * Makes a list. A shopping list starts with the aisles in the order of a
     * common way through a shop; [starter] fills a checklist from a template,
     * section by section (a null section name means "no section").
     */
    suspend fun createList(
        name: String,
        iconKey: String?,
        kind: String = ListKind.SHOPPING,
        starter: List<Pair<String?, List<String>>> = emptyList(),
    ): Long {
        val now = System.currentTimeMillis()
        val sections = if (kind == ListKind.SHOPPING) ListCatalog.AISLES else starter.mapNotNull { it.first }
        val id = listDao.insertList(
            ListEntity(
                name = name.trim().take(60),
                iconKey = iconKey,
                sortOrder = listDao.maxListSortOrder() + 1,
                createdAt = now,
                sectionOrder = sections.joinToString("\n").ifBlank { null },
                kind = kind,
            )
        )
        var order = 0
        listDao.insertItems(
            starter.flatMap { (section, items) ->
                items.map { text ->
                    ListItemEntity(listId = id, text = text, sortOrder = order++, createdAt = now, section = section)
                }
            }
        )
        return id
    }

    suspend fun updateList(list: ListEntity) = listDao.updateList(list)

    /**
     * Turns a list into the other kind. A list that becomes a shopping list gets
     * the aisles it does not have yet, behind its own sections.
     */
    suspend fun setKind(listId: Long, kind: String) {
        val list = listDao.getListById(listId) ?: return
        if (list.kind == kind) return
        val sections = if (kind == ListKind.SHOPPING) (list.sections + ListCatalog.AISLES).distinct() else list.sections
        listDao.updateList(list.copy(kind = kind, sectionOrder = sections.joinToString("\n").ifBlank { null }))
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    fun getItems(listId: Long): Flow<List<ListItemEntity>> = listDao.getItems(listId)

    suspend fun getItemsOnce(listId: Long): List<ListItemEntity> = listDao.getItemsOnce(listId)

    /**
     * Adds an item and remembers it for the suggestions. An amount in the text
     * ("2 milk", "flour 500 g") becomes the item's quantity. The item goes into the
     * section it was in last time; on a shopping list, something new goes to the
     * aisle it is usually found in ([ListCatalog]). Adding something that is
     * already on the list and ticked simply unticks it, which is what "milk again"
     * means on a shopping list.
     */
    suspend fun addItem(listId: Long, rawText: String, note: String? = null) {
        val parsed = ListText.parse(rawText)
        val text = ListText.tidy(parsed.text.take(120))
        if (text.isEmpty()) return
        val now = System.currentTimeMillis()
        val key = text.lowercase()
        val known = listDao.getSuggestion(listId, key)
        val list = listDao.getListById(listId)
        // What the user decided for this item wins; the catalogue is the first guess.
        val section = known?.section
            ?: if (known == null && list != null && !list.isChecklist) ListCatalog.aisleFor(text) else null
        if (section != null && list != null && section !in list.sections) {
            listDao.updateList(list.copy(sectionOrder = (list.sections + section).joinToString("\n")))
        }
        val existing = listDao.getItemsOnce(listId).firstOrNull { it.text.lowercase() == key }
        if (existing != null) {
            listDao.updateItem(
                existing.copy(
                    isChecked = false,
                    checkedAt = null,
                    quantity = parsed.quantity ?: existing.quantity,
                    note = note?.trim()?.ifBlank { null } ?: existing.note,
                )
            )
        } else {
            listDao.insertItem(
                ListItemEntity(
                    listId = listId,
                    text = text,
                    note = note?.trim()?.ifBlank { null },
                    sortOrder = listDao.maxItemSortOrder(listId) + 1,
                    createdAt = now,
                    section = section,
                    quantity = parsed.quantity,
                )
            )
        }
        listDao.upsertSuggestion(
            known?.copy(text = text, useCount = known.useCount + 1, lastUsedAt = now)
                ?: ListSuggestionEntity(listId = listId, key = key, text = text, lastUsedAt = now, section = section)
        )
    }

    fun observeAllItems(): Flow<List<ListItemEntity>> = listDao.observeAllItems()

    /** Open items only, for a todo's alarm or a routine step. */
    fun getOpenItems(listId: Long): Flow<List<ListItemEntity>> = listDao.getOpenItems(listId)

    // ── Sections ──────────────────────────────────────────────────────────────

    /** Puts an item into a section and remembers that for the next time it is added. */
    suspend fun setSection(item: ListItemEntity, section: String?) {
        val clean = section?.trim()?.take(40)?.ifBlank { null }
        listDao.updateItem(item.copy(section = clean))
        listDao.setSuggestionSection(item.listId, item.text.lowercase(), clean)
        if (clean != null) {
            val list = listDao.getListById(item.listId) ?: return
            if (clean !in list.sections) {
                listDao.updateList(list.copy(sectionOrder = (list.sections + clean).joinToString("\n")))
            }
        }
    }

    /** Puts the sections into a new order, e.g. the way through the shop. */
    suspend fun setSectionOrder(listId: Long, order: List<String>) {
        val list = listDao.getListById(listId) ?: return
        listDao.updateList(list.copy(sectionOrder = order.distinct().joinToString("\n").ifBlank { null }))
    }

    /** Moves a section one place up (-1) or down (+1), e.g. to follow the shop. */
    suspend fun moveSection(listId: Long, section: String, by: Int) {
        val list = listDao.getListById(listId) ?: return
        val order = list.sections.toMutableList()
        val from = order.indexOf(section)
        val to = from + by
        if (from < 0 || to !in order.indices) return
        order.add(to, order.removeAt(from))
        listDao.updateList(list.copy(sectionOrder = order.joinToString("\n")))
    }

    /** Renames a section, or removes it ([to] null): its items then have none. */
    suspend fun renameSection(listId: Long, from: String, to: String?) {
        val clean = to?.trim()?.take(40)?.ifBlank { null }
        val list = listDao.getListById(listId) ?: return
        listDao.renameSection(listId, from, clean)
        listDao.renameSuggestionSection(listId, from, clean)
        val order = list.sections.map { if (it == from) clean else it }.filterNotNull().distinct()
        listDao.updateList(list.copy(sectionOrder = order.joinToString("\n").ifBlank { null }))
    }

    // ── Whole lists ───────────────────────────────────────────────────────────

    /** A copy with all items unticked, e.g. a packing list for the next trip. */
    suspend fun duplicateList(id: Long): Long? {
        val list = listDao.getListById(id) ?: return null
        val now = System.currentTimeMillis()
        val newId = listDao.insertList(
            list.copy(
                id = 0,
                name = "${list.name} (copy)".take(60),
                sortOrder = listDao.maxListSortOrder() + 1,
                createdAt = now,
                deletedAt = null,
            )
        )
        listDao.insertItems(
            listDao.getItemsOnce(id).map {
                it.copy(id = 0, listId = newId, isChecked = false, checkedAt = null, createdAt = now)
            }
        )
        return newId
    }

    /** The open items as plain text, by section, to send to someone. */
    suspend fun shareText(listId: Long): String {
        val list = listDao.getListById(listId) ?: return ""
        val open = listDao.getItemsOnce(listId).filter { !it.isChecked }
        val order = list.sections
        val groups = open.groupBy { it.section }
            .toList()
            .sortedBy { (section, _) -> if (section == null) Int.MAX_VALUE else order.indexOf(section).let { i -> if (i < 0) order.size else i } }
        return buildString {
            append(list.name)
            groups.forEach { (section, items) ->
                append("\n")
                if (section != null && groups.size > 1) append("\n").append(section).append("\n")
                items.forEach { item ->
                    append("\n- ")
                    item.quantity?.let { append(it).append(' ') }
                    append(item.text)
                    item.note?.let { append(" (").append(it).append(')') }
                }
            }
        }.trim()
    }

    suspend fun updateItem(item: ListItemEntity) = listDao.updateItem(item)

    suspend fun setChecked(itemId: Long, checked: Boolean) =
        listDao.setChecked(itemId, checked, if (checked) System.currentTimeMillis() else null)

    suspend fun uncheckAll(listId: Long) = listDao.uncheckAll(listId)

    /** Unticks everything and returns what was ticked, so it can be put back. */
    suspend fun startOver(listId: Long): List<Long> {
        val ticked = listDao.getItemsOnce(listId).filter { it.isChecked }.map { it.id }
        listDao.uncheckAll(listId)
        return ticked
    }

    suspend fun tickAgain(itemIds: List<Long>) {
        if (itemIds.isNotEmpty()) listDao.checkAll(itemIds, System.currentTimeMillis())
    }

    suspend fun deleteItem(itemId: Long) = listDao.deleteItem(itemId)

    suspend fun deleteChecked(listId: Long) = listDao.deleteChecked(listId)

    /**
     * The shopping is done: what is in the basket leaves the list (it stays under
     * "Buy again"). Returns what was removed, so it can be put back.
     */
    suspend fun emptyBasket(listId: Long): List<ListItemEntity> {
        val bought = listDao.getItemsOnce(listId).filter { it.isChecked }
        listDao.deleteChecked(listId)
        return bought
    }

    /** Puts items back as they were (after an undo). */
    suspend fun restoreItems(items: List<ListItemEntity>) {
        if (items.isNotEmpty()) listDao.insertItems(items)
    }

    // ── Suggestions ───────────────────────────────────────────────────────────

    fun getSuggestions(listId: Long, limit: Int = 40): Flow<List<ListSuggestionEntity>> =
        listDao.getSuggestions(listId, limit)

    suspend fun forgetSuggestion(id: Long) = listDao.deleteSuggestion(id)

    // ── Trash ─────────────────────────────────────────────────────────────────

    fun getTrashedLists(): Flow<List<ListEntity>> = listDao.getTrashedLists()

    suspend fun moveToTrash(id: Long) = listDao.moveListToTrash(id, System.currentTimeMillis())

    suspend fun restoreFromTrash(id: Long) = listDao.restoreListFromTrash(id)

    suspend fun deleteForever(id: Long) {
        listDao.deleteSuggestionsForList(id)
        listDao.deleteListForever(id)
    }

    suspend fun emptyTrash() = listDao.emptyListTrash()

    suspend fun purgeTrashedBefore(before: Long) = listDao.purgeTrashedListsBefore(before)
}
