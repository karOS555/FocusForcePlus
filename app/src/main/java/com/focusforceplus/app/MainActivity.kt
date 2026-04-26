package com.focusforceplus.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.focusforceplus.app.ui.alarm.RoutineAlarmActivity
import androidx.compose.foundation.layout.Column
import com.focusforceplus.app.ui.common.AppFooter
import com.focusforceplus.app.ui.common.FocusForceTopBar
import com.focusforceplus.app.ui.navigation.BottomNavBar
import com.focusforceplus.app.ui.navigation.NavGraph
import com.focusforceplus.app.ui.navigation.RoutineRoutes
import com.focusforceplus.app.ui.navigation.SettingsRoutes
import com.focusforceplus.app.ui.navigation.bottomNavScreens
import com.focusforceplus.app.ui.theme.FocusForceTheme
import com.focusforceplus.app.util.AlarmEventBus
import com.focusforceplus.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var alarmEventBus: AlarmEventBus

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled in UI state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.canPostNotifications(this)
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

        val alarmRoutineId = intent.routineId()
        setContent {
            FocusForceTheme {
                AppContent(
                    initialRoutineId          = alarmRoutineId,
                    canDrawOverlays           = PermissionHelper.canDrawOverlays(this),
                    canScheduleExactAlarms    = PermissionHelper.canScheduleExactAlarms(this),
                    canFullScreen             = PermissionHelper.canUseFullScreenIntent(this),
                    onOpenOverlaySettings     = { PermissionHelper.openOverlaySettings(this) },
                    onOpenExactAlarmSettings  = { PermissionHelper.openExactAlarmSettings(this) },
                    onOpenFullScreenSettings  = { PermissionHelper.openFullScreenIntentSettings(this) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun Intent.routineId(): Long =
        getLongExtra("routineId", 0L).takeIf { it > 0L } ?: 0L
}

@Composable
private fun AppContent(
    initialRoutineId: Long = 0L,
    canDrawOverlays: Boolean = true,
    canScheduleExactAlarms: Boolean = true,
    canFullScreen: Boolean = true,
    onOpenOverlaySettings: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onOpenFullScreenSettings: () -> Unit = {},
) {
    val navController = rememberNavController()
    var pendingRoutineId by remember { mutableLongStateOf(initialRoutineId) }
    // Show dialogs in priority order: overlay first, then exact alarms, then full-screen intent.
    var showOverlayDialog     by remember { mutableStateOf(!canDrawOverlays) }
    var showExactAlarmDialog  by remember { mutableStateOf(!canScheduleExactAlarms && canDrawOverlays) }
    var showFullScreenDialog  by remember { mutableStateOf(!canFullScreen && canDrawOverlays && canScheduleExactAlarms) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val mainTabRoutes = remember { bottomNavScreens.map { it.route }.toSet() }
    // null = NavController not yet navigated (first frame) → default to showing the bar
    val showTopBar = currentRoute == null || currentRoute in mainTabRoutes

    // Navigate to active routine when launched from an alarm notification.
    LaunchedEffect(pendingRoutineId) {
        if (pendingRoutineId > 0L) {
            navController.navigate(RoutineRoutes.active(pendingRoutineId))
            pendingRoutineId = 0L
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showTopBar) FocusForceTopBar(
                onSettingsClick = { navController.navigate(SettingsRoutes.SETTINGS) },
            )
        },
        bottomBar = {
            Column {
                BottomNavBar(navController)
                AppFooter()
            }
        },
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier      = Modifier.padding(innerPadding),
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

    // USE_FULL_SCREEN_INTENT (Android 14+ only) — needed for the lock-screen alarm.
    if (showFullScreenDialog) {
        AlertDialog(
            onDismissRequest = { showFullScreenDialog = false },
            title = { Text("Enable lock-screen alarm") },
            text  = {
                Text(
                    "FocusForce+ needs permission to show the routine alarm over your lock " +
                    "screen. Without it, alarms will only appear as a notification banner " +
                    "when the phone is locked.\n\n" +
                    "Tap \"Open settings\", enable \"Full-screen notifications\" for " +
                    "FocusForce+, then come back.",
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
