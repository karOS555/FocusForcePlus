package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow

/** Deleting a routine moves it to the trash; see [TodoDao] for the convention. */
@Dao
interface RoutineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Update
    suspend fun update(routine: RoutineEntity)

    @Query("SELECT * FROM routines WHERE deletedAt IS NULL ORDER BY startTimeHour, startTimeMinute")
    fun getAll(): Flow<List<RoutineEntity>>

    /** Null for a routine that does not exist or sits in the trash. */
    @Query("SELECT * FROM routines WHERE id = :id AND deletedAt IS NULL")
    fun getById(id: Long): Flow<RoutineEntity?>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getByIdIncludingTrashed(id: Long): RoutineEntity?

    @Query(
        """
        SELECT * FROM routines
        WHERE isActive = 1
        AND deletedAt IS NULL
        AND (',' || activeDays || ',') LIKE ('%,' || :day || ',%')
        ORDER BY startTimeHour, startTimeMinute
    """
    )
    fun getActiveRoutinesForDay(day: String): Flow<List<RoutineEntity>>

    // ── Trash ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM routines WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashed(): Flow<List<RoutineEntity>>

    @Query("UPDATE routines SET deletedAt = :at WHERE id = :id")
    suspend fun moveToTrash(id: Long, at: Long)

    @Query("UPDATE routines SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    /** Tasks go with it through the foreign key. */
    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("DELETE FROM routines WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM routines WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTrashedBefore(before: Long)

    // ── Backup / restore ──────────────────────────────────────────────────────

    /** Everything, including the trash. */
    @Query("SELECT * FROM routines")
    suspend fun getAllOnce(): List<RoutineEntity>

    @Query("DELETE FROM routines")
    suspend fun deleteAll()
}
