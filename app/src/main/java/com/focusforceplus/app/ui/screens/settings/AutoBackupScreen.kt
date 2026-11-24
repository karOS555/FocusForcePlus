package com.focusforceplus.app.ui.screens.settings

import com.focusforceplus.app.ui.components.verticalScrollWithHint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.ui.components.Choice
import com.focusforceplus.app.ui.components.ChoiceDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.AutoBackup
import com.focusforceplus.app.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AutoBackupUiState(
    val folderName: String? = null,
    val on: Boolean = false,
    val keep: Int = SettingsRepository.DEFAULT_AUTO_BACKUP_KEEP,
    val lastAt: Long? = null,
    val lastError: String? = null,
    val running: Boolean = false,
)

@HiltViewModel
class AutoBackupViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val autoBackup: AutoBackup,
) : ViewModel() {

    private val running = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    val state: StateFlow<AutoBackupUiState> = combine(
        settings.autoBackup.map { it to withContext(Dispatchers.IO) { autoBackup.folderName(it.folderUri) } },
        running,
    ) { (backup, name), isRunning ->
        AutoBackupUiState(
            folderName = name ?: backup.folderUri?.let { "Chosen folder" },
            on = backup.folderUri != null,
            keep = backup.keep,
            lastAt = backup.lastAt,
            lastError = backup.lastError,
            running = isRunning,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupUiState())

    fun useFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching { autoBackup.useFolder(uri) }
                .onSuccess { backUpNow() }
                .onFailure { message.value = "That folder cannot be used. Try another one." }
        }
    }

    fun setKeep(keep: Int) {
        viewModelScope.launch { settings.saveAutoBackupKeep(keep) }
    }

    fun backUpNow() {
        if (running.value) return
        viewModelScope.launch {
            running.value = true
            val error = autoBackup.runNow()
            running.value = false
            message.value = error ?: "Backup saved."
        }
    }

    fun turnOff() {
        viewModelScope.launch {
            autoBackup.turnOff()
            message.value = "Automatic backups are off. Files already saved stay where they are."
        }
    }
}

/** Nightly backups into a folder of the user's choice. */
@Composable
fun AutoBackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: AutoBackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var pickKeep by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.useFolder(uri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.message.value = null
        }
    }

    PageScaffold(title = "Automatic backup", onBack = onNavigateBack, snackbarHostState = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScrollWithHint()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            FormSection(
                title = "Every night",
                hint = "A backup file goes into the folder you choose, for example one that Syncthing " +
                    "or a cloud app keeps in sync. One file a day; older ones are deleted. FocusForce+ " +
                    "only touches the files it wrote.",
            ) {
                FfCard {
                    ValueRow(
                        title = "Folder",
                        value = state.folderName ?: "Choose",
                        onClick = { picker.launch(null) },
                    )
                    if (state.on) {
                        RowDivider()
                        ValueRow(
                            title = "Keep",
                            value = "${state.keep} backups",
                            onClick = { pickKeep = true },
                        )
                        RowDivider()
                        ListRow(
                            title = "Last backup",
                            subtitle = when {
                                state.running -> "Saving..."
                                state.lastError != null -> state.lastError
                                state.lastAt != null -> TimeFormat.dayAndClock(state.lastAt!!)
                                else -> "Not yet"
                            },
                            subtitleColor = if (state.lastError != null && !state.running) colors.warning else colors.textMuted,
                            subtitleMaxLines = 3,
                            trailing = { TextAction("Back up now", onClick = viewModel::backUpNow) },
                        )
                    }
                }
            }
            if (state.on) {
                TextAction("Turn off automatic backups", onClick = viewModel::turnOff, color = colors.error)
            }
        }
    }

    if (pickKeep) {
        ChoiceDialog(
            title = "Keep how many?",
            choices = listOf(3, 7, 14, 30).map { Choice(it, "$it backups") },
            selected = state.keep,
            onSelect = {
                viewModel.setKeep(it)
                pickKeep = false
            },
            onDismiss = { pickKeep = false },
        )
    }
}
