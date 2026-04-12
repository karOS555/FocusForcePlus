package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.RoutineDao
import com.focusforceplus.app.data.db.dao.RoutineTaskDao
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val routineTaskDao: RoutineTaskDao
) {

    // --- Routines ---

    fun getAllRoutines(): Flow<List<RoutineEntity>> =
        routineDao.getAll()

    fun getRoutineById(id: Long): Flow<RoutineEntity?> =
        routineDao.getById(id)

    fun getActiveRoutinesForDay(day: String): Flow<List<RoutineEntity>> =
        routineDao.getActiveRoutinesForDay(day)

    suspend fun insertRoutine(routine: RoutineEntity): Long =
        routineDao.insert(routine)

    suspend fun updateRoutine(routine: RoutineEntity) =
        routineDao.update(routine)

    suspend fun deleteRoutine(routine: RoutineEntity) =
        routineDao.delete(routine)

    // --- Routine Tasks ---

    fun getTasksForRoutine(routineId: Long): Flow<List<RoutineTaskEntity>> =
        routineTaskDao.getTasksForRoutine(routineId)

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

    /** Ersetzt alle Tasks einer Routine atomisch. */
    suspend fun replaceTasksForRoutine(routineId: Long, tasks: List<RoutineTaskEntity>) {
        routineTaskDao.deleteTasksForRoutine(routineId)
        routineTaskDao.insertAll(tasks)
    }
}
