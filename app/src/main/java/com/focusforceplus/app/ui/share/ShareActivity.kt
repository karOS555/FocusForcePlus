package com.focusforceplus.app.ui.share

import com.focusforceplus.app.ui.components.verticalScrollWithHint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.repository.ListRepository
import com.focusforceplus.app.data.repository.ListText
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.navigation.AppModule
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FocusForceTheme
import com.focusforceplus.app.util.ExternalStarts
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What came in from another app's share sheet. */
data class SharedText(val title: String, val details: String?, val lines: List<String>)

/**
 * Takes text shared from another app and turns it into a todo or list items.
 * It needs no switch like start links do: the user picks the target right here.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = read(intent)
        if (shared == null) {
            finish()
            return
        }
        setContent {
            FocusForceTheme {
                ShareSheet(
                    shared = shared,
                    viewModel = viewModel,
                    onDone = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun read(intent: Intent?): SharedText? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (text.isEmpty() && subject.isEmpty()) return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // A shared link usually comes with the page title as the subject: that is
        // the better todo title, and the link goes into the details.
        return if (subject.isNotEmpty() && (lines.size != 1 || lines.first().startsWith("http"))) {
            SharedText(subject.take(200), text.ifEmpty { null }, lines.ifEmpty { listOf(subject) })
        } else {
            // A first line too long for a title is shortened there and kept whole
            // in the details, so nothing of what was shared is lost.
            val first = lines.first()
            val tooLong = first.length > 200
            SharedText(
                title = if (tooLong) first.take(199).trimEnd() + "…" else first,
                details = (if (tooLong) lines else lines.drop(1)).joinToString("\n").ifBlank { null },
                lines = lines,
            )
        }
    }
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val starts: ExternalStarts,
    private val listRepository: ListRepository,
    navRepository: NavRepository,
) : ViewModel() {

    data class Targets(val todos: Boolean = true, val lists: List<ListEntity> = emptyList())

    val targets: StateFlow<Targets> = combine(listRepository.getLists(), navRepository.config) { lists, nav ->
        Targets(
            todos = nav.isOn(AppModule.TODOS),
            lists = if (nav.isOn(AppModule.LISTS)) lists else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Targets())

    fun addTodo(title: String, details: String?, priority: Int, onDone: (String) -> Unit) {
        viewModelScope.launch {
            starts.addTodo(title, details, priority)
            onDone("Todo added")
        }
    }

    /**
     * Adds what was shared, read the way the list reads what is typed into it: a
     * shopping list splits at commas too and takes amounts, a checklist keeps each
     * line as it is.
     */
    fun addToList(list: ListEntity, lines: List<String>, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val text = lines.joinToString("\n")
            val items = (if (list.isChecklist) ListText.splitLines(text) else ListText.splitItems(text)).take(MAX_SHARED_ITEMS)
            items.forEach { listRepository.addItem(list.id, it) }
            onDone(if (items.size == 1) "Added to ${list.name}" else "${items.size} items added to ${list.name}")
        }
    }

    private companion object {
        const val MAX_SHARED_ITEMS = 50
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareSheet(
    shared: SharedText,
    viewModel: ShareViewModel,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = FfTheme.colors
    val targets by viewModel.targets.collectAsState()
    var title by remember { mutableStateOf(shared.title) }
    var priority by remember { mutableIntStateOf(1) }
    // How many items the lines become depends on the list they go to, so the sheet
    // speaks of the lines.
    val lines = shared.lines

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .background(colors.background, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .imePadding()
                .verticalScrollWithHint()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add to FocusForce+", style = MaterialTheme.typography.titleLarge, color = colors.text)

            if (targets.todos) {
                FfTextField(value = title, onValueChange = { title = it }, label = "New todo")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Low", 1 to "Medium", 2 to "High").forEach { (p, label) ->
                        FfChip(label = label, selected = priority == p, onClick = { priority = p })
                    }
                }
                PrimaryButton(
                    text = "Add todo",
                    onClick = { viewModel.addTodo(title, shared.details, priority, onDone) },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (targets.lists.isNotEmpty()) {
                Text(
                    if (lines.size == 1) "Or add \"${lines.first().take(40)}\" to a list" else "Or add these ${lines.size} lines to a list",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                FfCard {
                    targets.lists.forEachIndexed { i, list ->
                        if (i > 0) RowDivider()
                        ListRow(
                            title = list.name,
                            onClick = { viewModel.addToList(list, lines, onDone) },
                            leading = {
                                IconCircle(
                                    icon = RoutineIcons.find(list.iconKey)?.vector
                                        ?: Icons.AutoMirrored.Filled.FormatListBulleted,
                                )
                            },
                        )
                    }
                }
            }

            if (!targets.todos && targets.lists.isEmpty()) {
                Text(
                    "Todos and lists are both switched off, so there is nowhere to put this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }
            TextAction("Cancel", onClick = onCancel, color = colors.textSecondary, modifier = Modifier.align(Alignment.End))
        }
    }
}
