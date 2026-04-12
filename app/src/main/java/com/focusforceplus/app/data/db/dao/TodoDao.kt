package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("SELECT * FROM todos ORDER BY priority DESC, dueDateTime ASC")
    fun getAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE isCompleted = 0 ORDER BY priority DESC, dueDateTime ASC")
    fun getUncompleted(): Flow<List<TodoEntity>>

    @Query("""
        SELECT * FROM todos
        WHERE isCompleted = 0
        AND dueDateTime IS NOT NULL
        AND dueDateTime < :currentTime
        ORDER BY dueDateTime ASC
    """)
    fun getOverdue(currentTime: Long): Flow<List<TodoEntity>>

    @Query("""
        SELECT * FROM todos
        WHERE dueDateTime IS NOT NULL
        AND dueDateTime BETWEEN :start AND :end
        ORDER BY dueDateTime ASC
    """)
    fun getScheduledForTimeRange(start: Long, end: Long): Flow<List<TodoEntity>>

    @Query("UPDATE todos SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long)

    @Query("UPDATE todos SET snoozeCount = :count WHERE id = :id")
    suspend fun updateSnoozeCount(id: Long, count: Int)
}
