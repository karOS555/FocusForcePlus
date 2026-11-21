package com.focusforceplus.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.focusforceplus.app.compliance.ShortFeed
import com.focusforceplus.app.compliance.ShortVideoAllowance
import com.focusforceplus.app.compliance.ShortVideoUsage
import com.focusforceplus.app.compliance.shortVideoTimeIsUp
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.ui.navigation.AppModule
import com.focusforceplus.app.util.BlockerNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Leaves Reels in Instagram and Shorts in YouTube, at once or after the daily time
 * the user allows, and keeps the rest of both apps usable (issue #2).
 *
 * This is a separate service on purpose. The main blocker only learns which app is
 * in front and never reads a screen. Telling Reels apart from the rest of Instagram
 * needs a look at what is on the screen, so that lives here, switched off unless
 * the user turns it on after its own disclosure, and kept as narrow as it can be:
 *
 * - Android only sends it events from the apps whose feed is switched on in the
 *   blocker ([narrowTo]); with none switched on, from no app at all,
 * - it only asks whether one of a few screen ids of the Reels or Shorts player is
 *   showing, and reads no text. While a player is open it asks that again every few
 *   seconds, and then only the player's own parts, never the window in front,
 * - when one is and no time is left, it presses Back once,
 * - with a daily time it keeps one number per feed, today's time in the player.
 *   Nothing else is stored, and nothing is sent.
 *
 * Apps that are short videos through and through (TikTok and the like) are not its
 * business: those are blocked as whole apps by the main blocker.
 *
 * Everything here runs on the main thread: the events, the settings and the clock.
 */
@AndroidEntryPoint
class ShortFormBlockerService : AccessibilityService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var navRepository: NavRepository
    @Inject lateinit var notifications: BlockerNotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    /** The feeds that are switched on and not paused with the blocker, by package. */
    private var feeds: Map<String, ShortFeed> = emptyMap()
    private var allowance = ShortVideoAllowance()

    /** Today's time in the players. Null until it has been read back from storage. */
    private var usage: ShortVideoUsage? = null

    /** The feed whose player was open at the last look, and when that look was. */
    private var watching: ShortFeed? = null
    private var lastLook = 0L
    private var lastSaved = 0L
    private var lastBack = 0L
    private var lastToast = 0L

    /** The open player's own parts on screen, as the last event found them. */
    private var playerNodes: List<AccessibilityNodeInfo> = emptyList()
    private var screenHeight = 0
    private val bounds = Rect()

    private val lookAgain = Runnable { watching?.let { settle(it, playerStillOpen(), byClock = true) } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceLink.shortVideos.value = true
        scope.launch { usage = settings.shortVideoUsage.first() }
        scope.launch {
            combine(
                settings.shortsBlockInstagram,
                settings.shortsBlockYouTube,
                settings.blockerEnabled,
                navRepository.config,
                settings.shortVideoAllowance,
            ) { instagram, youtube, blockerOn, nav, allowed ->
                // Paused with the blocker, and off with the Blocker module.
                val on = blockerOn && nav.isOn(AppModule.BLOCKER)
                val active = buildList {
                    if (on && instagram) add(ShortFeed.INSTAGRAM)
                    if (on && youtube) add(ShortFeed.YOUTUBE)
                }
                active to allowed
            }.collect { (active, allowed) ->
                feeds = active.associateBy { it.packageName }
                allowance = allowed
                narrowTo(feeds.keys)
                if (watching?.packageName !in feeds) stopWatching()
            }
        }
    }

    /**
     * Narrows what Android delivers to exactly the apps whose feed is switched on.
     * With none, the service is pinned to our own package and hears nothing.
     */
    private fun narrowTo(packages: Set<String>) {
        runCatching {
            serviceInfo = serviceInfo?.apply {
                packageNames = if (packages.isEmpty()) arrayOf(packageName) else packages.toTypedArray()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val feed = feeds[pkg] ?: return
        settle(feed, findPlayer(feed), byClock = false)
    }

    /**
     * What follows from a look at [feed]: its player is [open] or not. If it was
     * open at the last look, the time since then is counted; if it is open now and
     * no time is left, the player is left.
     *
     * Back is only ever pressed after an event from the feed's app, when the window
     * in front is known to be that app. A look [byClock] only counts: it knows that
     * the player is visible, not that nothing of another app lies over it.
     */
    private fun settle(feed: ShortFeed, open: Boolean, byClock: Boolean) {
        handler.removeCallbacks(lookAgain)
        val now = SystemClock.elapsedRealtime()

        if (allowance.none) {
            // Nothing is allowed, so there is nothing to count.
            watching = null
            if (open && !byClock) leave(feed, now)
            return
        }

        val counted = usage ?: return // not read back yet; a moment later it is
        val today = LocalDate.now().toEpochDay()
        var current = counted.on(today)
        watching?.let { was ->
            current = current.plus(was, (now - lastLook).coerceIn(0L, LONGEST_STEP_MILLIS), today)
        }
        usage = current
        lastLook = now

        if (!open) {
            playerNodes = emptyList()
            if (watching != null) {
                watching = null
                save(now)
            }
            return
        }
        watching = feed
        if (!byClock && shortVideoTimeIsUp(feed, allowance, current)) leave(feed, now)
        if (now - lastSaved > SAVE_SPACING_MILLIS) save(now)
        // A video can play for minutes without anything changing on screen, so no
        // event comes: look again by the clock, or that time would not be counted.
        handler.postDelayed(lookAgain, LOOK_AGAIN_MILLIS)
    }

    /**
     * After an event from [feed]'s app: whether its full-screen player is what the
     * user is looking at. The parts of it that were found are kept for the looks by
     * the clock.
     */
    private fun findPlayer(feed: ShortFeed): Boolean {
        playerNodes = runCatching {
            if (!screenIsOn()) return@runCatching emptyList()
            val root = rootInActiveWindow ?: return@runCatching emptyList()
            // The event came from the feed's app, but the window in front may be
            // another one by now. Then there is nothing of ours to see.
            if (root.packageName?.toString() != feed.packageName) return@runCatching emptyList()
            screenHeight = Rect().also { root.getBoundsInScreen(it) }.height()
            // Only the full-screen player counts. YouTube's home feed has a small
            // Shorts shelf with some of the same ids, and that must stay untouched.
            feed.playerIds
                .flatMap { id -> root.findAccessibilityNodeInfosByViewId("${feed.packageName}:id/$id") }
                .filter { fillsScreen(it) }
        }.getOrDefault(emptyList())
        return playerNodes.isNotEmpty()
    }

    /**
     * A look by the clock: is the player that was found before still on screen? It
     * asks those very parts of the feed's app again and nothing else, so whatever
     * app the user has moved on to is never looked at. Once they are off screen the
     * counting stops, until the next event from the feed's app.
     */
    private fun playerStillOpen(): Boolean = runCatching {
        screenIsOn() && playerNodes.any { it.refresh() && fillsScreen(it) }
    }.getOrDefault(false)

    private fun fillsScreen(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        node.getBoundsInScreen(bounds)
        return bounds.height() >= screenHeight * FULL_SCREEN_SHARE
    }

    private fun screenIsOn(): Boolean = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive

    /** Presses Back once, which lands on the rest of the app, and says why now and then. */
    private fun leave(feed: ShortFeed, now: Long) {
        // One Back per moment is enough; content events come in bursts.
        if (now - lastBack < BACK_SPACING_MILLIS) return
        lastBack = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (now - lastToast > TOAST_SPACING_MILLIS) {
            lastToast = now
            val text = when {
                allowance.none -> "${feed.noun} are off in FocusForce+"
                allowance.each -> "Today's ${allowance.minutes} min of ${feed.noun} are used"
                else -> "Today's ${allowance.minutes} min of short videos are used"
            }
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopWatching() {
        handler.removeCallbacks(lookAgain)
        playerNodes = emptyList()
        if (watching != null) {
            watching = null
            save(SystemClock.elapsedRealtime())
        }
    }

    /** Writes today's numbers down. Not tied to the service's life: the last ones must land. */
    private fun save(now: Long) {
        val snapshot = usage ?: return
        lastSaved = now
        scope.launch(NonCancellable) { settings.saveShortVideoUsage(snapshot) }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceLink.shortVideos.value = false
        // The user (or the system) switched the service off while feeds were meant
        // to be left. Compliance: say so once and do nothing else; never restart it
        // and never steer the user back (Golden Rules #2 and #3).
        if (feeds.isNotEmpty()) runCatching { notifications.showShortVideoServiceLostNotification() }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceLink.shortVideos.value = false
        stopWatching()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /** How much of the screen a player has to fill to count as open. */
        const val FULL_SCREEN_SHARE = 0.6f

        const val BACK_SPACING_MILLIS = 1_500L
        const val TOAST_SPACING_MILLIS = 30_000L

        /** While a player is open: how often to look again when nothing happens on screen. */
        const val LOOK_AGAIN_MILLIS = 5_000L

        /**
         * The most one look may add. A look that comes late, because the phone slept
         * or the clock was held up, never books more than this.
         */
        const val LONGEST_STEP_MILLIS = 8_000L

        /** While counting: how often the numbers are written down. */
        const val SAVE_SPACING_MILLIS = 20_000L
    }
}
