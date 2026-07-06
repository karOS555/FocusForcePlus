package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.focusforceplus.app.data.db.entity.FocusCompletionEntity
import com.focusforceplus.app.data.db.entity.RoutineCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineCompletionDao {

    @Insert
    suspend fun insert(completion: RoutineCompletionEntity): Long

    @Query("SELECT * FROM routine_completions WHERE completedAt >= :since ORDER BY completedAt DESC")
    fun getSince(since: Long): Flow<List<RoutineCompletionEntity>>

    @Query("SELECT * FROM routine_completions ORDER BY completedAt DESC")
    suspend fun getAllOnce(): List<RoutineCompletionEntity>

    @Query("DELETE FROM routine_completions")
    suspend fun deleteAll()
}

@Dao
interface FocusCompletionDao {

    @Insert
    suspend fun insert(completion: FocusCompletionEntity): Long

    @Query("SELECT * FROM focus_completions WHERE completedAt >= :since ORDER BY completedAt DESC")
    fun getSince(since: Long): Flow<List<FocusCompletionEntity>>

    @Query("SELECT * FROM focus_completions ORDER BY completedAt DESC")
    suspend fun getAllOnce(): List<FocusCompletionEntity>

    @Query("DELETE FROM focus_completions")
    suspend fun deleteAll()
}
