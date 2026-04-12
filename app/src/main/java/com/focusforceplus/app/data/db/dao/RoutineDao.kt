package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Update
    suspend fun update(routine: RoutineEntity)

    @Delete
    suspend fun delete(routine: RoutineEntity)

    @Query("SELECT * FROM routines ORDER BY startTimeHour, startTimeMinute")
    fun getAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    fun getById(id: Long): Flow<RoutineEntity?>

    @Query("""
        SELECT * FROM routines
        WHERE isActive = 1
        AND (',' || activeDays || ',') LIKE ('%,' || :day || ',%')
        ORDER BY startTimeHour, startTimeMinute
    """)
    fun getActiveRoutinesForDay(day: String): Flow<List<RoutineEntity>>
}
