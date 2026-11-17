package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.ListCounts
import com.focusforceplus.app.data.db.dao.ListDao
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListItemEntity
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

    suspend fun createList(name: String, iconKey: String?): Long =
        listDao.insertList(
            ListEntity(
                name = name.trim(),
                iconKey = iconKey,
                sortOrder = listDao.maxListSortOrder() + 1,
                createdAt = System.currentTimeMillis(),
            )
        )

    suspend fun updateList(list: ListEntity) = listDao.updateList(list)

    // ── Items ─────────────────────────────────────────────────────────────────

    fun getItems(listId: Long): Flow<List<ListItemEntity>> = listDao.getItems(listId)

    suspend fun getItemsOnce(listId: Long): List<ListItemEntity> = listDao.getItemsOnce(listId)

    /**
     * Adds an item and remembers it for the suggestions. Adding something that is
     * already on the list and ticked simply unticks it, which is what "milk again"
     * means on a shopping list.
     */
    suspend fun addItem(listId: Long, rawText: String, note: String? = null) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        val now = System.currentTimeMillis()
        val key = text.lowercase()
        val existing = listDao.getItemsOnce(listId).firstOrNull { it.text.lowercase() == key }
        if (existing != null) {
            listDao.updateItem(
                existing.copy(
                    isChecked = false,
                    checkedAt = null,
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
                )
            )
        }
        val known = listDao.getSuggestion(listId, key)
        listDao.upsertSuggestion(
            known?.copy(text = text, useCount = known.useCount + 1, lastUsedAt = now)
                ?: ListSuggestionEntity(listId = listId, key = key, text = text, lastUsedAt = now)
        )
    }

    suspend fun updateItem(item: ListItemEntity) = listDao.updateItem(item)

    suspend fun setChecked(itemId: Long, checked: Boolean) =
        listDao.setChecked(itemId, checked, if (checked) System.currentTimeMillis() else null)

    suspend fun uncheckAll(listId: Long) = listDao.uncheckAll(listId)

    suspend fun deleteItem(itemId: Long) = listDao.deleteItem(itemId)

    suspend fun deleteChecked(listId: Long) = listDao.deleteChecked(listId)

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
