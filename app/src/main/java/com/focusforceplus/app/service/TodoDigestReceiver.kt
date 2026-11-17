package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.util.TodoAlarmScheduler
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One of the daily reminder times came up. */
@AndroidEntryPoint
class TodoDigestReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: TodoReminderEngine

    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getIntExtra(TodoAlarmScheduler.EXTRA_DIGEST_SLOT, 0)
        val hour = intent.getIntExtra(TodoAlarmScheduler.EXTRA_DIGEST_HOUR, 8)
        val minute = intent.getIntExtra(TodoAlarmScheduler.EXTRA_DIGEST_MINUTE, 0)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                engine.onDailyReminder(slot, hour, minute)
            } finally {
                pending.finish()
            }
        }
    }
}
