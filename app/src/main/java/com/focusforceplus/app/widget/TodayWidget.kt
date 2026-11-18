package com.focusforceplus.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.focusforceplus.app.R
import com.focusforceplus.app.compliance.dayCodeFor
import com.focusforceplus.app.data.db.entity.RoutineCompletionEntity
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.util.TimeFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.Calendar

/** One row of the Today widget. */
internal data class WidgetTodayRow(
    val at: Long,
    val title: String,
    val detail: String,
    val late: Boolean,
    /** Where a tap leads in the app. */
    val route: String?,
)

internal data class WidgetToday(val rows: List<WidgetTodayRow>, val done: Int, val planned: Int)

/** What is left today: routines and todos in one list by time, late things first. */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetColors.load(context)
        val data = todayFlow(context)
        val first = data.first()
        provideContent {
            val today by remember { data }.collectAsState(first)
            TodayContent(context, colors, today)
        }
    }

    private fun todayFlow(context: Context): Flow<WidgetToday> {
        val deps = context.widgetDeps()
        val start = startOfToday()
        return combine(
            deps.routines().getAllRoutines(),
            deps.routines().getCompletionsSince(start),
            deps.todos().getUncompletedTodos(),
            deps.todos().getCompletionTimesSince(start),
            deps.routineRegistry().activeRoutineId,
        ) { routines, completions, open, doneTimes, running ->
            build(routines, completions, open, doneTimes, running, start)
        }
    }

    private fun build(
        routines: List<RoutineEntity>,
        completions: List<RoutineCompletionEntity>,
        open: List<TodoEntity>,
        doneTimes: List<Long>,
        running: Long?,
        start: Long,
    ): WidgetToday {
        val now = System.currentTimeMillis()
        val end = start + 24 * 60 * 60_000L
        val today = dayCodeFor(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
        val doneRoutineIds = completions.filter { it.completedAt >= start }.map { it.routineId }.toSet()
        val todaysRoutines = routines.filter { it.isActive && today in it.activeDays.split(",") }
        val rows = buildList {
            todaysRoutines.filter { it.id !in doneRoutineIds }.forEach { r ->
                val at = start + (r.startTimeHour * 60 + r.startTimeMinute) * 60_000L
                val isRunning = r.id == running
                add(
                    WidgetTodayRow(
                        at = if (isRunning) minOf(at, now) else at,
                        title = r.name,
                        detail = when {
                            isRunning -> "Routine · running"
                            at < now -> "Routine · was due"
                            else -> "Routine"
                        },
                        late = !isRunning && at < now,
                        route = "routine/active/${r.id}",
                    )
                )
            }
            open.filter { it.deletedAt == null && it.dueDateTime != null && it.dueDateTime < end }.forEach { t ->
                add(
                    WidgetTodayRow(
                        at = t.dueDateTime!!,
                        title = t.title,
                        detail = if (t.dueDateTime < now) "Todo · overdue" else "Todo",
                        late = t.dueDateTime < now,
                        route = "todo/edit/${t.id}",
                    )
                )
            }
        }.sortedBy { it.at }
        val done = doneRoutineIds.count { id -> todaysRoutines.any { it.id == id } } + doneTimes.count { it >= start }
        val dueToday = open.count { it.deletedAt == null && it.dueDateTime != null && it.dueDateTime in start until end }
        val routinesLeft = todaysRoutines.count { it.id !in doneRoutineIds }
        return WidgetToday(rows, done, done + dueToday + routinesLeft)
    }
}

@Composable
private fun TodayContent(context: Context, c: WidgetColors, today: WidgetToday) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(c.background)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context, "home")))) {
                Text("Today", style = TextStyle(color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                if (today.planned > 0) {
                    Text("${today.done} of ${today.planned} done", style = TextStyle(color = c.muted, fontSize = 12.sp))
                }
            }
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = "New todo",
                colorFilter = ColorFilter.tint(c.onContainer),
                modifier = GlanceModifier
                    .size(36.dp)
                    .background(c.container)
                    .cornerRadius(18.dp)
                    .padding(8.dp)
                    .clickable(actionStartActivity(openApp(context, "todo/create"))),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (today.rows.isEmpty()) {
            Text("Nothing else today", style = TextStyle(color = c.muted, fontSize = 14.sp))
        } else {
            LazyColumn {
                items(today.rows, itemId = { it.route.hashCode().toLong() * 31 + it.at }) { row ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable(actionStartActivity(openApp(context, row.route))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            TimeFormat.clock(row.at),
                            style = TextStyle(color = if (row.late) c.error else c.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.width(48.dp),
                        )
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(row.title, style = TextStyle(color = c.text, fontSize = 14.sp), maxLines = 1)
                            Text(row.detail, style = TextStyle(color = c.muted, fontSize = 12.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

internal fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
