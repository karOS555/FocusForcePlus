package com.focusforceplus.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AlarmEvent(
    val routineId: Long,
    val routineName: String,
    val invincibleMode: Boolean,
    val maxSnoozeCount: Int,
    val maxRescheduleCount: Int,
)

/**
 * Singleton bus used to deliver alarm events to the foreground app.
 * When the app is not running, the event is dropped (the notification handles it instead).
 */
@Singleton
class AlarmEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AlarmEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AlarmEvent> = _events.asSharedFlow()

    fun emit(event: AlarmEvent) { _events.tryEmit(event) }
}
