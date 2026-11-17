package com.focusforceplus.app.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.ui.navigation.AppModule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Leaves Reels in Instagram and Shorts in YouTube, and keeps the rest of both apps
 * usable (issue #2).
 *
 * This is a separate service on purpose. The main blocker only learns which app is
 * in front and never reads a screen. Telling Reels apart from the rest of Instagram
 * needs a look at what is on the screen, so that lives here, switched off unless
 * the user turns it on after its own disclosure, and kept as narrow as it can be:
 *
 * - its configuration limits it to Instagram and YouTube, so no other app's
 *   content ever reaches it,
 * - it only asks whether one of a few screen ids of the Reels or Shorts player is
 *   showing, and reads no text,
 * - when one is, it presses Back once; nothing is stored or sent.
 *
 * The ids are those apps' own and change with their updates now and then. When one
 * changes, the service simply finds nothing; it never guesses.
 */
@AndroidEntryPoint
class ShortFormBlockerService : AccessibilityService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var navRepository: NavRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var instagram = false
    @Volatile private var youtube = false
    private var lastBack = 0L
    private var lastToast = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            combine(
                settings.shortsBlockInstagram,
                settings.shortsBlockYouTube,
                settings.blockerEnabled,
                navRepository.config,
            ) { ig, yt, blockerOn, nav ->
                val on = blockerOn && nav.isOn(AppModule.BLOCKER)
                (on && ig) to (on && yt)
            }.collect { (ig, yt) ->
                instagram = ig
                youtube = yt
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val ids = when {
            pkg == INSTAGRAM && instagram -> INSTAGRAM_REELS_IDS
            pkg == YOUTUBE && youtube -> YOUTUBE_SHORTS_IDS
            else -> return
        }
        val now = SystemClock.elapsedRealtime()
        // One Back per moment is enough; content events come in bursts.
        if (now - lastBack < BACK_SPACING_MILLIS) return
        val root = rootInActiveWindow ?: return
        val screen = android.graphics.Rect().also { root.getBoundsInScreen(it) }
        val bounds = android.graphics.Rect()
        // Only the full-screen player counts. YouTube's home feed has a small Shorts
        // shelf with some of the same ids, and that must stay untouched.
        val showing = ids.any { id ->
            root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { node ->
                node.isVisibleToUser && run {
                    node.getBoundsInScreen(bounds)
                    bounds.height() >= screen.height() * FULL_SCREEN_SHARE
                }
            }
        }
        if (!showing) return
        lastBack = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (now - lastToast > TOAST_SPACING_MILLIS) {
            lastToast = now
            val what = if (pkg == INSTAGRAM) "Reels are" else "Shorts are"
            Toast.makeText(this, "$what off in FocusForce+", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"

        /** Ids of Instagram's full-screen Reels player. */
        private val INSTAGRAM_REELS_IDS = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_root",
            "root_clips_layout",
            "clips_video_container",
        )

        /** Ids of YouTube's Shorts player. */
        private val YOUTUBE_SHORTS_IDS = listOf(
            "reel_player_page_container",
            "shorts_player_container",
            "reel_watch_player",
            "reel_recycler",
        )

        /** How much of the screen a player has to fill to count as open. */
        private const val FULL_SCREEN_SHARE = 0.6f

        private const val BACK_SPACING_MILLIS = 1_500L
        private const val TOAST_SPACING_MILLIS = 30_000L
    }
}
