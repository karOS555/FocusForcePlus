package com.focusforceplus.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.updateAll
import androidx.glance.unit.ColorProvider
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.ListRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.service.FocusSessionRegistry
import com.focusforceplus.app.ui.theme.AppColors
import com.focusforceplus.app.ui.theme.ThemePresets
import com.focusforceplus.app.ui.theme.ThemeSpec
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the widgets read; they run outside any screen, so they ask Hilt directly. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun todos(): TodoRepository
    fun routines(): RoutineRepository
    fun lists(): ListRepository
    fun focus(): FocusRepository
    fun settings(): SettingsRepository
    fun focusRegistry(): FocusSessionRegistry
    fun routineRegistry(): ActiveRoutineRegistry
}

internal fun Context.widgetDeps(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** The colours of the theme the user picked, fixed for one drawing of a widget. */
internal class WidgetColors(colors: AppColors) {
    val background = ColorProvider(colors.surface)
    val text = ColorProvider(colors.text)
    val muted = ColorProvider(colors.textMuted)
    val faint = ColorProvider(colors.textFaint)
    val accent = ColorProvider(colors.accent)
    val primary = ColorProvider(colors.primary)
    val onPrimary = ColorProvider(colors.onPrimary)
    val container = ColorProvider(colors.container)
    val onContainer = ColorProvider(colors.onContainer)
    val error = ColorProvider(colors.error)
    val warning = ColorProvider(colors.warning)
    val divider = ColorProvider(colors.divider)
    val priorityHigh = ColorProvider(colors.error)
    val priorityMedium = ColorProvider(colors.warning)
    val priorityLow = ColorProvider(colors.textFaint)

    fun priority(p: Int) = when (p) { 2 -> priorityHigh; 1 -> priorityMedium; else -> priorityLow }

    companion object {
        suspend fun load(context: Context): WidgetColors {
            val json = runCatching { context.widgetDeps().settings().themeJson.first() }.getOrNull()
            val spec = json?.let { ThemeSpec.fromJson(it) } ?: ThemePresets.MIDNIGHT
            return WidgetColors(spec.toAppColors())
        }
    }
}

/** Opens the app, on [route] when given (only routes MainActivity accepts from outside). */
internal fun openApp(context: Context, route: String? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        route?.let { putExtra("openRoute", it) }
    }

/** Keeps the widgets in step with the data while the app process lives. */
@Singleton
class WidgetUpdater @Inject constructor(
    private val todos: TodoRepository,
    private val lists: ListRepository,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val focusRegistry: FocusSessionRegistry,
    private val routineRegistry: ActiveRoutineRegistry,
) {
    @OptIn(FlowPreview::class)
    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            combine(
                todos.getAllTodos(),
                combine(lists.getLists(), lists.getCounts(), ::Pair),
                routines.getAllRoutines(),
                combine(focusRegistry.activeSession, routineRegistry.activeRoutineId, ::Pair),
                settings.themeJson,
            ) { _, _, _, _, _ -> }
                .drop(1) // the widgets drew themselves when they were placed
                .debounce(1_000)
                .collect { updateAll(app) }
        }
        // Lists change item by item; the list widget follows each tick.
        scope.launch {
            lists.observeAllItems().drop(1).debounce(500).collect { runCatching { ListWidget().updateAll(app) } }
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            runCatching { TodayWidget().updateAll(context) }
            runCatching { FocusWidget().updateAll(context) }
            runCatching { ListWidget().updateAll(context) }
        }
    }
}

internal fun Color.provider() = ColorProvider(this)

/** Asks the home screen to place a widget; false when it cannot. */
fun pinWidget(context: Context, receiver: Class<*>): Boolean {
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return runCatching { manager.requestPinAppWidget(android.content.ComponentName(context, receiver), null, null) }
        .getOrDefault(false)
}
