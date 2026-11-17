package com.focusforceplus.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.R
import com.focusforceplus.app.ui.theme.FfDimens
import com.focusforceplus.app.ui.theme.FfTheme
import kotlinx.coroutines.launch

// ── Logo ──────────────────────────────────────────────────────────────────────

/** The FocusForce+ mark, tinted. Vector, so it is sharp at any size. */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = FfTheme.colors.brand,
) {
    Icon(
        painter = painterResource(R.drawable.ic_logo_mark),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

// ── Tabs ──────────────────────────────────────────────────────────────────────

data class TabItem(val label: String, val count: Int? = null)

/** Text tabs with a short underline, sitting on a hairline. */
@Composable
fun UnderlineTabs(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FfTheme.colors
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tabWidth = maxWidth / tabs.size.coerceAtLeast(1)
        val indicatorWidth = tabWidth * 0.5f
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex + (tabWidth - indicatorWidth) / 2,
            animationSpec = tween(180),
            label = "tab_indicator",
        )
        Column {
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable { onSelect(index) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) colors.text else colors.textMuted,
                        )
                        if (tab.count != null) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = tab.count.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textFaint,
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                RowDivider()
                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(indicatorWidth)
                        .height(2.dp)
                        .background(colors.accent),
                )
            }
        }
    }
}

// ── Form scaffold ─────────────────────────────────────────────────────────────

/**
 * Frame for every create/edit screen.
 *
 * Two things it guarantees: the primary action stays visible at the bottom no
 * matter how far the form is scrolled (and rides above the keyboard), and when
 * there is more form below the fold, a small arrow says so. Both were easy to
 * miss when Save sat at the end of a long scrolling column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScaffold(
    title: String,
    onClose: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    scrollState: ScrollState = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    secondaryAction: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FfTheme.colors
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textSecondary,
                        )
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                    actionIconContentColor = colors.textMuted,
                ),
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .background(colors.background)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FfDimens.screenPadding, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (secondaryAction != null) secondaryAction()
                    PrimaryButton(
                        text = primaryLabel,
                        onClick = onPrimary,
                        modifier = Modifier.weight(1f),
                        enabled = primaryEnabled,
                        loading = primaryLoading,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = FfDimens.screenPadding)
                    .padding(top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
            ScrollHint(
                visible = scrollState.canScrollForward,
                onClick = {
                    scope.launch {
                        scrollState.animateScrollTo(
                            (scrollState.value + scrollState.viewportSize * 0.7f).toInt()
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

/**
 * "There is more below": a small round arrow that nudges up and down while the
 * content can still scroll, and scrolls on tap.
 */
@Composable
fun ScrollHint(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FfTheme.colors
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        val nudge = rememberInfiniteTransition(label = "scroll_hint")
        val shift by nudge.animateFloat(
            initialValue = 0f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "scroll_hint_shift",
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surfaceHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Scroll down for more",
                tint = colors.text,
                modifier = Modifier
                    .size(22.dp)
                    .offset(y = shift.dp),
            )
        }
    }
}

// ── Form pieces ───────────────────────────────────────────────────────────────

/** A titled block of a form: label on top, a card with the controls below. */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = FfTheme.colors.text,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        content()
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = FfTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** A setting row with a switch on the right. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    position: RowPosition = RowPosition.InCard,
    leading: (@Composable () -> Unit)? = null,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        position = position,
        titleColor = if (enabled) FfTheme.colors.text else FfTheme.colors.textMuted,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        leading = leading,
        trailing = { FfSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** A row that shows a current value and opens something to change it. */
@Composable
fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    position: RowPosition = RowPosition.InCard,
    leading: (@Composable () -> Unit)? = null,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        position = position,
        onClick = if (enabled) onClick else null,
        leading = leading,
        trailing = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) FfTheme.colors.accent else FfTheme.colors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
) {
    val colors = FfTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        titleContentColor = colors.text,
        textContentColor = colors.textSecondary,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) colors.error else colors.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    dismissLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
            }
        },
    )
}

/**
 * Asked before anything is deleted. Things that go to the trash say so, which is
 * the difference between "are you sure" as a reflex and as information.
 */
@Composable
fun DeleteDialog(
    what: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    goesToTrash: Boolean = true,
    retentionDays: Int = 30,
    extra: String? = null,
) {
    val trashNote = if (goesToTrash) {
        "It moves to the trash and stays there for $retentionDays days, so you can bring it back."
    } else {
        "This cannot be undone."
    }
    ConfirmDialog(
        title = "Delete $what?",
        message = listOfNotNull(trashNote, extra).joinToString("\n\n"),
        confirmLabel = if (goesToTrash) "Move to trash" else "Delete",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = FfTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textFaint, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        if (text != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            TonalButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Standard padding for the scrolling content of a main tab. */
val ScreenContentPadding = PaddingValues(
    start = FfDimens.screenPadding,
    end = FfDimens.screenPadding,
    top = 6.dp,
    bottom = 24.dp,
)

/**
 * A plain sub-page: a back arrow and the title on top, then whatever the page
 * shows. Settings pages and the trash use it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = FfTheme.colors
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textSecondary,
                        )
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                    actionIconContentColor = colors.textMuted,
                ),
            )
        },
        content = content,
    )
}
