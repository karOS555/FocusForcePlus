package com.focusforceplus.app

import android.app.Application
import com.focusforceplus.app.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FocusForcePlusApp : Application() {

    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }
}
