package com.focusforceplus.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.ui.alarm.RoutineAlarmActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.ui.common.FeatureHelpAction
import com.focusforceplus.app.ui.common.FeatureHelpContent
import com.focusforceplus.app.ui.common.FocusForceTopBar
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.navigation.AppModule
import com.focusforceplus.app.ui.navigation.Dock
import com.focusforceplus.app.ui.navigation.FocusRoutes
import com.focusforceplus.app.ui.navigation.DockFooter
import com.focusforceplus.app.ui.navigation.NavEditSheet
import com.focusforceplus.app.ui.navigation.NavGraph
import com.focusforceplus.app.ui.navigation.NavViewModel
import com.focusforceplus.app.ui.navigation.OnboardingRoutes
import com.focusforceplus.app.ui.navigation.RoutineRoutes
import com.focusforceplus.app.ui.navigation.SettingsRoutes
import com.focusforceplus.app.ui.navigation.StatsRoutes
import com.focusforceplus.app.ui.navigation.TodoRoutes
import com.focusforceplus.app.ui.navigation.isFocusedRoute
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FocusForceTheme
import com.focusforceplus.app.util.AlarmEventBus
import com.focusforceplus.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var alarmEventBus: AlarmEventBus
    @Inject lateinit var settingsRepository: SettingsRepository

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled in UI state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The app draws its own dark background edge to edge, so the system bars
        // always need light icons, whatever the phone's own light/dark setting is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        // Keep the splash visible until we know whether onboarding is needed.
        // Compose state so setContent recomposes once the value arrives.
        val onboardingState = androidx.compose.runtime.mutableStateOf<Boolean?>(null)
        splashScreen.setKeepOnScreenCondition { onboardingState.value == null }
        lifecycleScope.launch {
            askForFullScreenIntent.value =
                PermissionHelper.fullScreenIntentState(this@MainActivity) ==
                    PermissionHelper.FullScreenIntentState.GRANTABLE &&
                !settingsRepository.fullScreenIntentPromptShown.first()
            val completed = settingsRepository.onboardingCompleted.first()
            onboardingState.value = completed
            // Prominent disclosure first (Golden Rule #10): new users learn about
            // notifications in onboarding; only returning users get the system
            // prompt directly.
            if (completed &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PermissionHelper.canPostNotifications(this@MainActivity)
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // When an alarm fires while the app is in the foreground, open the alarm screen directly.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                alarmEventBus.events.collect { event ->
                    startActivity(
                        Intent(this@MainActivity, RoutineAlarmActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("routineId", event.routineId)
                            putExtra("routineName", event.routineName)
                            putExtra("snoozeCount", 0)
                            putExtra("isPreAlarm", false)
                        }
                    )
                }
            }
        }

        routineNavRequest.value = intent.routineId() to 0
        openRouteRequest.value = intent.openRoute() to 0
        setContent {
            FocusForceTheme {
                // Direct .value read is tracked by composition; the local val allows smart-cast.
                val completed = onboardingState.value
                if (completed != null) {
                    AppContent(
                        onboardingCompleted       = completed,
                        routineNavRequest         = routineNavRequest.value,
                        openRouteRequest          = openRouteRequest.value,
                        canDrawOverlays           = PermissionHelper.canDrawOverlays(this),
                        canScheduleExactAlarms    = PermissionHelper.canScheduleExactAlarms(this),
                        askForFullScreen          = askForFullScreenIntent.value,
                        onOpenOverlaySettings     = { PermissionHelper.openOverlaySettings(this) },
                        onOpenExactAlarmSettings  = { PermissionHelper.openExactAlarmSettings(this) },
                        onOpenFullScreenSettings  = { PermissionHelper.openFullScreenIntentSettings(this) },
                        onFullScreenPromptSeen    = {
                            lifecycleScope.launch { settingsRepository.markFullScreenIntentPromptShown() }
                        },
                    )
                }
            }
        }
    }

    /**
     * Navigation request for the active-routine screen. The nonce makes repeated
     * launches with the same routineId (e.g. the task-end wake-up re-firing) still
     * re-trigger navigation while the activity is alive.
     */
    private val routineNavRequest = androidx.compose.runtime.mutableStateOf(0L to 0)

    /**
     * Whether to offer the optional lock-screen alarm permission this launch.
     * Only true when the permission is missing, this device actually has a screen
     * to grant it on, and we have not asked before.
     */
    private val askForFullScreenIntent = androidx.compose.runtime.mutableStateOf(false)

    /**
     * A top-level route to open, asked for by a notification or a shortcut. The
     * nonce makes a repeated request for the same route navigate again.
     */
    private val openRouteRequest = androidx.compose.runtime.mutableStateOf<Pair<String?, Int>>(null to 0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.routineId()
        if (id > 0L) {
            routineNavRequest.value = id to routineNavRequest.value.second + 1
        }
        intent.openRoute()?.let { route ->
            openRouteRequest.value = route to openRouteRequest.value.second + 1
        }
    }

    /** Only routes of known modules are accepted; anything else is ignored. */
    private fun Intent.openRoute(): String? =
        getStringExtra(EXTRA_OPEN_ROUTE)?.takeIf { route ->
            AppModule.fromRoute(route) != null || isDirectRoute(route)
        }

    companion object {
        /** Intent extra: route of the module to show, e.g. "todos". */
        const val EXTRA_OPEN_ROUTE = "openRoute"

        /** The running focus session, opened from its notification. */
        private val ACTIVE_FOCUS_ROUTE = Regex("focus/active/[0-9]+")

        /** Screens below the modules that other parts of the app may open directly. */
        fun isDirectRoute(route: String): Boolean =
            ACTIVE_FOCUS_ROUTE.matches(route) || route == SettingsRoutes.INTEGRATIONS ||
                route == SettingsRoutes.AUTO_BACKUP || route == TodoRoutes.CREATE
    }

    private fun Intent.routineId(): Long =
        getLongExtra("routineId", 0L).takeIf { it > 0L } ?: 0L
}

@Composable
private fun AppContent(
    onboardingCompleted: Boolean,
    routineNavRequest: Pair<Long, Int> = 0L to 0,
    openRouteRequest: Pair<String?, Int> = null to 0,
    canDrawOverlays: Boolean = true,
    canScheduleExactAlarms: Boolean = true,
    askForFullScreen: Boolean = false,
    onOpenOverlaySettings: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onOpenFullScreenSettings: () -> Unit = {},
    onFullScreenPromptSeen: () -> Unit = {},
) {
    val navController = rememberNavController()
    // Show dialogs in priority order — but never during onboarding, which has its
    // own permission checklist with proper disclosures.
    var showOverlayDialog     by remember { mutableStateOf(onboardingCompleted && !canDrawOverlays) }
    var showExactAlarmDialog  by remember { mutableStateOf(onboardingCompleted && !canScheduleExactAlarms && canDrawOverlays) }
    var showFullScreenDialog  by remember { mutableStateOf(onboardingCompleted && askForFullScreen && canDrawOverlays && canScheduleExactAlarms) }
    LaunchedEffect(showFullScreenDialog) {
        if (showFullScreenDialog) onFullScreenPromptSeen()
    }

    val navViewModel: NavViewModel = hiltViewModel()
    val navConfig by navViewModel.config.collectAsState()
    val playDockHint by navViewModel.showDockHint.collectAsState()
    val navMessage by navViewModel.message.collectAsState()
    var showNavEdit by remember { mutableStateOf(false) }
    var showRateInfo by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentModule = AppModule.fromRoute(currentRoute)?.takeIf { it in AppModule.topLevel }
    // The top bar carries each tab's title, so no screen repeats it below the bar.
    // Home keeps the wordmark instead of a title.
    val screenTitle = currentModule?.takeIf { it != AppModule.HOME }?.label
    val routeHelp = when (currentModule) {
        AppModule.ROUTINES -> FeatureHelpContent.ROUTINES
        AppModule.TODOS -> FeatureHelpContent.TODOS
        AppModule.LISTS -> FeatureHelpContent.LISTS
        AppModule.BLOCKER -> FeatureHelpContent.BLOCKER
        AppModule.FOCUS -> FeatureHelpContent.FOCUS
        AppModule.SLEEP -> FeatureHelpContent.SLEEP
        else -> null
    }
    // null = NavController not yet navigated (first frame): default by onboarding state.
    val showTopBar = if (currentRoute == null) onboardingCompleted else currentModule != null
    val hideChrome = isFocusedRoute(currentRoute) || (currentRoute == null && !onboardingCompleted)

    fun openModule(module: AppModule) {
        // Read the route now, at the tap. A value captured when this function was
        // created can be stale by the time the bar calls it, and then a tap on a
        // tab was ignored as "already there".
        val route = navController.currentDestination?.route
        if (module.route == route) return
        // Tapping the module you are already in, on one of its inner pages, goes
        // back to the module's own page. Restoring its saved state would only
        // bring the inner page back, which looked like the tap did nothing.
        if (module == AppModule.owning(route) &&
            navController.popBackStack(module.route, inclusive = false)
        ) {
            return
        }
        if (module in AppModule.topLevel) {
            navController.navigate(module.route) {
                popUpTo(AppModule.HOME.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(module.route) { launchSingleTop = true }
        }
    }

    // Navigate to active routine when launched (or re-launched) from an alarm
    // notification or the task-end wake-up. Keyed on the nonce so re-launches with
    // the same routineId navigate again.
    LaunchedEffect(routineNavRequest) {
        if (routineNavRequest.first > 0L) {
            navController.navigate(RoutineRoutes.active(routineNavRequest.first))
        }
    }

    LaunchedEffect(openRouteRequest) {
        val route = openRouteRequest.first
        val module = AppModule.fromRoute(route)
        when {
            module != null -> openModule(module)
            route != null && MainActivity.isDirectRoute(route) ->
                if (navController.currentDestination?.route != FocusRoutes.ACTIVE) {
                    navController.navigate(route) { launchSingleTop = true }
                }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = FfTheme.colors.background,
        topBar = {
            if (showTopBar && !hideChrome) FocusForceTopBar(
                screenTitle = screenTitle,
                onSettingsClick = { navController.navigate(SettingsRoutes.SETTINGS) },
                actions = {
                    routeHelp?.let { FeatureHelpAction(it) }
                    if (currentModule == AppModule.HOME) {
                        IconButton(onClick = { navController.navigate(StatsRoutes.STATS) }) {
                            Icon(Icons.Filled.BarChart, contentDescription = "Statistics")
                        }
                    }
                },
            )
        },
        bottomBar = {
            val config = navConfig
            if (!hideChrome && config != null) {
                Column {
                    Dock(
                        entries = config.dock,
                        currentRoute = currentRoute,
                        onSelect = ::openModule,
                        onEdit = { showNavEdit = true },
                        playHint = playDockHint,
                        onHintPlayed = navViewModel::noteHintPlayed,
                        onUserScrolled = navViewModel::noteUserScrolled,
                    )
                    DockFooter(onRate = { showRateInfo = true })
                }
            }
        },
    ) { innerPadding ->
        NavGraph(
            navController    = navController,
            // consumeWindowInsets is what keeps the status/navigation bar insets
            // from being applied a second time by every screen's own Scaffold.
            modifier         = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            startDestination = if (onboardingCompleted) AppModule.HOME.route
                               else OnboardingRoutes.ONBOARDING,
        )
    }

    navConfig?.let { config ->
        if (showNavEdit) {
            NavEditSheet(
                config = config,
                onMove = navViewModel::move,
                onSetShown = navViewModel::setShown,
                onSetModuleOn = navViewModel::setModuleOn,
                onReset = navViewModel::resetOrder,
                onDismiss = { showNavEdit = false },
            )
        }
    }

    navMessage?.let { text ->
        ConfirmDialog(
            title = "Not right now",
            message = text,
            confirmLabel = "OK",
            dismissLabel = "Close",
            onConfirm = navViewModel::clearMessage,
            onDismiss = navViewModel::clearMessage,
        )
    }

    if (showRateInfo) {
        ConfirmDialog(
            title = "Coming soon",
            message = "FocusForce+ is not on the Play Store yet. This link goes live with " +
                "the first release there. Thanks for wanting to rate it already.",
            confirmLabel = "OK",
            dismissLabel = "Close",
            onConfirm = { showRateInfo = false },
            onDismiss = { showRateInfo = false },
        )
    }

    // "Display over other apps" — needed so the alarm screen appears even when another
    // app is open. Without this, the alarm only shows a notification banner.
    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text("Allow alarm screen over other apps") },
            text  = {
                Text(
                    "For FocusForce+ to show the full alarm screen when you are using another " +
                    "app, it needs the \"Display over other apps\" permission.\n\n" +
                    "Tap \"Open settings\" and enable the toggle for FocusForce+, then come back.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showOverlayDialog = false
                    onOpenOverlaySettings()
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) { Text("Not now") }
            },
        )
    }

    // SCHEDULE_EXACT_ALARM (Android 12 only) — needed so the 15-min pre-alarm fires on time.
    // On Android 13+, USE_EXACT_ALARM in the manifest auto-grants this.
    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = { Text("Allow precise reminders") },
            text  = {
                Text(
                    "FocusForce+ needs permission to set precise alarms so the 15-minute " +
                    "pre-alarm fires exactly on time.\n\n" +
                    "Tap \"Open settings\" and enable \"Alarms & reminders\" for FocusForce+.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showExactAlarmDialog = false
                    onOpenExactAlarmSettings()
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmDialog = false }) { Text("Not now") }
            },
        )
    }

    // USE_FULL_SCREEN_INTENT (Android 14+ only) — optional polish for the lock-screen
    // alarm. Asked at most once, and only on phones that actually have the setting;
    // it stays available in Settings > Permissions.
    if (showFullScreenDialog) {
        AlertDialog(
            onDismissRequest = { showFullScreenDialog = false },
            title = { Text("Enable lock-screen alarm") },
            text  = {
                Text(
                    "This is optional. It lets alarms show over your lock screen instead " +
                    "of only as a banner - everything else works either way.\n\n" +
                    "Tap \"Open settings\" and enable full-screen notifications for " +
                    "FocusForce+. You will not be asked again; the setting stays under " +
                    "Settings > Permissions.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showFullScreenDialog = false
                    onOpenFullScreenSettings()
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showFullScreenDialog = false }) { Text("Not now") }
            },
        )
    }
}
