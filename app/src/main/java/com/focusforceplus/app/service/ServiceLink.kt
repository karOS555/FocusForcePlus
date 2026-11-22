package com.focusforceplus.app.service

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

/**
 * Whether the two accessibility services are really running right now.
 *
 * Android's settings only say that a service is switched on. After some app
 * updates, and on phones that close apps to save battery, that switch can stay on
 * while the service is not connected: nothing is blocked any more, and nothing says
 * so. The services run in the app's own process, so they can simply tell the rest
 * of the app, which then shows it instead of claiming that everything is fine.
 *
 * Reading this never changes anything about a service. Getting one going again is
 * the user's step in Android's settings (Golden Rule #3: no restarting on our own).
 */
object ServiceLink {
    /** The app blocker. */
    val blocker = MutableStateFlow(false)

    /** The short-video service. */
    val shortVideos = MutableStateFlow(false)

    /**
     * True once a service has been [switchedOn] in Android without being [linked]
     * for longer than a start-up takes. Right after the app starts, Android may
     * still be busy connecting it, and that is not worth a warning.
     *
     * It says false at once and only the warning waits. The pages that show it
     * combine it with everything else they show: while it kept silent for the grace
     * time, Home and the Blocker page stayed empty for as long.
     */
    @OptIn(FlowPreview::class)
    fun notRunning(
        switchedOn: Flow<Boolean>,
        linked: Flow<Boolean>,
        graceMillis: Long = GRACE_MILLIS,
    ): Flow<Boolean> =
        combine(switchedOn, linked) { on, running -> on && !running }
            .distinctUntilChanged()
            .debounce { stalled -> if (stalled) graceMillis else 0L }
            .onStart { emit(false) }
            .distinctUntilChanged()

    private const val GRACE_MILLIS = 2_500L
}
