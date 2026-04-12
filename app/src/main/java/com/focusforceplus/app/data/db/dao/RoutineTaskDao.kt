package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: RoutineTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<RoutineTaskEntity>)

    @Update
    suspend fun update(task: RoutineTaskEntity)

    @Delete
    suspend fun delete(task: RoutineTaskEntity)

    @Query("SELECT * FROM routine_tasks WHERE routineId = :routineId ORDER BY sortOrder")
    fun getTasksForRoutine(routineId: Long): Flow<List<RoutineTaskEntity>>

    @Query("DELETE FROM routine_tasks WHERE routineId = :routineId")
    suspend fun deleteTasksForRoutine(routineId: Long)
}
