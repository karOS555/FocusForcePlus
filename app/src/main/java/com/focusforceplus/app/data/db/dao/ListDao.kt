package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.data.db.entity.ListSuggestionEntity
import kotlinx.coroutines.flow.Flow

/** Open and total item counts of a list, for the overview. */
data class ListCounts(
    val listId: Long,
    val openCount: Int,
    val totalCount: Int,
)

@Dao
interface ListDao {

    // ── Lists ─────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ListEntity): Long

    @Update
    suspend fun updateList(list: ListEntity)

    @Query("SELECT * FROM lists WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun getLists(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    suspend fun getListsOnce(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE id = :id AND deletedAt IS NULL")
    fun observeList(id: Long): Flow<ListEntity?>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun getListById(id: Long): ListEntity?

    @Query(
        "SELECT listId, SUM(CASE WHEN isChecked = 0 THEN 1 ELSE 0 END) AS openCount, " +
            "COUNT(*) AS totalCount FROM list_items GROUP BY listId"
    )
    fun getCounts(): Flow<List<ListCounts>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM lists")
    suspend fun maxListSortOrder(): Int

    // ── Items ─────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ListItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ListItemEntity>)

    @Update
    suspend fun updateItem(item: ListItemEntity)

    @Query("SELECT * FROM list_items WHERE listId = :listId ORDER BY isChecked, sortOrder, createdAt")
    fun getItems(listId: Long): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items WHERE listId = :listId ORDER BY isChecked, sortOrder, createdAt")
    suspend fun getItemsOnce(listId: Long): List<ListItemEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM list_items WHERE listId = :listId")
    suspend fun maxItemSortOrder(listId: Long): Int

    @Query("UPDATE list_items SET isChecked = :checked, checkedAt = :at WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean, at: Long?)

    @Query("SELECT * FROM list_items WHERE listId = :listId AND isChecked = 0 ORDER BY sortOrder, createdAt")
    fun getOpenItems(listId: Long): Flow<List<ListItemEntity>>

    @Query("UPDATE list_suggestions SET section = :section WHERE listId = :listId AND `key` = :key")
    suspend fun setSuggestionSection(listId: Long, key: String, section: String?)

    @Query("UPDATE list_items SET section = :to WHERE listId = :listId AND section = :from")
    suspend fun renameSection(listId: Long, from: String, to: String?)

    @Query("UPDATE list_suggestions SET section = :to WHERE listId = :listId AND section = :from")
    suspend fun renameSuggestionSection(listId: Long, from: String, to: String?)

    @Query("UPDATE list_items SET isChecked = 0, checkedAt = NULL WHERE listId = :listId")
    suspend fun uncheckAll(listId: Long)

    @Query("DELETE FROM list_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM list_items WHERE listId = :listId AND isChecked = 1")
    suspend fun deleteChecked(listId: Long)

    // ── Suggestions ───────────────────────────────────────────────────────────

    @Query("SELECT * FROM list_suggestions WHERE listId = :listId AND `key` = :key LIMIT 1")
    suspend fun getSuggestion(listId: Long, key: String): ListSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuggestion(suggestion: ListSuggestionEntity): Long

    @Query(
        "SELECT * FROM list_suggestions WHERE listId = :listId " +
            "ORDER BY useCount DESC, lastUsedAt DESC LIMIT :limit"
    )
    fun getSuggestions(listId: Long, limit: Int): Flow<List<ListSuggestionEntity>>

    @Query("DELETE FROM list_suggestions WHERE id = :id")
    suspend fun deleteSuggestion(id: Long)

    @Query("DELETE FROM list_suggestions WHERE listId = :listId")
    suspend fun deleteSuggestionsForList(listId: Long)

    // ── Trash ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM lists WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashedLists(): Flow<List<ListEntity>>

    @Query("UPDATE lists SET deletedAt = :at WHERE id = :id")
    suspend fun moveListToTrash(id: Long, at: Long)

    @Query("UPDATE lists SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreListFromTrash(id: Long)

    /** Items go with it through the foreign key. */
    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteListForever(id: Long)

    @Query("DELETE FROM lists WHERE deletedAt IS NOT NULL")
    suspend fun emptyListTrash()

    @Query("DELETE FROM lists WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTrashedListsBefore(before: Long)

    // ── Backup / restore ──────────────────────────────────────────────────────

    @Query("SELECT * FROM lists")
    suspend fun getAllListsOnce(): List<ListEntity>

    @Query("SELECT * FROM list_items")
    suspend fun getAllItemsOnce(): List<ListItemEntity>

    /** Every item of every list, for the home-screen widgets. */
    @Query("SELECT * FROM list_items")
    fun observeAllItems(): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_suggestions")
    suspend fun getAllSuggestionsOnce(): List<ListSuggestionEntity>

    @Query("DELETE FROM list_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM list_suggestions")
    suspend fun deleteAllSuggestions()

    @Query("DELETE FROM lists")
    suspend fun deleteAllLists()
}
