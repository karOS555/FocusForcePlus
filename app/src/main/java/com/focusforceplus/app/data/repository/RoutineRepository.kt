package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.RoutineCompletionDao
import com.focusforceplus.app.data.db.dao.RoutineDao
import com.focusforceplus.app.data.db.dao.RoutineTaskDao
import com.focusforceplus.app.data.db.entity.RoutineCompletionEntity
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val routineTaskDao: RoutineTaskDao,
    private val routineCompletionDao: RoutineCompletionDao,
) {
    // --- Routines ---

    /** Every routine that is not in the trash. */
    fun getAllRoutines(): Flow<List<RoutineEntity>> =
        routineDao.getAll()

    /** Emits null for a routine that does not exist or sits in the trash. */
    fun getRoutineById(id: Long): Flow<RoutineEntity?> =
        routineDao.getById(id)

    fun getActiveRoutinesForDay(day: String): Flow<List<RoutineEntity>> =
        routineDao.getActiveRoutinesForDay(day)

    suspend fun insertRoutine(routine: RoutineEntity): Long =
        routineDao.insert(routine)

    suspend fun updateRoutine(routine: RoutineEntity) =
        routineDao.update(routine)

    // --- Trash ---

    fun getTrashedRoutines(): Flow<List<RoutineEntity>> = routineDao.getTrashed()

    suspend fun getRoutineIncludingTrashed(id: Long): RoutineEntity? =
        routineDao.getByIdIncludingTrashed(id)

    suspend fun moveToTrash(id: Long) = routineDao.moveToTrash(id, System.currentTimeMillis())

    suspend fun restoreFromTrash(id: Long) = routineDao.restoreFromTrash(id)

    suspend fun deleteForever(id: Long) = routineDao.deleteForever(id)

    suspend fun emptyTrash() = routineDao.emptyTrash()

    suspend fun purgeTrashedBefore(before: Long) = routineDao.purgeTrashedBefore(before)

    // --- Routine Tasks ---

    fun getTasksForRoutine(routineId: Long): Flow<List<RoutineTaskEntity>> =
        routineTaskDao.getTasksForRoutine(routineId)

    /** Every step of every routine: the pool the task suggestions draw from. */
    fun getAllTasks(): Flow<List<RoutineTaskEntity>> =
        routineTaskDao.getAll()

    suspend fun insertTask(task: RoutineTaskEntity): Long =
        routineTaskDao.insert(task)

    suspend fun insertTasks(tasks: List<RoutineTaskEntity>) =
        routineTaskDao.insertAll(tasks)

    suspend fun updateTask(task: RoutineTaskEntity) =
        routineTaskDao.update(task)

    suspend fun deleteTask(task: RoutineTaskEntity) =
        routineTaskDao.delete(task)

    suspend fun deleteTasksForRoutine(routineId: Long) =
        routineTaskDao.deleteTasksForRoutine(routineId)

    /** Replaces all tasks of a routine. */
    suspend fun replaceTasksForRoutine(routineId: Long, tasks: List<RoutineTaskEntity>) {
        routineTaskDao.deleteTasksForRoutine(routineId)
        routineTaskDao.insertAll(tasks)
    }

    // --- Completion history ---

    suspend fun recordCompletion(completion: RoutineCompletionEntity) =
        routineCompletionDao.insert(completion)

    fun getCompletionsSince(since: Long): Flow<List<RoutineCompletionEntity>> =
        routineCompletionDao.getSince(since)
}
