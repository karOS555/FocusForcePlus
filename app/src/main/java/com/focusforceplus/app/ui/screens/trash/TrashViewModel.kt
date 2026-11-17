package com.focusforceplus.app.ui.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.ListRepository
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.ui.navigation.AppModule
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.FocusAlarmHelper
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TrashKind(val label: String) {
    TODO("Todos"),
    ROUTINE("Routines"),
    FOCUS("Focus sessions"),
    LIST("Lists"),
}

data class TrashEntry(
    val kind: TrashKind,
    val id: Long,
    val title: String,
    val iconKey: String?,
    val deletedAt: Long,
)

data class TrashUiState(
    val loading: Boolean = true,
    val entries: List<TrashEntry> = emptyList(),
    val retentionDays: Int = SettingsRepository.DEFAULT_TRASH_RETENTION_DAYS,
)

/**
 * Everything deleted, from every module, in one place. Restoring puts an item back
 * exactly as it was, alarms included; deleting from here is final.
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    todoRepository: TodoRepository,
    private val routineRepository: RoutineRepository,
    private val focusRepository: FocusRepository,
    private val listRepository: ListRepository,
    settingsRepository: SettingsRepository,
    private val navRepository: NavRepository,
    private val todoReminders: TodoReminderEngine,
    private val alarmHelper: AlarmHelper,
    private val focusAlarmHelper: FocusAlarmHelper,
) : ViewModel() {

    val uiState: StateFlow<TrashUiState> = combine(
        todoRepository.getTrashedTodos(),
        routineRepository.getTrashedRoutines(),
        focusRepository.getTrashedSessions(),
        listRepository.getTrashedLists(),
        settingsRepository.trashRetentionDays,
    ) { todos, routines, sessions, lists, days ->
        TrashUiState(
            loading = false,
            entries = buildList {
                todos.forEach { add(TrashEntry(TrashKind.TODO, it.id, it.title, it.iconKey, it.deletedAt ?: 0L)) }
                routines.forEach { add(TrashEntry(TrashKind.ROUTINE, it.id, it.name, it.iconKey, it.deletedAt ?: 0L)) }
                sessions.forEach { add(TrashEntry(TrashKind.FOCUS, it.id, it.name, it.iconKey, it.deletedAt ?: 0L)) }
                lists.forEach { add(TrashEntry(TrashKind.LIST, it.id, it.name, it.iconKey, it.deletedAt ?: 0L)) }
            },
            retentionDays = days,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun restore(entry: TrashEntry) {
        viewModelScope.launch {
            when (entry.kind) {
                TrashKind.TODO -> todoReminders.restoreFromTrash(entry.id)
                TrashKind.ROUTINE -> {
                    routineRepository.restoreFromTrash(entry.id)
                    val routine = routineRepository.getRoutineById(entry.id).first()
                    if (routine != null && navRepository.isModuleOn(AppModule.ROUTINES)) {
                        alarmHelper.scheduleRoutineAlarms(routine)
                    }
                }
                TrashKind.FOCUS -> {
                    focusRepository.restoreFromTrash(entry.id)
                    val session = focusRepository.getSessionById(entry.id).first()
                    if (session != null && navRepository.isModuleOn(AppModule.FOCUS)) {
                        focusAlarmHelper.scheduleSessionAlarms(session)
                    }
                }
                TrashKind.LIST -> listRepository.restoreFromTrash(entry.id)
            }
        }
    }

    fun deleteForever(entry: TrashEntry) {
        viewModelScope.launch {
            when (entry.kind) {
                TrashKind.TODO -> todoReminders.deleteForever(entry.id)
                TrashKind.ROUTINE -> routineRepository.deleteForever(entry.id)
                TrashKind.FOCUS -> focusRepository.deleteForever(entry.id)
                TrashKind.LIST -> listRepository.deleteForever(entry.id)
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            // Todos one by one, so their notifications and alarms go with them.
            uiState.value.entries
                .filter { it.kind == TrashKind.TODO }
                .forEach { todoReminders.deleteForever(it.id) }
            routineRepository.emptyTrash()
            focusRepository.emptyTrash()
            listRepository.emptyTrash()
        }
    }
}
