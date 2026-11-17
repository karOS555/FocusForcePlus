package com.focusforceplus.app.ui.screens.blocker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.OutlineButton
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.PermissionHelper

/** Route argument values for the disclosure screen. */
object DisclosureType {
    const val ACCESSIBILITY = "accessibility"
    const val USAGE_ACCESS = "usage"
    const val SHORT_FORM = "shorts"
}

private data class Disclosure(
    val title: String,
    val heading: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val does: String,
    val usedFor: String,
    val doesNot: String,
    val turnOff: String,
    val restricted: Boolean,
)

private fun disclosureFor(type: String): Disclosure = when (type) {
    DisclosureType.USAGE_ACCESS -> Disclosure(
        title = "Usage access",
        heading = "Usage access",
        icon = Icons.Filled.QueryStats,
        does = "It lets an app read the device's app-usage statistics: how long each app " +
            "was in the foreground.",
        usedFor = "Measuring how long you used the apps you set a daily limit for, and " +
            "showing your own screen time on the home screen.",
        doesNot = "Your usage data never leaves the device. It is not collected, shared, or " +
            "used for anything except the limits and numbers you see in the app.",
        turnOff = "Any time, in Android Settings > Special app access > Usage access > " +
            "FocusForce+. Daily limits stop counting; everything else keeps working.",
        restricted = false,
    )
    DisclosureType.SHORT_FORM -> Disclosure(
        title = "Short videos",
        heading = "A separate service for Reels and Shorts",
        icon = Icons.Filled.SmartDisplay,
        does = "It lets an app see what is on the screen of other apps and press Back. " +
            "This one is limited to Instagram and YouTube: no other app is visible to it.",
        usedFor = "Noticing the Reels or Shorts player when it opens, and pressing Back " +
            "once, so you land on the rest of the app. Only for what you switched off.",
        doesNot = "It does not read your messages, posts, comments or anything else: it " +
            "only checks whether the Reels or Shorts player is showing. It stores nothing " +
            "and sends nothing, and FocusForce+ has no internet access. It is separate " +
            "from the app blocker, which never looks at screen content at all.",
        turnOff = "Switch Reels and Shorts back on in the blocker, or turn off \"FocusForce+ " +
            "Short videos\" in Android Settings > Accessibility. Nothing else changes.",
        restricted = true,
    )
    else -> Disclosure(
        title = "Blocking service",
        heading = "The blocking service",
        icon = Icons.Filled.Accessibility,
        does = "It lets an app receive a signal from Android whenever a different app " +
            "comes to the foreground.",
        usedFor = "Exactly one thing: noticing that you opened an app you chose to block, " +
            "so the blocking screen can appear on top of it.",
        doesNot = "It cannot and does not read your screen; content access is switched off " +
            "in the service's configuration. It does not collect, store or send anything " +
            "about other apps. There is no analytics and no internet access in this app.",
        turnOff = "Any time, in Android Settings > Accessibility > FocusForce+ App Blocker. " +
            "Your blocks stay saved; blocking simply pauses.",
        restricted = true,
    )
}

/**
 * Shown before the user is sent to the system setting for a permission
 * (compliance guide 3.4, Golden Rule #10): what it does, what FocusForce+ uses it
 * for, what it does not do, how to turn it off, and an explicit button to go on.
 */
@Composable
fun DisclosureScreen(
    type: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = FfTheme.colors
    val content = disclosureFor(type)

    fun isGranted(): Boolean = when (type) {
        DisclosureType.USAGE_ACCESS -> PermissionHelper.hasUsageStatsPermission(context)
        DisclosureType.SHORT_FORM -> PermissionHelper.isShortFormServiceEnabled(context)
        else -> PermissionHelper.isAccessibilityServiceEnabled(context)
    }

    var granted by remember { mutableStateOf(isGranted()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = isGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PageScaffold(title = content.title, onBack = onNavigateBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconCircle(icon = content.icon, size = 52.dp)
            Text(content.heading, style = MaterialTheme.typography.headlineSmall, color = colors.text)
            Spacer(Modifier.height(4.dp))
            Section("What this permission does", content.does)
            Section("What FocusForce+ uses it for", content.usedFor)
            Section("What FocusForce+ does not do", content.doesNot)
            Section("How to turn it off again", content.turnOff)
            if (content.restricted && !granted) {
                FfPaddedCard(spacing = 6.dp) {
                    Text("Seeing \"Restricted setting\"?", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text(
                        "Because FocusForce+ was installed directly and not from an app store, " +
                            "Android blocks this switch the first time. Open the app info, tap " +
                            "the three-dot menu at the top right (on Samsung: Permissions first) " +
                            "and choose \"Allow restricted settings\". Then come back here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    TextAction(
                        "Open app info",
                        onClick = { PermissionHelper.openAppDetailsSettings(context) },
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (granted) {
                FfCard {
                    ListRow(
                        title = "Turned on",
                        subtitle = "You are all set.",
                        leading = { IconCircle(icon = Icons.Filled.CheckCircle, tint = colors.success) },
                    )
                }
                PrimaryButton(text = "Done", onClick = onNavigateBack, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    text = "Got it, open Android settings",
                    onClick = {
                        if (type == DisclosureType.USAGE_ACCESS) PermissionHelper.openUsageAccessSettings(context)
                        else PermissionHelper.openAccessibilitySettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlineButton(text = "Not now", onClick = onNavigateBack, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    val colors = FfTheme.colors
    FfPaddedCard(spacing = 4.dp) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}
