package com.focusforceplus.app.ui.screens.onboarding

import com.focusforceplus.app.ui.theme.ffBackdrop
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.LogoMark
import com.focusforceplus.app.ui.components.OutlineButton
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.navigation.AppModule
import com.focusforceplus.app.ui.navigation.Dock
import com.focusforceplus.app.ui.navigation.NavEditor
import com.focusforceplus.app.ui.navigation.NavViewModel
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.PermissionHelper
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Link
import com.focusforceplus.app.ui.theme.ThemePresets
import com.focusforceplus.app.ui.navigation.SettingsRoutes
import com.focusforceplus.app.ui.components.verticalScrollWithHint
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 9

/**
 * First launch, in the order someone new needs it: what the app is and what stays
 * private, why each permission is asked for and a place to grant them, then making
 * it one's own (the look, the bar), then what is there for later (the optional
 * links to other apps, how updates arrive), a word of thanks, and an optional
 * first routine.
 *
 * Accessibility and Usage Access deliberately route through the prominent
 * disclosure screen instead of straight to system settings (Golden Rule #10).
 */
@Composable
fun OnboardingScreen(
    onFinish: (createFirstRoutine: Boolean) -> Unit,
    onOpenDisclosure: (String) -> Unit,
    /** Opens one of the settings pages for an optional extra; Back returns here. */
    onOpenPage: (String) -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
    navViewModel: NavViewModel = hiltViewModel(),
) {
    val colors = FfTheme.colors
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    fun next() {
        scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(PAGE_COUNT - 1)) }
    }

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
            .ffBackdrop(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> WelcomePage(onContinue = ::next)
                1 -> PermissionsExplainedPage(onContinue = ::next)
                2 -> PermissionChecklistPage(viewModel, onOpenDisclosure, onContinue = ::next)
                3 -> LookPage(onContinue = ::next)
                4 -> NavBarPage(navViewModel, onContinue = ::next)
                5 -> ExtrasPage(onOpenPage = onOpenPage, onContinue = ::next)
                6 -> UpdatesPage(onContinue = ::next)
                7 -> ThanksPage(onContinue = ::next)
                else -> FinishPage(
                    navViewModel = navViewModel,
                    onCreateRoutine = { viewModel.completeOnboarding { onFinish(true) } },
                    onLater = { viewModel.completeOnboarding { onFinish(false) } },
                )
            }
        }

        // Where you are
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(PAGE_COUNT) { index ->
                val current = pagerState.currentPage == index
                val width by animateDpAsState(if (current) 18.dp else 6.dp, label = "dot")
                val color by animateColorAsState(if (current) colors.accent else colors.surfaceHigh, label = "dot")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

/**
 * One page: a title and a line under it, content that scrolls, and the main
 * button pinned to the bottom so it is never below the fold.
 */
@Composable
private fun OnboardingPage(
    title: String,
    body: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = FfTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollWithHint()
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 20.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = if (centered) Arrangement.Center else Arrangement.Top,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                )
            }
            Spacer(Modifier.height(24.dp))
            content()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(text = primaryLabel, onClick = onPrimary, modifier = Modifier.fillMaxWidth())
            if (secondaryLabel != null && onSecondary != null) {
                OutlineButton(text = secondaryLabel, onClick = onSecondary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, content: @Composable ColumnScope.() -> Unit = {}) {
    val colors = FfTheme.colors
    FfPaddedCard(spacing = 4.dp) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        content()
    }
}

// ── Welcome ───────────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(onContinue: () -> Unit) {
    val colors = FfTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoMark(size = 72.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                "FocusForce+",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Routines, todos, an app blocker and focus sessions in one app, made to be " +
                    "hard to ignore.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCard(
                    title = "Everything stays on your phone",
                    body = "No account, no cloud, no tracking, no ads. The app has no internet " +
                        "access at all.",
                )
                InfoCard(
                    title = "An honest note",
                    body = "FocusForce+ is a self-help tool, not a medical app. It does not " +
                        "diagnose or treat ADHD or anything else, and it is no substitute for " +
                        "professional care. If you are struggling, please talk to a doctor or " +
                        "a therapist.",
                )
            }
        }
        PrimaryButton(
            text = "Get started",
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
    }
}

// ── Why permissions ───────────────────────────────────────────────────────────

@Composable
private fun PermissionsExplainedPage(onContinue: () -> Unit) {
    OnboardingPage(
        title = "Why it asks for permissions",
        body = "Each permission powers one feature. All of them are optional: skip one and " +
            "that feature simply stays off.",
        primaryLabel = "Set them up",
        onPrimary = onContinue,
    ) {
        val rows = listOf(
            Triple(Icons.Filled.Notifications, "Notifications", "Alarms and reminders for routines and todos."),
            Triple(Icons.Filled.AccessTime, "Exact alarms", "So reminders go off on the minute."),
            Triple(Icons.Filled.Layers, "Display over other apps", "Shows the alarm screen while you are in another app."),
            Triple(
                Icons.Filled.Accessibility, "Accessibility service",
                "Notices which app is opened, so blocked apps can be covered. Reads no screen content.",
            ),
            Triple(Icons.Filled.QueryStats, "Usage access", "Counts app time for daily limits and your stats."),
            Triple(Icons.Filled.BatteryChargingFull, "Battery: unrestricted", "Keeps reminders reliable on strict battery savers."),
            Triple(Icons.Filled.DoNotDisturbOn, "Do Not Disturb access", "Lets focus and sleep silence interruptions."),
        )
        FfCard {
            rows.forEachIndexed { index, (icon, title, detail) ->
                if (index > 0) RowDivider()
                ListRow(
                    title = title,
                    subtitle = detail,
                    subtitleMaxLines = 3,
                    leading = { IconCircle(icon = icon, size = 34.dp) },
                )
            }
        }
    }
}

// ── Grant ─────────────────────────────────────────────────────────────────────

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

    OnboardingPage(
        title = "Grant permissions",
        body = "Tap Grant for the ones you want. All of them can be changed later in Settings.",
        primaryLabel = "Continue",
        onPrimary = onContinue,
    ) {
        FfCard {
            GrantRow("Notifications", perms.notifications) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            RowDivider()
            GrantRow("Exact alarms", perms.exactAlarms) { PermissionHelper.openExactAlarmSettings(context) }
            RowDivider()
            GrantRow("Display over other apps", perms.overlay) { PermissionHelper.openOverlaySettings(context) }
            // Only offered where the phone actually has the screen for it. Several
            // OEMs (Samsung One UI) ship no full-screen-notification toggle at all,
            // and a row that cannot be ticked reads as a broken app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                (perms.fullScreenIntent || PermissionHelper.hasFullScreenIntentSettingsScreen(context))
            ) {
                RowDivider()
                GrantRow("Full-screen alarms", perms.fullScreenIntent) {
                    PermissionHelper.openFullScreenIntentSettings(context)
                }
            }
            RowDivider()
            GrantRow("Accessibility (app blocker)", perms.accessibility) { onOpenDisclosure("accessibility") }
            RowDivider()
            GrantRow("Usage access (daily limits)", perms.usageStats) { onOpenDisclosure("usage") }
            RowDivider()
            GrantRow("Battery: unrestricted", perms.batteryUnrestricted) {
                PermissionHelper.openBatteryOptimizationSettings(context)
            }
            RowDivider()
            GrantRow("Do Not Disturb access", perms.dndAccess) { PermissionHelper.openDndAccessSettings(context) }
        }
    }
}

@Composable
private fun GrantRow(title: String, granted: Boolean, onGrant: () -> Unit) {
    val colors = FfTheme.colors
    ListRow(
        title = title,
        subtitle = if (granted) "Granted" else null,
        subtitleColor = colors.success,
        onClick = if (granted) null else onGrant,
        trailing = {
            if (granted) {
                Icon(Icons.Filled.Check, contentDescription = "Granted", tint = colors.success)
            } else {
                TextAction("Grant", onClick = onGrant)
            }
        },
    )
}

// ── The look ──────────────────────────────────────────────────────────────────

@Composable
private fun LookPage(
    onContinue: () -> Unit,
    appearance: com.focusforceplus.app.ui.screens.settings.AppearanceViewModel = hiltViewModel(),
) {
    val colors = FfTheme.colors
    val current by appearance.spec.collectAsState()
    OnboardingPage(
        title = "Pick a look",
        body = "Tap one to try it; it changes right away. Not final: Settings > Theme " +
            "has these again, and lets you mix your own colours.",
        primaryLabel = "Continue",
        onPrimary = onContinue,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemePresets.ALL.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { theme ->
                        val selected = current.name == theme.name
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(theme.background)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) colors.accent else colors.outline,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .clickable(role = Role.RadioButton) { appearance.use(theme) }
                                .padding(14.dp),
                        ) {
                            // A small picture of the theme itself: its card, its
                            // button colour and its text, on its own background.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.surface)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(theme.primary))
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .height(6.dp)
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(theme.text.copy(alpha = 0.55f)),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    theme.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = theme.text,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = theme.accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Optional extras ───────────────────────────────────────────────────────────

/**
 * The three links to other apps. Nobody needs them to use the app, so nothing is
 * asked for here: each card says in a line what it is for and leads to its own
 * page, which explains it before any permission comes up.
 */
@Composable
private fun ExtrasPage(onOpenPage: (String) -> Unit, onContinue: () -> Unit) {
    val colors = FfTheme.colors
    OnboardingPage(
        title = "Extras, if you want them",
        body = "You can skip this page. FocusForce+ works fully without these three. They " +
            "connect it to other apps and are there for when you miss them.",
        primaryLabel = "Continue",
        onPrimary = onContinue,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExtraCard(
                icon = Icons.Filled.CalendarMonth,
                title = "Your calendar",
                body = "Today's events on Home, and todos with a date in a calendar you pick.",
                action = "Set up",
                onAction = { onOpenPage(SettingsRoutes.CALENDAR) },
            )
            ExtraCard(
                icon = Icons.Filled.Sync,
                title = "A tasks app",
                body = "Keeps your todos in step with one list in OpenTasks or Tasks.org, for " +
                    "people who sync their tasks with a server of their own.",
                action = "Set up",
                onAction = { onOpenPage(SettingsRoutes.TASK_SYNC) },
            )
            ExtraCard(
                icon = Icons.Filled.Link,
                title = "Start links",
                body = "Let automation apps, shortcuts or NFC tags start a focus session or " +
                    "add a todo. Off until you switch it on.",
                action = "Have a look",
                onAction = { onOpenPage(SettingsRoutes.INTEGRATIONS) },
            )
            Text(
                "Later you find all three under Settings > Connections.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ExtraCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    FfCard {
        ListRow(
            title = title,
            subtitle = body,
            subtitleMaxLines = 4,
            onClick = onAction,
            leading = { IconCircle(icon = icon, size = 34.dp) },
            trailing = { TextAction(action, onClick = onAction) },
        )
    }
}

// ── Updates ───────────────────────────────────────────────────────────────────

@Composable
private fun UpdatesPage(onContinue: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    OnboardingPage(
        title = "Keeping it up to date",
        body = "FocusForce+ is not in an app store yet, and without internet access it " +
            "cannot look for updates itself. Two ways to stay current:",
        primaryLabel = "Continue",
        onPrimary = onContinue,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard(
                title = "Check now and then",
                body = "Settings > About > Check for updates opens the releases page, so you " +
                    "can see whether there is a newer version.",
            )
            InfoCard(
                title = "Or let Obtainium do it",
                body = "Obtainium watches GitHub releases and updates apps you installed " +
                    "directly. While FocusForce+ is in beta, switch on \"Include prereleases\" " +
                    "for it. FocusForce+ itself still never goes online.",
            ) {
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    text = "Get Obtainium",
                    onClick = { uriHandler.openUri("https://github.com/ImranR98/Obtainium") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Thanks ────────────────────────────────────────────────────────────────────

@Composable
private fun ThanksPage(onContinue: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    OnboardingPage(
        title = "Thanks for trying it",
        body = "I built FocusForce+ for myself, because nothing I tried was stubborn enough " +
            "to get me going. Putting it on GitHub came later. If it helps you too, that is " +
            "the best outcome I could ask for.",
        primaryLabel = "Continue",
        onPrimary = onContinue,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard(
                title = "A star takes two seconds",
                body = "The app collects nothing and phones home to nobody, so a star on " +
                    "GitHub is the only way I see that anyone uses it.",
            )
            InfoCard(
                title = "Tell me what breaks, or what doesn't",
                body = "Bugs, rough edges, ideas, anything that annoyed you. Without " +
                    "analytics there is no other way for me to know.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(
                    text = "Star on GitHub",
                    onClick = { uriHandler.openUri("https://github.com/karOS555/FocusForcePlus") },
                    modifier = Modifier.weight(1f),
                )
                OutlineButton(
                    text = "Feedback",
                    onClick = {
                        uriHandler.openUri("https://github.com/karOS555/FocusForcePlus/issues/new/choose")
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── The bar ───────────────────────────────────────────────────────────────────

@Composable
private fun NavBarPage(navViewModel: NavViewModel, onContinue: () -> Unit) {
    val colors = FfTheme.colors
    val context = LocalContext.current
    val config by navViewModel.config.collectAsState()
    val message by navViewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            navViewModel.clearMessage()
        }
    }

    OnboardingPage(
        title = "Make the bar yours",
        body = "Choose what goes in the bar at the bottom of the screen, and drag the " +
            "handles to put it in your order.",
        primaryLabel = "Continue",
        onPrimary = onContinue,
    ) {
        val current = config ?: return@OnboardingPage
        Text("Your bar", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        // The real bar, so what you arrange is exactly what you will get, Edit
        // entry included.
        Dock(
            entries = current.dock,
            currentRoute = AppModule.HOME.route,
            onSelect = {},
            onEdit = {},
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, colors.outline, RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Not final. Tap Edit at the end of the bar, or press and hold the bar, " +
                    "to change this whenever you like.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(20.dp))
        NavEditor(
            config = current,
            onMove = navViewModel::move,
            onSetShown = navViewModel::setShown,
            onSetModuleOn = navViewModel::setModuleOn,
        )
    }
}

// ── Finish ────────────────────────────────────────────────────────────────────

@Composable
private fun FinishPage(
    navViewModel: NavViewModel,
    onCreateRoutine: () -> Unit,
    onLater: () -> Unit,
) {
    val config by navViewModel.config.collectAsState()
    val routinesOn = config?.isOn(AppModule.ROUTINES) ?: true
    if (routinesOn) {
        OnboardingPage(
            title = "Start with a routine?",
            body = "A routine is a few steps with a timer each, started by an alarm that does " +
                "not let you quietly skip it. Mornings and evenings are a good place to begin.",
            primaryLabel = "Create a routine",
            onPrimary = onCreateRoutine,
            secondaryLabel = "Not now",
            onSecondary = onLater,
            centered = true,
        )
    } else {
        OnboardingPage(
            title = "All set",
            body = "What you kept is in the bar. Everything else is in Settings.",
            primaryLabel = "Start",
            onPrimary = onLater,
            centered = true,
        )
    }
}
