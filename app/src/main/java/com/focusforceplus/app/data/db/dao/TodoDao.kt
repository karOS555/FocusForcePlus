package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Todos are never removed by the user directly: deleting sets [TodoEntity.deletedAt]
 * and the row moves to the trash. Every query below that feeds a list, a reminder
 * or a count therefore filters on `deletedAt IS NULL`; only the trash queries and
 * the backup see deleted rows.
 */
@Dao
interface TodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL ORDER BY priority DESC, dueDateTime ASC")
    fun getAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL")
    suspend fun getAllActiveOnce(): List<TodoEntity>

    @Query(
        "SELECT * FROM todos WHERE isCompleted = 0 AND deletedAt IS NULL " +
            "ORDER BY priority DESC, dueDateTime ASC"
    )
    fun getUncompleted(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE isCompleted = 0 AND deletedAt IS NULL")
    suspend fun getOpenOnce(): List<TodoEntity>

    @Query(
        "SELECT * FROM todos WHERE isCompleted = 1 AND deletedAt IS NULL ORDER BY completedAt DESC"
    )
    fun getCompleted(): Flow<List<TodoEntity>>

    /** When todos were ticked off, for the statistics; trashed ones still count. */
    @Query("SELECT completedAt FROM todos WHERE completedAt IS NOT NULL AND completedAt >= :since")
    fun getCompletionTimesSince(since: Long): Flow<List<Long>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): TodoEntity?

    @Query("SELECT * FROM todos WHERE id = :id")
    fun observeById(id: Long): Flow<TodoEntity?>

    @Query("UPDATE todos SET checklistJson = :json WHERE id = :id")
    suspend fun updateChecklistJson(id: Long, json: String?)

    // ── Trash ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM todos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashed(): Flow<List<TodoEntity>>

    @Query("UPDATE todos SET deletedAt = :at WHERE id = :id")
    suspend fun moveToTrash(id: Long, at: Long)

    @Query("UPDATE todos SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("DELETE FROM todos WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM todos WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTrashedBefore(before: Long)

    /** Auto-clean of the Done list: old completed todos go to the trash, not away. */
    @Query(
        "UPDATE todos SET deletedAt = :now " +
            "WHERE isCompleted = 1 AND deletedAt IS NULL AND completedAt < :before"
    )
    suspend fun trashCompletedBefore(before: Long, now: Long)

    // ── Backup / restore ──────────────────────────────────────────────────────

    /** Everything, including the trash. */
    @Query("SELECT * FROM todos")
    suspend fun getAllOnce(): List<TodoEntity>

    @Query("DELETE FROM todos")
    suspend fun deleteAll()
}
