package com.focusforceplus.app.ui.screens.lists

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.data.db.entity.ListKind
import com.focusforceplus.app.data.repository.ListText
import com.focusforceplus.app.ui.common.IconPickerButton
import com.focusforceplus.app.ui.components.ActionPill
import com.focusforceplus.app.ui.components.CompletionRing
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.GroupLabel
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.OutlineButton
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.ReorderableColumn
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.RowPosition
import com.focusforceplus.app.ui.components.StepButton
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.TonalButton
import com.focusforceplus.app.ui.components.rowPosition
import com.focusforceplus.app.ui.components.submitsOnEnter
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType
import com.focusforceplus.app.ui.theme.ffBar
import com.focusforceplus.app.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One list. What it offers depends on what it is for.
 *
 * A **shopping list** is about adding quickly and then walking through the shop
 * once: items sort themselves by aisle, things bought before are one tap away,
 * and "Go shopping" turns the page into a big tick-off view that keeps the screen
 * on. Ticked items sit in the basket and leave the list when the shopping is done.
 *
 * A **checklist** is about the same things every time: ticked items stay where
 * they are, the top shows how far along it is, and "Start over" makes it ready
 * for the next time.
 *
 * Whatever is done often has a button in plain sight; nothing a list needs every
 * day is behind a menu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
    onRemind: (listId: Long, title: String) -> Unit = { _, _ -> },
    onEditTodo: (Long) -> Unit = {},
    onOpenList: (Long) -> Unit = {},
    viewModel: ListDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val undo by viewModel.undo.collectAsState()
    val added by viewModel.justAdded.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var input by rememberSaveable { mutableStateOf("") }
    // A checklist with sections: where the next items go. Null = where each was last time.
    var addTo by rememberSaveable { mutableStateOf<String?>(null) }
    var shopping by rememberSaveable { mutableStateOf(false) }
    var hideTicked by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ListItemEntity?>(null) }
    var editingList by remember { mutableStateOf(false) }
    var sortingSections by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // The list was deleted elsewhere: nothing to show.
    LaunchedEffect(state.loading, state.list) {
        if (!state.loading && state.list == null) onBack()
    }
    // The section chosen for new items was renamed or removed.
    LaunchedEffect(state.loading, state.sections) {
        if (!state.loading && addTo != null && addTo !in state.sections) addTo = null
    }
    // A checklist has no shopping view.
    LaunchedEffect(state.isChecklist) {
        if (state.isChecklist) shopping = false
    }
    LaunchedEffect(undo) {
        val offer = undo ?: return@LaunchedEffect
        // Long: taking something back needs a moment to notice and reach for.
        val result = snackbar.showSnackbar(offer.message, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(offer) else viewModel.undoShown(offer)
    }

    // In the shop the phone is in one hand and the trolley in the other: the display stays on.
    val view = LocalView.current
    DisposableEffect(shopping) {
        view.keepScreenOn = shopping
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = shopping) { shopping = false }

    fun submit() {
        if (input.isNotBlank()) {
            viewModel.add(input, addTo)
            input = ""
        }
    }

    fun share() {
        viewModel.shareText { text ->
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                    "Share list",
                )
            )
        }
    }

    PageScaffold(
        title = if (shopping) "Shopping" else state.list?.name.orEmpty(),
        onBack = { if (shopping) shopping = false else onBack() },
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (shopping) {
                ShoppingView(
                    state = state,
                    onTick = viewModel::toggle,
                    onDone = {
                        viewModel.emptyBasket()
                        shopping = false
                    },
                )
            } else {
                AddBar(
                    input = input,
                    onInput = { input = it },
                    onSubmit = ::submit,
                    checklist = state.isChecklist,
                    // While typing: what matches. Otherwise: what was bought before.
                    typed = viewModel.matches(input),
                    again = if (state.isChecklist) emptyList() else state.buyAgain.take(14).map { it.text },
                    onPick = { text ->
                        viewModel.add(text, addTo)
                        input = ""
                    },
                    sections = if (state.isChecklist) state.sections else emptyList(),
                    section = addTo,
                    onSection = { addTo = it },
                    notice = if (added?.already == true) "Already on the list" else null,
                )
                ListBody(
                    state = state,
                    hideTicked = hideTicked,
                    added = added,
                    onAddedShown = viewModel::addedShown,
                    onToggleHide = { hideTicked = !hideTicked },
                    onToggle = viewModel::toggle,
                    onEdit = { editing = it },
                    onRemind = { state.list?.let { onRemind(it.id, it.name) } },
                    onShare = ::share,
                    onSections = { sortingSections = true },
                    onEditList = { editingList = true },
                    onEditTodo = onEditTodo,
                    onStartOver = viewModel::startOver,
                    onEmptyBasket = viewModel::emptyBasket,
                )
                // Not while typing: nobody leaves for the shop mid-word, and the room
                // is better spent on the list.
                if (!state.isChecklist && state.open > 0 && !WindowInsets.isImeVisible) {
                    Column(Modifier.fillMaxWidth().ffBar().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        PrimaryButton(
                            text = if (state.open == 1) "Go shopping · 1 item" else "Go shopping · ${state.open} items",
                            icon = Icons.Filled.ShoppingCart,
                            onClick = { shopping = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    editing?.let { item ->
        EditItemSheet(
            item = item,
            sections = state.sections,
            sectionWord = if (state.isChecklist) "Section" else "Aisle",
            onSave = { text, quantity, note, section ->
                viewModel.update(item, text, quantity, note, section)
                editing = null
            },
            onDelete = {
                viewModel.delete(item)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (sortingSections) {
        SectionOrderSheet(
            sections = state.sections,
            checklist = state.isChecklist,
            onOrder = viewModel::setSectionOrder,
            onRemove = viewModel::removeSection,
            onDismiss = { sortingSections = false },
        )
    }

    val list = state.list
    if (editingList && list != null) {
        EditListSheet(
            name = list.name,
            iconKey = list.iconKey,
            kind = list.kind,
            onSave = { name, icon, kind ->
                viewModel.rename(name, icon)
                viewModel.setKind(kind)
                editingList = false
            },
            onDuplicate = {
                editingList = false
                viewModel.duplicate(onOpenList)
            },
            onDelete = {
                editingList = false
                confirmDelete = true
            },
            onDismiss = { editingList = false },
        )
    }

    if (confirmDelete) {
        DeleteDialog(
            what = "\"${state.list?.name.orEmpty()}\"",
            onConfirm = {
                confirmDelete = false
                viewModel.moveToTrash(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// ── Adding ─────────────────────────────────────────────────────────────────────

/**
 * The field to type into and, under it, what one tap can do: on a checklist with
 * sections, choose where the next items go; while typing, take a suggestion; on a
 * shopping list, add again what was bought before.
 */
@Composable
private fun AddBar(
    input: String,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    checklist: Boolean,
    typed: List<String>,
    again: List<String>,
    onPick: (String) -> Unit,
    sections: List<String>,
    section: String?,
    onSection: (String?) -> Unit,
    notice: String?,
) {
    val colors = FfTheme.colors
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FfTextField(
                value = input,
                onValueChange = onInput,
                placeholder = if (checklist) "Add an item" else "Add: milk, 2 bread, flour 500 g",
                // Several lines: a long item can be read while it is typed, and a
                // pasted list keeps its lines.
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.weight(1f).submitsOnEnter(onSubmit),
            )
            IconButton(onClick = onSubmit, enabled = input.isNotBlank()) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add",
                    tint = if (input.isNotBlank()) colors.accent else colors.textFaint,
                )
            }
        }
        if (notice != null) {
            Text(
                notice,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
        if (sections.isNotEmpty()) {
            ChipLine(label = "Add to") {
                sections.forEach { name ->
                    FfChip(
                        label = name,
                        selected = section == name,
                        onClick = { onSection(if (section == name) null else name) },
                    )
                }
            }
        }
        val typing = input.isNotBlank()
        val chips = if (typing) typed else again
        if (chips.isNotEmpty()) {
            ChipLine(label = if (typing) null else "Buy again") {
                chips.forEach { text -> FfChip(label = text, selected = false, onClick = { onPick(text) }) }
            }
        }
    }
}

/** One line of chips that scrolls sideways, with a quiet word in front that says what they do. */
@Composable
private fun ChipLine(label: String?, chips: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = FfTheme.colors.textMuted)
        }
        chips()
    }
}

// ── The list ───────────────────────────────────────────────────────────────────

/**
 * One row of the page, top to bottom. The page is a flat list of these, so that
 * every item is a row of its own: it can be scrolled to, and it can move.
 */
private sealed interface BodyRow {
    val key: String

    data object Actions : BodyRow { override val key = "actions" }
    data object Progress : BodyRow { override val key = "progress" }
    data object Reminders : BodyRow { override val key = "reminders" }
    data object Empty : BodyRow { override val key = "empty" }
    data class Header(val name: String?) : BodyRow {
        override val key = if (name == null) "section" else "section-$name"
    }
    data class Item(val item: ListItemEntity, val position: RowPosition) : BodyRow {
        override val key = "item-${item.id}"

        /** The top row of its card, which keeps its distance from what is above. */
        val first: Boolean get() = position == RowPosition.First || position == RowPosition.Single
    }
    data object AllIn : BodyRow { override val key = "all-in" }
    data object BasketLabel : BodyRow { override val key = "basket-label" }
    data object Hint : BodyRow { override val key = "hint" }
}

private fun bodyRows(state: ListDetailUiState, hideTicked: Boolean): List<BodyRow> = buildList {
    val checklist = state.isChecklist
    add(BodyRow.Actions)
    if (checklist && state.total > 0) add(BodyRow.Progress)
    if (state.reminders.isNotEmpty()) add(BodyRow.Reminders)
    if (state.total == 0 && !state.loading) add(BodyRow.Empty)
    val showHeaders = state.groups.size > 1 || state.groups.any { it.name != null }
    state.groups.forEach { group ->
        val items = if (checklist && hideTicked) group.items.filter { !it.isChecked } else group.items
        if (items.isEmpty()) return@forEach
        if (showHeaders) add(BodyRow.Header(group.name))
        items.forEachIndexed { index, item -> add(BodyRow.Item(item, rowPosition(index, items.size))) }
    }
    if (!checklist && state.open == 0 && state.basket.isNotEmpty()) add(BodyRow.AllIn)
    if (state.basket.isNotEmpty()) {
        add(BodyRow.BasketLabel)
        state.basket.forEachIndexed { index, item -> add(BodyRow.Item(item, rowPosition(index, state.basket.size))) }
    }
    if (state.total > 0) add(BodyRow.Hint)
}

/** How long a row that was just added stays pointed out. */
private const val ADDED_SHOWN_MILLIS = 2_200L

@Composable
private fun ColumnScope.ListBody(
    state: ListDetailUiState,
    hideTicked: Boolean,
    added: JustAdded?,
    onAddedShown: (JustAdded) -> Unit,
    onToggleHide: () -> Unit,
    onToggle: (ListItemEntity) -> Unit,
    onEdit: (ListItemEntity) -> Unit,
    onRemind: () -> Unit,
    onShare: () -> Unit,
    onSections: () -> Unit,
    onEditList: () -> Unit,
    onEditTodo: (Long) -> Unit,
    onStartOver: () -> Unit,
    onEmptyBasket: () -> Unit,
) {
    val colors = FfTheme.colors
    val checklist = state.isChecklist
    val rows = remember(state, hideTicked) { bodyRows(state, hideTicked) }
    val currentRows by rememberUpdatedState(rows)
    val listState = rememberLazyListState()
    val roomAbove = with(LocalDensity.current) { 56.dp.roundToPx() }

    // What was just added is brought into view and pointed out. With the keyboard
    // open only a few rows are on screen, and a new item often lands below them:
    // without this it looked as if nothing had happened.
    LaunchedEffect(added?.stamp) {
        val target = added ?: return@LaunchedEffect
        // The row arrives from the database a moment after the tap.
        val index = withTimeoutOrNull(1_500) {
            snapshotFlow { currentRows.indexOfFirst { it is BodyRow.Item && it.item.id == target.itemId } }
                .first { it >= 0 }
        }
        if (index != null) {
            withFrameNanos { }
            val layout = listState.layoutInfo
            val seen = layout.visibleItemsInfo.firstOrNull { it.index == index }
            val inView = seen != null && seen.offset >= 0 && seen.offset + seen.size <= layout.viewportEndOffset
            // Only as far as needed: a row that is on screen stays where it is.
            if (!inView) listState.animateScrollToItem(index, -roomAbove)
        }
        delay(ADDED_SHOWN_MILLIS)
        onAddedShown(target)
    }

    // The distance between two blocks. Rows of one card have none.
    val gap = Modifier.padding(top = 6.dp)
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
    ) {
        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            when (row) {
                // What the list offers, in plain sight.
                BodyRow.Actions -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        // Room for the soft shadow the pills cast in the Aero style.
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionPill("Remind me", Icons.Filled.Alarm, onRemind)
                    ActionPill("Share", Icons.Filled.Share, onShare, enabled = state.total > 0)
                    ActionPill(
                        if (checklist) "Sections" else "Aisles",
                        Icons.Filled.SwapVert,
                        onSections,
                        enabled = state.sections.isNotEmpty(),
                    )
                    if (checklist) {
                        ActionPill(
                            if (hideTicked) "Show ticked" else "Hide ticked",
                            if (hideTicked) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            onToggleHide,
                            enabled = state.ticked > 0,
                            selected = hideTicked,
                        )
                    }
                    ActionPill("Edit list", Icons.Filled.Edit, onEditList)
                }

                BodyRow.Progress -> ChecklistProgress(state.ticked, state.total, onStartOver, modifier = gap)

                BodyRow.Reminders -> FfCard(modifier = gap) {
                    state.reminders.forEachIndexed { index, todo ->
                        if (index > 0) RowDivider()
                        ListRow(
                            title = todo.title,
                            subtitle = todo.dueDateTime?.let { "Reminder " + TimeFormat.dayAndClock(it) }
                                ?: "Reminder, no date",
                            onClick = { onEditTodo(todo.id) },
                            leading = { IconCircle(icon = Icons.Filled.Alarm) },
                        )
                    }
                }

                BodyRow.Empty -> Text(
                    if (checklist) {
                        "Nothing on it yet. Add what you pack or check every time; ticked items stay, " +
                            "and \"Start over\" makes the list ready for the next time."
                    } else {
                        "Nothing on it yet. Type above, several things at once if you like: \"milk, " +
                            "2 bread, flour 500 g\". Common things find their aisle on their own."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = gap.padding(horizontal = 2.dp, vertical = 8.dp),
                )

                is BodyRow.Header -> GroupLabel(row.name ?: "Other", modifier = gap.padding(top = 6.dp))

                is BodyRow.Item -> ItemRow(
                    item = row.item,
                    position = row.position,
                    pointedOut = added?.itemId == row.item.id,
                    onToggle = { onToggle(row.item) },
                    onEdit = { onEdit(row.item) },
                    modifier = Modifier.animateItem().then(if (row.first) gap else Modifier),
                )

                BodyRow.AllIn -> Text(
                    "Everything is in the basket.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = gap.padding(horizontal = 2.dp, vertical = 4.dp),
                )

                BodyRow.BasketLabel -> Row(
                    modifier = gap.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupLabel("In the basket · ${state.basket.size}", modifier = Modifier.weight(1f))
                    TextAction("Done, empty it", onClick = onEmptyBasket)
                }

                BodyRow.Hint -> Text(
                    "Tap to tick. Press and hold an item for its amount, note or " +
                        (if (checklist) "section." else "aisle."),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                    modifier = gap.padding(top = 8.dp, start = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ChecklistProgress(ticked: Int, total: Int, onStartOver: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FfTheme.colors
    FfPaddedCard(modifier = modifier, spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ticked >= total) "All $total ticked" else "$ticked of $total ticked",
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            if (ticked > 0) {
                ActionPill("Start over", Icons.Filled.Refresh, onStartOver, selected = ticked >= total)
            }
        }
        ProgressLine(ticked, total)
    }
}

@Composable
private fun ProgressLine(done: Int, total: Int) {
    val colors = FfTheme.colors
    LinearProgressIndicator(
        progress = { if (total == 0) 0f else done.toFloat() / total },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = colors.primary,
        trackColor = colors.surfaceHigh,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

@Composable
private fun ItemRow(
    item: ListItemEntity,
    position: RowPosition,
    pointedOut: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FfTheme.colors
    // Comes up quickly over the row that was just added and fades slowly.
    val tint by animateColorAsState(
        targetValue = colors.accent.copy(alpha = if (pointedOut) 0.20f else 0f),
        animationSpec = tween(if (pointedOut) 200 else 700),
        label = "added",
    )
    ListRow(
        title = item.text,
        subtitle = item.note,
        struck = item.isChecked,
        titleColor = if (item.isChecked) colors.textMuted else colors.text,
        // All of it: an item is there to be read.
        titleMaxLines = Int.MAX_VALUE,
        subtitleMaxLines = 4,
        position = position,
        tint = tint,
        modifier = modifier,
        onClick = onToggle,
        onLongClick = onEdit,
        leading = {
            CompletionRing(
                checked = item.isChecked,
                onClick = onToggle,
                color = colors.textFaint,
                contentDescription = item.text,
            )
        },
        trailing = item.quantity?.let { amount ->
            {
                Text(
                    amount,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (item.isChecked) colors.textFaint else colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        },
    )
}

// ── In the shop ────────────────────────────────────────────────────────────────

/**
 * The list as it is needed in the shop: only what is left, aisle by aisle, in rows
 * big enough for a thumb, with how far along it is on top. A tap puts an item in
 * the basket; the basket is at the end in case something has to come back out.
 */
@Composable
private fun ColumnScope.ShoppingView(
    state: ListDetailUiState,
    onTick: (ListItemEntity) -> Unit,
    onDone: () -> Unit,
) {
    val colors = FfTheme.colors
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Text(
            when {
                state.total == 0 -> "Nothing on the list"
                state.open == 0 -> "Everything is in the basket"
                else -> "${state.ticked} of ${state.total} in the basket"
            },
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
        )
        Spacer(Modifier.height(8.dp))
        ProgressLine(state.ticked, state.total)
    }
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.groups.forEach { group ->
            item(key = "aisle-${group.name}") {
                GroupLabel(group.name ?: "Other", modifier = Modifier.padding(top = 6.dp))
            }
            items(group.items, key = { it.id }) { item ->
                ShopRow(item, onClick = { onTick(item) }, modifier = Modifier.animateItem())
            }
        }
        if (state.basket.isNotEmpty()) {
            item(key = "basket-label") {
                GroupLabel("In the basket · ${state.basket.size}", modifier = Modifier.padding(top = 12.dp))
            }
            item(key = "basket") {
                FfCard {
                    state.basket.forEachIndexed { index, item ->
                        if (index > 0) RowDivider()
                        ListRow(
                            title = item.text,
                            struck = true,
                            titleColor = colors.textMuted,
                            titleMaxLines = Int.MAX_VALUE,
                            onClick = { onTick(item) },
                            leading = {
                                CompletionRing(
                                    checked = true,
                                    onClick = { onTick(item) },
                                    color = colors.textFaint,
                                    contentDescription = "Take ${item.text} back out",
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().ffBar().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        PrimaryButton(
            text = when {
                state.basket.isEmpty() -> "Back to the list"
                state.open == 0 -> "Done shopping"
                else -> "Done shopping · ${state.open} stay on the list"
            },
            icon = Icons.Filled.Check,
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ShopRow(item: ListItemEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FfTheme.colors
    FfCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompletionRing(
                checked = false,
                onClick = onClick,
                color = colors.textFaint,
                contentDescription = "Put ${item.text} in the basket",
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(item.text, style = MaterialTheme.typography.titleMedium, color = colors.text)
                item.note?.let {
                    Text(it, style = FfType.rowDetail, color = colors.textMuted)
                }
            }
            item.quantity?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = colors.accent)
            }
        }
    }
}

// ── Sheets ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditItemSheet(
    item: ListItemEntity,
    sections: List<String>,
    sectionWord: String,
    onSave: (text: String, quantity: String, note: String, section: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var text by remember { mutableStateOf(item.text) }
    var quantity by remember { mutableStateOf(item.quantity.orEmpty()) }
    var note by remember { mutableStateOf(item.note.orEmpty()) }
    var section by remember { mutableStateOf(item.section) }
    var newSection by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FfTextField(
                value = text,
                onValueChange = { text = it.take(ListText.MAX_ITEM) },
                label = "Item",
                // Several lines, so a long item can be read while it is changed.
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )
            // Plus and minus count pieces; anything with a unit ("500 g") is typed.
            val count = ListText.countOf(quantity)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FfTextField(
                    value = quantity,
                    onValueChange = { quantity = it.take(20) },
                    label = "Amount",
                    placeholder = "1",
                    modifier = Modifier.weight(1f),
                )
                StepButton(Icons.Filled.Remove, "One less", enabled = count != null && count > 1) {
                    quantity = ListText.withCount(quantity, (count ?: 1) - 1).orEmpty()
                }
                StepButton(Icons.Filled.Add, "One more", enabled = count != null) {
                    quantity = ListText.withCount(quantity, (count ?: 1) + 1).orEmpty()
                }
            }
            FfTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = "Note",
                placeholder = "The big one, brand, ...",
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )
            Text(sectionWord, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FfChip(label = "None", selected = section == null && newSection.isBlank(), onClick = { section = null; newSection = "" })
                sections.forEach { name ->
                    FfChip(label = name, selected = section == name && newSection.isBlank(), onClick = { section = name; newSection = "" })
                }
            }
            FfTextField(
                value = newSection,
                onValueChange = { newSection = it.take(40) },
                placeholder = "Or a new one",
            )
            Text(
                "Next time this item is added, it goes to the same place.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(
                    text = "Remove",
                    icon = Icons.Filled.DeleteOutline,
                    onClick = onDelete,
                    contentColor = colors.error,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Save",
                    onClick = { onSave(text, quantity, note, newSection.trim().ifBlank { section }) },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The order of sections: drag them into the way you walk through the shop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionOrderSheet(
    sections: List<String>,
    checklist: Boolean,
    onOrder: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    // Kept here while dragging, so a row follows the finger without waiting for the database.
    var order by remember(sections.toSet()) { mutableStateOf(sections) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(if (checklist) "Sections" else "Aisles", style = MaterialTheme.typography.titleLarge, color = colors.text)
            Text(
                if (checklist) "Drag them into the order you go through them."
                else "Drag them into the order you walk through your shop. The list follows it.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            ReorderableColumn(
                items = order,
                key = { it },
                rowHeight = 52.dp,
                onMove = { from, to ->
                    if (from in order.indices && to in order.indices) {
                        order = order.toMutableList().apply { add(to, removeAt(from)) }
                        onOrder(order)
                    }
                },
            ) { name, handle, _ ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(handle.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to move $name",
                            tint = colors.textFaint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(name, style = FfType.rowTitle, color = colors.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(name) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove $name", tint = colors.textMuted)
                    }
                }
            }
            Text(
                "Removing one keeps its items; they just have no " + (if (checklist) "section" else "aisle") + " then.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Name, icon and kind of the list, and what is done with a whole list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditListSheet(
    name: String,
    iconKey: String?,
    kind: String,
    onSave: (name: String, iconKey: String?, kind: String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var newName by remember { mutableStateOf(name) }
    var icon by remember { mutableStateOf(iconKey) }
    var newKind by remember { mutableStateOf(kind) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPickerButton(selectedKey = icon, size = 52.dp, onSelect = { icon = it })
                Spacer(Modifier.width(10.dp))
                FfTextField(
                    value = newName,
                    onValueChange = { newName = it.take(60) },
                    label = "Name",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
            }
            KindPicker(newKind, onPick = { newKind = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill("Duplicate", Icons.Filled.ContentCopy, onDuplicate)
                ActionPill("Delete list", Icons.Filled.DeleteOutline, onDelete)
            }
            PrimaryButton(
                text = "Save",
                onClick = { onSave(newName, icon, newKind) },
                enabled = newName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The two kinds of list, each with a sentence that says what it does differently. */
@Composable
internal fun KindPicker(kind: String, onPick: (String) -> Unit) {
    val colors = FfTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KindOption(
            title = "Shopping list",
            text = "Sorts by aisle, remembers what you buy, and empties the basket when you are done.",
            icon = Icons.Filled.ShoppingCart,
            selected = kind == ListKind.SHOPPING,
            onClick = { onPick(ListKind.SHOPPING) },
        )
        KindOption(
            title = "Checklist",
            text = "The same things every time, like packing. Ticked items stay, and it starts over.",
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            selected = kind == ListKind.CHECKLIST,
            onClick = { onPick(ListKind.CHECKLIST) },
        )
    }
}

@Composable
private fun KindOption(
    title: String,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FfTheme.colors
    FfCard(color = if (selected) colors.container else colors.surface, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(icon = icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = FfType.rowTitle, color = colors.text)
                Text(text, style = FfType.rowDetail, color = colors.textMuted)
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}
