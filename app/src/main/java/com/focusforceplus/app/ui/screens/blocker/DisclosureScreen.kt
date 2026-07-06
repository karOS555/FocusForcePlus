package com.focusforceplus.app.ui.screens.blocker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusforceplus.app.util.PermissionHelper

/** Route argument values for the disclosure screen. */
object DisclosureType {
    const val ACCESSIBILITY = "accessibility"
    const val USAGE_ACCESS = "usage"
}

/**
 * Prominent disclosure shown BEFORE sending the user to the corresponding system
 * settings page (compliance guide section 3.4 / Golden Rule #10). Covers the five
 * required elements: what the permission does, what we use it for, what we do NOT
 * do, how to revoke, explicit confirmation button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclosureScreen(
    type: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val isAccessibility = type == DisclosureType.ACCESSIBILITY

    // Track whether the permission is granted, refreshed when returning from settings.
    var granted by remember { mutableStateOf(isGranted(context, isAccessibility)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = isGranted(context, isAccessibility)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAccessibility) "Blocking service" else "Usage access") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = if (isAccessibility) Icons.Filled.Accessibility else Icons.Filled.QueryStats,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.CenterHorizontally),
            )

            Text(
                text = if (isAccessibility) "Accessibility permission" else "Usage access permission",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            if (isAccessibility) {
                DisclosureSection(
                    "What this permission does",
                    "It lets an app receive a signal from Android whenever a different app " +
                        "comes to the foreground.",
                )
                DisclosureSection(
                    "What FocusForce+ uses it for",
                    "Exactly one thing: recognizing that you opened an app you chose to " +
                        "block, so the blocking screen can appear on top of it.",
                )
                DisclosureSection(
                    "What FocusForce+ does NOT do",
                    "It cannot and does not read your screen content — content access is " +
                        "switched off in the service configuration. It does not collect, " +
                        "store, or transmit anything about other apps. There is no analytics " +
                        "and no internet access in this app at all.",
                )
                DisclosureSection(
                    "How to turn it off again",
                    "Anytime, in Android Settings > Accessibility > FocusForce+ App Blocker. " +
                        "Your rules stay saved; blocking simply pauses.",
                )
                RestrictedSettingsCard(
                    onOpenAppInfo = { PermissionHelper.openAppDetailsSettings(context) },
                )
            } else {
                DisclosureSection(
                    "What this permission does",
                    "It lets an app read the device's app-usage statistics — how long each " +
                        "app was in the foreground.",
                )
                DisclosureSection(
                    "What FocusForce+ uses it for",
                    "Measuring how long you used the apps you set a daily limit for, and " +
                        "showing your own screen-time numbers on the dashboard.",
                )
                DisclosureSection(
                    "What FocusForce+ does NOT do",
                    "Your usage data never leaves the device. It is not collected, shared, " +
                        "or used for anything except the limits and stats you see in the app.",
                )
                DisclosureSection(
                    "How to turn it off again",
                    "Anytime, in Android Settings > Special app access > Usage access > " +
                        "FocusForce+. Daily limits will stop counting; everything else keeps working.",
                )
            }

            Spacer(Modifier.height(8.dp))

            if (granted) {
                // Permission is set — make returning the prominent, obvious action.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Permission granted. You're all set.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Done")
                }
            } else {
                Button(
                    onClick = {
                        if (isAccessibility) PermissionHelper.openAccessibilitySettings(context)
                        else PermissionHelper.openUsageAccessSettings(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Got it — open Android settings")
                }

                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun isGranted(context: android.content.Context, isAccessibility: Boolean): Boolean =
    if (isAccessibility) PermissionHelper.isAccessibilityServiceEnabled(context)
    else PermissionHelper.hasUsageStatsPermission(context)

/**
 * Sideload note: Android blocks the accessibility toggle for apps not installed
 * from the Play Store ("Restricted setting"). We can't change that, so we explain
 * the one-time unblock and offer a direct button to this app's info page.
 */
@Composable
private fun RestrictedSettingsCard(onOpenAppInfo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Seeing \"Restricted setting\"?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Because you installed FocusForce+ directly (not from the Play Store), " +
                    "Android blocks the accessibility toggle the first time. It's a quick " +
                    "one-time unlock. Tap \"Open app info\" below, then:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "• Most phones: tap the three-dot menu (top-right) and choose " +
                    "\"Allow restricted settings\".\n" +
                    "• Samsung: open Permissions, tap the three-dot menu (top-right), " +
                    "then \"Allow restricted settings\".\n\n" +
                    "After that, come back here and turn the service on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenAppInfo,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text("Open app info")
            }
        }
    }
}

@Composable
private fun DisclosureSection(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
