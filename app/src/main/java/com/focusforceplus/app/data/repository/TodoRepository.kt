package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.TodoDao
import com.focusforceplus.app.data.db.entity.TodoEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain storage access for todos. It deliberately knows nothing about alarms or
 * notifications: everything that changes what a todo should be doing right now
 * goes through [com.focusforceplus.app.util.TodoReminderEngine], which writes
 * here and then brings alarms and notifications in line.
 */
@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
) {
    /** Open and completed todos; nothing from the trash. */
    fun getAllTodos(): Flow<List<TodoEntity>> = todoDao.getAll()

    fun getUncompletedTodos(): Flow<List<TodoEntity>> = todoDao.getUncompleted()

    fun getCompletedTodos(): Flow<List<TodoEntity>> = todoDao.getCompleted()

    fun getCompletionTimesSince(since: Long): Flow<List<Long>> = todoDao.getCompletionTimesSince(since)

    /** Open todos that remind about a list. */
    fun getOpenForList(listId: Long): Flow<List<TodoEntity>> = todoDao.getOpenForList(listId)

    fun getTrashedTodos(): Flow<List<TodoEntity>> = todoDao.getTrashed()

    fun observeTodo(id: Long): Flow<TodoEntity?> = todoDao.observeById(id)

    suspend fun getTodoById(id: Long): TodoEntity? = todoDao.getById(id)

    suspend fun getOpenTodosOnce(): List<TodoEntity> = todoDao.getOpenOnce()

    suspend fun getAllActiveOnce(): List<TodoEntity> = todoDao.getAllActiveOnce()

    suspend fun insertTodo(todo: TodoEntity): Long = todoDao.insert(todo)

    suspend fun updateTodo(todo: TodoEntity) = todoDao.update(todo)

    suspend fun updateChecklistJson(id: Long, json: String?) = todoDao.updateChecklistJson(id, json)

    // ── Trash ─────────────────────────────────────────────────────────────────

    suspend fun moveToTrash(id: Long) = todoDao.moveToTrash(id, System.currentTimeMillis())

    suspend fun restoreFromTrash(id: Long) = todoDao.restoreFromTrash(id)

    suspend fun deleteForever(id: Long) = todoDao.deleteForever(id)

    suspend fun emptyTrash() = todoDao.emptyTrash()

    suspend fun purgeTrashedBefore(before: Long) = todoDao.purgeTrashedBefore(before)

    suspend fun trashCompletedBefore(before: Long) =
        todoDao.trashCompletedBefore(before, System.currentTimeMillis())
}
