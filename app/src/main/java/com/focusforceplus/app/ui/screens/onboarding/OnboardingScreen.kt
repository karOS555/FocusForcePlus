package com.focusforceplus.app.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusforceplus.app.R
import com.focusforceplus.app.util.PermissionHelper
import kotlinx.coroutines.launch

/**
 * First-launch onboarding: welcome + local-data promise + health disclaimer,
 * permission explanations (why each one exists), a grant checklist, and an optional
 * jump into creating the first routine.
 *
 * Accessibility and Usage Access deliberately route through the prominent
 * disclosure screen instead of straight to system settings (Golden Rule #10).
 */
@Composable
fun OnboardingScreen(
    onFinish: (createFirstRoutine: Boolean) -> Unit,
    onOpenDisclosure: (String) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    // Re-check grants whenever the user returns from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = true,
        ) { page ->
            when (page) {
                0 -> WelcomePage(onContinue = { scope.launch { pagerState.animateScrollToPage(1) } })
                1 -> PermissionsExplainedPage(onContinue = { scope.launch { pagerState.animateScrollToPage(2) } })
                2 -> PermissionChecklistPage(
                    viewModel = viewModel,
                    onOpenDisclosure = onOpenDisclosure,
                    onContinue = { scope.launch { pagerState.animateScrollToPage(3) } },
                )
                3 -> FirstRoutinePage(
                    onCreateRoutine = { viewModel.completeOnboarding { onFinish(true) } },
                    onLater = { viewModel.completeOnboarding { onFinish(false) } },
                )
            }
        }

        // Page indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                        .background(
                            if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

// ── Page 1: Welcome ───────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(120.dp),
        )

        Row {
            Text(
                "FOCUSFORCE",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "+",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Your tool for focus and discipline",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Yours, and only yours",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    "Everything stays on your device. No account, no cloud, no tracking, " +
                        "no ads - the app has no internet access at all.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "An honest note",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    "FocusForce+ is a self-help tool for building focus and habits - not a " +
                        "medical app. It doesn't diagnose, treat, or cure ADHD or any other " +
                        "condition, and it's no substitute for professional care. If you're " +
                        "struggling, please reach out to a qualified healthcare professional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Continue") }
    }
}

// ── Page 2: Permissions explained ─────────────────────────────────────────────

@Composable
private fun PermissionsExplainedPage(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Why FocusForce+ asks for permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Each permission powers exactly one feature. Everything is optional - " +
                "skip any of them and the related feature simply stays off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        ExplainRow(Icons.Filled.Notifications, "Notifications", "Routine alarms and todo reminders.")
        ExplainRow(Icons.Filled.AccessTime, "Exact alarms", "So reminders fire exactly on time.")
        ExplainRow(Icons.Filled.Layers, "Display over other apps", "Shows the alarm screen while you are in another app.")
        ExplainRow(Icons.Filled.Accessibility, "Accessibility service", "Detects which app you open, so blocked apps can be covered. Reads no screen content.")
        ExplainRow(Icons.Filled.QueryStats, "Usage access", "Measures app usage for daily limits and your screen-time stats.")
        ExplainRow(Icons.Filled.BatteryChargingFull, "Battery: unrestricted", "Keeps reminders reliable on aggressive battery savers.")
        ExplainRow(Icons.Filled.DoNotDisturbOn, "Do Not Disturb access", "Lets focus sessions silence interruptions.")

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Set up permissions") }
    }
}

@Composable
private fun ExplainRow(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Page 3: Permission checklist ──────────────────────────────────────────────

@Composable
private fun PermissionChecklistPage(
    viewModel: OnboardingViewModel,
    onOpenDisclosure: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val perms by viewModel.permissions.collectAsState()

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Grant permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "You can skip any of these and grant them later from Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        ChecklistRow("Notifications", perms.notifications) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        ChecklistRow("Exact alarms", perms.exactAlarms) {
            PermissionHelper.openExactAlarmSettings(context)
        }
        ChecklistRow("Display over other apps", perms.overlay) {
            PermissionHelper.openOverlaySettings(context)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ChecklistRow("Full-screen alarms", perms.fullScreenIntent) {
                PermissionHelper.openFullScreenIntentSettings(context)
            }
        }
        ChecklistRow("Accessibility (app blocker)", perms.accessibility) {
            onOpenDisclosure("accessibility")
        }
        ChecklistRow("Usage access (daily limits)", perms.usageStats) {
            onOpenDisclosure("usage")
        }
        ChecklistRow("Battery: unrestricted", perms.batteryUnrestricted) {
            PermissionHelper.openBatteryOptimizationSettings(context)
        }
        ChecklistRow("Do Not Disturb access", perms.dndAccess) {
            PermissionHelper.openDndAccessSettings(context)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Continue") }
    }
}

@Composable
private fun ChecklistRow(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = if (granted) "Granted" else "Not granted",
                tint = if (granted) Color(0xFF34d399) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (!granted) {
                TextButton(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}

// ── Page 4: First routine ─────────────────────────────────────────────────────

@Composable
private fun FirstRoutinePage(
    onCreateRoutine: () -> Unit,
    onLater: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Ready to build your first routine?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Routines are the heart of FocusForce+: multi-step timers with alarm-style " +
                "wake-ups that do not let you quietly skip.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onCreateRoutine,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Yes, let's go") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onLater,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Later") }
    }
}
