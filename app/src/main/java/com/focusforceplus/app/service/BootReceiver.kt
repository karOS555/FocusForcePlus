package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.TodoAlarmHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var routineRepository: RoutineRepository
    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var alarmHelper: AlarmHelper
    @Inject lateinit var todoAlarmHelper: TodoAlarmHelper

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                alarmHelper.rescheduleAll(routineRepository.getAllRoutines().first())
                val digestTimes = settingsRepository.digestTimes.first()
                todoAlarmHelper.rescheduleAll(todoRepository.getAllTodos().first(), digestTimes)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
