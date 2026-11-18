package com.focusforceplus.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.lifecycle.lifecycleScope
import com.focusforceplus.app.R
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.data.repository.ListRepository
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.theme.FocusForceTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

private val LIST_ID = longPreferencesKey("listId")

/** One list to tick off from the home screen; each widget can show a different one. */
class ListWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetColors.load(context)
        val lists = context.widgetDeps().lists()
        val stored = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[LIST_ID]
        val fallback = stored ?: pickDefault(lists)
        val firstList = fallback?.let { lists.getListById(it) }
        val firstItems = fallback?.let { lists.getOpenItems(it).first() }.orEmpty()
        provideContent {
            val listId = currentState<Preferences>()[LIST_ID] ?: fallback
            val list by remember(listId) { listId?.let { lists.observeList(it) } ?: flowOf(null) }
                .collectAsState(firstList.takeIf { it?.id == listId })
            val items by remember(listId) { listId?.let { lists.getOpenItems(it) } ?: flowOf(emptyList()) }
                .collectAsState(if (listId == fallback) firstItems else emptyList())
            ListContent(context, colors, list, items)
        }
    }

    /** Without a choice: the list with the most to do, else the first one. */
    private suspend fun pickDefault(lists: ListRepository): Long? {
        val all = lists.getListsOnce()
        val counts = lists.getCounts().first().associateBy { it.listId }
        return all.maxByOrNull { counts[it.id]?.openCount ?: 0 }?.id
    }
}

@Composable
private fun ListContent(context: Context, c: WidgetColors, list: ListEntity?, items: List<ListItemEntity>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(c.background)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (list == null) {
            Text("No list yet", style = TextStyle(color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Text(
                "Make one in FocusForce+",
                style = TextStyle(color = c.accent, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(openApp(context, "lists"))),
            )
            return@Column
        }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context, "list/${list.id}")))) {
                Text(list.name, style = TextStyle(color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(
                    if (items.isEmpty()) "All done" else "${items.size} to go",
                    style = TextStyle(color = c.muted, fontSize = 12.sp),
                )
            }
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = "Add to ${list.name}",
                colorFilter = ColorFilter.tint(c.onContainer),
                modifier = GlanceModifier
                    .size(36.dp)
                    .background(c.container)
                    .cornerRadius(18.dp)
                    .padding(8.dp)
                    .clickable(actionStartActivity(openApp(context, "list/${list.id}"))),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        LazyColumn {
            items(items, itemId = { it.id }) { item ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable(actionRunCallback<TickListItem>(actionParametersOf(TickListItem.ITEM to item.id))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_circle),
                        contentDescription = "Tick ${item.text}",
                        colorFilter = ColorFilter.tint(c.faint),
                        modifier = GlanceModifier.size(22.dp),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    Text(item.text, style = TextStyle(color = c.text, fontSize = 14.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    item.quantity?.let {
                        Text(it, style = TextStyle(color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}

/** Ticks an item off; the widget redraws from the list itself. */
class TickListItem : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val itemId = parameters[ITEM] ?: return
        context.widgetDeps().lists().setChecked(itemId, true)
        ListWidget().update(context, glanceId)
    }

    companion object {
        val ITEM = ActionParameters.Key<Long>("itemId")
    }
}

class ListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListWidget()
}

/** Shown when a list widget is placed (and when it is set up again): which list? */
@AndroidEntryPoint
class ListWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var lists: ListRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            FocusForceTheme {
                PickList(
                    lists = lists,
                    onPick = { list -> pick(appWidgetId, list) },
                    onBack = ::finish,
                )
            }
        }
    }

    private fun pick(appWidgetId: Int, list: ListEntity) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@ListWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@ListWidgetConfigActivity, glanceId) { it[LIST_ID] = list.id }
            ListWidget().update(this@ListWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@Composable
private fun PickList(lists: ListRepository, onPick: (ListEntity) -> Unit, onBack: () -> Unit) {
    val all by remember { lists.getLists() }.collectAsState(emptyList())
    val counts by remember { lists.getCounts() }.collectAsState(emptyList())
    PageScaffold(title = "Which list?", onBack = onBack) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.Text(
                "The widget shows what is still open on it, to tick off right from the home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = com.focusforceplus.app.ui.theme.FfTheme.colors.textMuted,
            )
            FfCard {
                all.forEachIndexed { i, list ->
                    if (i > 0) RowDivider()
                    val open = counts.firstOrNull { it.listId == list.id }?.openCount ?: 0
                    ListRow(title = list.name, subtitle = if (open == 0) "Nothing open" else "$open to go", onClick = { onPick(list) })
                }
            }
        }
    }
}
