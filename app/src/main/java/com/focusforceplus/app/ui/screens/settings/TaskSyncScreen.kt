package com.focusforceplus.app.ui.screens.settings

import com.focusforceplus.app.ui.components.verticalScrollWithHint
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TaskSyncTarget
import com.focusforceplus.app.data.tasks.TaskApp
import com.focusforceplus.app.data.tasks.TaskListInfo
import com.focusforceplus.app.data.tasks.TaskStore
import com.focusforceplus.app.data.tasks.TaskSync
import com.focusforceplus.app.data.tasks.TaskSyncStatus
import com.focusforceplus.app.ui.components.Choice
import com.focusforceplus.app.ui.components.ChoiceDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One tasks app on this phone, and what FocusForce+ may do with it. */
data class TaskAppState(val app: TaskApp, val granted: Boolean, val lists: List<TaskListInfo>)

data class TaskSyncUiState(
    val apps: List<TaskAppState> = emptyList(),
    val target: TaskSyncTarget? = null,
    val status: TaskSyncStatus = TaskSyncStatus(),
    val syncing: Boolean = false,
) {
    val targetApp: TaskApp? get() = TaskApp.of(target?.authority)
    val targetList: TaskListInfo?
        get() = apps.firstOrNull { it.app == targetApp }?.lists?.firstOrNull { it.id == target?.listId }
}

@HiltViewModel
class TaskSyncViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sync: TaskSync,
    settings: SettingsRepository,
) : ViewModel() {

    private val apps = MutableStateFlow<List<TaskAppState>>(emptyList())
    private val syncing = MutableStateFlow(false)

    val uiState: StateFlow<TaskSyncUiState> = combine(apps, settings.taskSyncTarget, sync.status, syncing) { apps, target, status, busy ->
        TaskSyncUiState(apps, target, status, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskSyncUiState())

    /** After coming back from the permission dialog or another app. */
    fun refresh() {
        viewModelScope.launch {
            apps.value = withContext(Dispatchers.IO) {
                TaskApp.entries.filter { it.isInstalled(context) }.map { app ->
                    val granted = app.isGranted(context)
                    TaskAppState(app, granted, if (granted) TaskStore(context, app).lists().orEmpty() else emptyList())
                }
            }
        }
    }

    fun connect(app: TaskApp, list: TaskListInfo) = run { sync.connect(TaskSyncTarget(app.authority, list.id)) }

    fun disconnect() = run { sync.disconnect() }

    fun syncNow() = run { sync.syncNow() }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            syncing.value = true
            runCatching { block() }
            syncing.value = false
        }
    }
}

/**
 * Two-way sync with a list in OpenTasks or Tasks.org. FocusForce+ has no internet
 * access; a tasks app with DAVx5 is the way to get todos to other devices.
 */
@Composable
fun TaskSyncScreen(
    onNavigateBack: () -> Unit,
    onOpenDisclosure: (String) -> Unit,
    viewModel: TaskSyncViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    var picking by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PageScaffold(title = "Tasks app", onBack = onNavigateBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScrollWithHint()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            FfPaddedCard(spacing = 6.dp) {
                Text("Your todos, also in a tasks app", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(
                    "FocusForce+ keeps one list of OpenTasks or Tasks.org in step with your todos, both " +
                        "ways: titles, dates, priority, notes and done. With DAVx5, that list syncs with " +
                        "Nextcloud and other CalDAV servers, so your todos reach your other devices. " +
                        "FocusForce+ itself never goes online.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            if (s.apps.isEmpty()) {
                FormSection(title = "No tasks app found") {
                    Text(
                        "Install OpenTasks or Tasks.org (both are free, on F-Droid and Google Play), then come back here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
                return@Column
            }

            val target = s.target
            if (target == null) {
                FormSection(title = "Sync with", hint = "Pick a list. Its open tasks come over as todos, and your open todos go there.") {
                    FfCard {
                        s.apps.forEachIndexed { i, state ->
                            if (i > 0) RowDivider()
                            if (!state.granted) {
                                ListRow(
                                    title = state.app.label,
                                    subtitle = "Allow access first",
                                    subtitleColor = colors.accent,
                                    onClick = { onOpenDisclosure(state.app.disclosureType) },
                                )
                            } else if (state.lists.isEmpty()) {
                                ListRow(title = state.app.label, subtitle = "No lists there yet. Make one in ${state.app.label}.")
                            } else {
                                ListRow(title = state.app.label, subtitle = "Pick a list", subtitleColor = colors.accent, onClick = { picking = true })
                            }
                        }
                    }
                }
            } else {
                FormSection(title = "Syncing") {
                    FfCard {
                        ValueRow(
                            title = s.targetApp?.label ?: "Tasks app",
                            value = s.targetList?.name ?: "A list that is gone",
                            onClick = { picking = true },
                        )
                        RowDivider()
                        ListRow(
                            title = when {
                                s.syncing -> "Syncing..."
                                s.status.at == null -> "Not synced yet"
                                else -> "Last sync " + TimeFormat.dayAndClock(s.status.at!!)
                            },
                            subtitle = s.status.message,
                            subtitleColor = if (s.status.ok) colors.textMuted else colors.warning,
                            subtitleMaxLines = 4,
                        )
                    }
                    PrimaryButton(
                        text = "Sync now",
                        onClick = viewModel::syncNow,
                        loading = s.syncing,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
                FormSection(
                    title = "What happens",
                    hint = "Done here is done there, and the other way round. A todo you delete goes " +
                        "from the list too; a task deleted there goes to the trash here, so you can bring it " +
                        "back. When both sides changed the same todo, the change made here wins. Checklists, " +
                        "snoozes and routines stay in FocusForce+.",
                ) {}
                TextAction("Stop syncing", onClick = viewModel::disconnect, color = colors.error)
            }
        }
    }

    if (picking) {
        val choices = s.apps.filter { it.granted }.flatMap { state ->
            state.lists.map { list -> Choice(state.app to list, list.name, "${state.app.label}" + if (list.account.isNotBlank()) " · ${list.account}" else "") }
        }
        val current = s.apps.firstOrNull { it.app == s.targetApp }?.let { st -> st.lists.firstOrNull { it.id == s.target?.listId }?.let { st.app to it } }
        ChoiceDialog(
            title = "Sync with",
            choices = choices,
            selected = current ?: choices.firstOrNull()?.value ?: return,
            onSelect = { (app, list) ->
                picking = false
                viewModel.connect(app, list)
            },
            onDismiss = { picking = false },
        )
    }
}
