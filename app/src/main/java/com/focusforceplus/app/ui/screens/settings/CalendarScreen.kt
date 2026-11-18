package com.focusforceplus.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.calendar.CalendarInfo
import com.focusforceplus.app.data.calendar.CalendarRepository
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.ui.components.Choice
import com.focusforceplus.app.ui.components.ChoiceDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.screens.blocker.DisclosureType
import com.focusforceplus.app.ui.theme.FfTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarUiState(
    val loading: Boolean = true,
    val access: Boolean = false,
    val calendars: List<CalendarInfo> = emptyList(),
    /** null = every calendar that is switched on. */
    val selectedIds: Set<Long>? = null,
    val onHome: Boolean = true,
    val todoTarget: Long? = null,
    val blockGroups: Set<String> = emptySet(),
    val groupNames: List<String> = emptyList(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val settings: SettingsRepository,
    blockerRepository: BlockerRepository,
) : ViewModel() {

    private val access = MutableStateFlow(calendar.canWrite())
    private val calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())

    private val prefs = combine(
        settings.calendarIds,
        settings.calendarOnHome,
        settings.calendarTodoTarget,
        settings.calendarBlockGroups,
    ) { ids, onHome, target, groups -> CalendarUiState(selectedIds = ids, onHome = onHome, todoTarget = target, blockGroups = groups) }

    val uiState: StateFlow<CalendarUiState> = combine(
        access,
        calendars,
        prefs,
        blockerRepository.getAllGroups().map { groups -> groups.map { it.name } },
    ) { access, calendars, prefs, groupNames ->
        prefs.copy(loading = false, access = access, calendars = calendars, groupNames = groupNames)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** After coming back from the permission or Android settings. */
    fun refresh() {
        access.value = calendar.canWrite()
        viewModelScope.launch { calendars.value = calendar.calendars() }
    }

    fun setOnHome(on: Boolean) = viewModelScope.launch { settings.saveCalendarOnHome(on) }

    fun setIds(ids: Set<Long>?) = viewModelScope.launch { settings.saveCalendarIds(ids) }

    fun setTodoTarget(id: Long?) = viewModelScope.launch { settings.saveCalendarTodoTarget(id) }

    fun toggleBlockGroup(name: String) = viewModelScope.launch {
        val now = uiState.value.blockGroups
        settings.saveCalendarBlockGroups(if (name in now) now - name else now + name)
    }
}

/**
 * The calendar: today's events on Home, todos with a date as events in a calendar
 * the user picks, and block groups that block while an event is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    onNavigateBack: () -> Unit,
    onOpenDisclosure: (String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    var pickingCalendars by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PageScaffold(title = "Calendar", onBack = onNavigateBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (!s.access) {
                FfPaddedCard(spacing = 8.dp) {
                    Text("Your calendar, next to your day", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text(
                        "FocusForce+ can show today's events on Home, put todos with a date into a " +
                            "calendar you pick, and block apps while an event is on. All of it stays on " +
                            "this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    PrimaryButton(
                        text = "Allow calendar access",
                        onClick = { onOpenDisclosure(DisclosureType.CALENDAR) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                return@Column
            }

            val shown = s.selectedIds?.let { ids -> s.calendars.filter { it.id in ids } } ?: s.calendars
            FormSection(title = "Calendars", hint = "The calendars FocusForce+ looks at, for Home and for blocking.") {
                FfCard {
                    ValueRow(
                        title = "Calendars",
                        value = when {
                            s.calendars.isEmpty() -> "None found"
                            s.selectedIds == null -> "All (${s.calendars.size})"
                            else -> "${shown.size} of ${s.calendars.size}"
                        },
                        onClick = { pickingCalendars = true },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Today's events on Home",
                        subtitle = "What is still ahead today, under your routines.",
                        checked = s.onHome,
                        onCheckedChange = { viewModel.setOnHome(it) },
                    )
                }
            }

            val writable = s.calendars.filter { it.writable }
            FormSection(
                title = "Todos in the calendar",
                hint = "A todo with a date becomes a half-hour event that shows you as free. When it is " +
                    "done or deleted, it leaves the calendar again. A calendar that syncs with an account " +
                    "sends these events there like any other.",
            ) {
                FfCard {
                    ValueRow(
                        title = "Put todos with a date into",
                        value = s.todoTarget?.let { id -> s.calendars.firstOrNull { it.id == id }?.name ?: "A calendar that is gone" } ?: "Off",
                        onClick = { pickingTarget = true },
                    )
                }
            }

            FormSection(
                title = "Blocking during events",
                hint = "While an event that shows you as busy is on, the groups you pick are blocked. " +
                    "All-day events and events you declined do not count.",
            ) {
                if (s.groupNames.isEmpty()) {
                    Text(
                        "Make a block group in the blocker first; then pick it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        s.groupNames.forEach { name ->
                            FfChip(label = name, selected = name in s.blockGroups, onClick = { viewModel.toggleBlockGroup(name) })
                        }
                    }
                }
            }

            Text(
                "To take calendar access away again: Android Settings > Apps > FocusForce+ > " +
                    "Permissions > Calendar.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }

    if (pickingCalendars) {
        CalendarPickerDialog(
            calendars = s.calendars,
            selected = s.selectedIds ?: s.calendars.map { it.id }.toSet(),
            onDone = { picked ->
                viewModel.setIds(if (picked.size == s.calendars.size) null else picked)
                pickingCalendars = false
            },
            onDismiss = { pickingCalendars = false },
        )
    }
    if (pickingTarget) {
        val writable = s.calendars.filter { it.writable }
        ChoiceDialog(
            title = "Put todos with a date into",
            choices = listOf(Choice<Long?>(null, "Off")) + writable.map { Choice<Long?>(it.id, it.name, it.account.ifBlank { null }) },
            selected = s.todoTarget,
            onSelect = {
                viewModel.setTodoTarget(it)
                pickingTarget = false
            },
            onDismiss = { pickingTarget = false },
        )
    }
}

@Composable
private fun CalendarPickerDialog(
    calendars: List<CalendarInfo>,
    selected: Set<Long>,
    onDone: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var picked by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text("Calendars", style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                calendars.forEach { cal ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = cal.id in picked,
                            onCheckedChange = { on -> picked = if (on) picked + cal.id else picked - cal.id },
                            colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.outline),
                        )
                        Box(Modifier.size(10.dp).background(Color(cal.color), CircleShape))
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(cal.name, style = MaterialTheme.typography.bodyLarge, color = colors.text)
                            if (cal.account.isNotBlank()) {
                                Text(cal.account, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(picked) }, enabled = picked.isNotEmpty()) {
                Text("Done", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}
