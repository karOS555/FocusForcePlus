package com.focusforceplus.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class RoutineSessionAction {
    data class CompleteTask(val routineId: Long) : RoutineSessionAction()
}

/**
 * Singleton bus that lets notification actions (e.g. "Done" on the foreground
 * notification) drive the active-routine ViewModel while the app is running.
 * If the ViewModel is not alive the event is silently dropped.
 */
@Singleton
class RoutineSessionBus @Inject constructor() {
    private val _actions = MutableSharedFlow<RoutineSessionAction>(extraBufferCapacity = 4)
    val actions: SharedFlow<RoutineSessionAction> = _actions.asSharedFlow()

    fun send(action: RoutineSessionAction) { _actions.tryEmit(action) }
}
