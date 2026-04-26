package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.TodoDao
import com.focusforceplus.app.data.db.entity.TodoEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao
) {

    fun getAllTodos(): Flow<List<TodoEntity>> =
        todoDao.getAll()

    fun getUncompletedTodos(): Flow<List<TodoEntity>> =
        todoDao.getUncompleted()

    fun getOverdueTodos(currentTime: Long): Flow<List<TodoEntity>> =
        todoDao.getOverdue(currentTime)

    fun getTodosForTimeRange(start: Long, end: Long): Flow<List<TodoEntity>> =
        todoDao.getScheduledForTimeRange(start, end)

    suspend fun insertTodo(todo: TodoEntity): Long =
        todoDao.insert(todo)

    suspend fun updateTodo(todo: TodoEntity) =
        todoDao.update(todo)

    suspend fun deleteTodo(todo: TodoEntity) =
        todoDao.delete(todo)

    suspend fun markCompleted(id: Long, completedAt: Long) =
        todoDao.markCompleted(id, completedAt)

    suspend fun updateSnoozeCount(id: Long, count: Int) =
        todoDao.updateSnoozeCount(id, count)

    suspend fun updateRescheduleCount(id: Long, count: Int) =
        todoDao.updateRescheduleCount(id, count)

    suspend fun updateChecklistJson(id: Long, json: String?) =
        todoDao.updateChecklistJson(id, json)

    fun getCompletedTodos(): Flow<List<TodoEntity>> =
        todoDao.getCompleted()

    suspend fun getTodoById(id: Long): TodoEntity? =
        todoDao.getById(id)

    suspend fun deleteCompletedBefore(beforeTimestamp: Long) =
        todoDao.deleteCompletedBefore(beforeTimestamp)
}
