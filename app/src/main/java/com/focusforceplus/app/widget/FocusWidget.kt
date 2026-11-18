package com.focusforceplus.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.focusforceplus.app.R
import com.focusforceplus.app.service.ActiveFocusSession
import com.focusforceplus.app.service.FocusForegroundService
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.TimeFormat

/** Quick focus from the home screen: one tap starts it; while one runs, when it ends. */
class FocusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetColors.load(context)
        val registry = context.widgetDeps().focusRegistry()
        provideContent {
            val session by registry.activeSession.collectAsState()
            FocusContent(context, colors, session)
        }
    }
}

private val MINUTES = listOf(15, 25, 50)

@Composable
private fun FocusContent(context: Context, c: WidgetColors, session: ActiveFocusSession?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(c.background)
            .cornerRadius(20.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (session != null) {
            Column(
                modifier = GlanceModifier.fillMaxWidth()
                    .clickable(actionStartActivity(openApp(context, "focus/active/${session.sessionId}"))),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_bolt),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(c.accent),
                        modifier = GlanceModifier.size(16.dp),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text("Focus", style = TextStyle(color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
                Text(session.name, style = TextStyle(color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(
                    if (session.pausedRemainingSeconds != null) "Paused" else "Until " + TimeFormat.clock(session.endsAtMillis),
                    style = TextStyle(color = c.muted, fontSize = 13.sp),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_bolt),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(c.accent),
                    modifier = GlanceModifier.size(16.dp),
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    "Quick focus",
                    style = TextStyle(color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context, "focus"))),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                MINUTES.forEachIndexed { index, minutes ->
                    if (index > 0) Spacer(GlanceModifier.width(6.dp))
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .height(40.dp)
                            .background(c.primary)
                            .cornerRadius(12.dp)
                            .clickable(actionRunCallback<StartQuickFocus>(actionParametersOf(StartQuickFocus.MINUTES to minutes))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$minutes",
                            style = TextStyle(color = c.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            Text("minutes, with your blocker rules", style = TextStyle(color = c.muted, fontSize = 12.sp), maxLines = 1)
        }
    }
}

/** Starts a quick session like the Focus tab does: not invincible, Do Not Disturb when allowed. */
class StartQuickFocus : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val minutes = parameters[MINUTES] ?: return
        if (context.widgetDeps().focusRegistry().activeSession.value != null) return
        runCatching {
            FocusForegroundService.start(
                context = context,
                sessionId = 0L,
                name = "Quick focus",
                durationMinutes = minutes,
                enableDnd = PermissionHelper.hasDndAccess(context),
                blockNotifications = false,
                blocksApps = true,
                invincible = false,
            )
        }
    }

    companion object {
        val MINUTES = ActionParameters.Key<Int>("minutes")
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}
