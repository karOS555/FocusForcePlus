package com.focusforceplus.app.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val repository: RoutineRepository,
) : ViewModel() {

    val routines: StateFlow<List<RoutineEntity>> = repository
        .getAllRoutines()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun toggleActive(routine: RoutineEntity) {
        viewModelScope.launch {
            repository.updateRoutine(routine.copy(isActive = !routine.isActive))
        }
    }

    fun deleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            repository.deleteRoutine(routine)
        }
    }
}
