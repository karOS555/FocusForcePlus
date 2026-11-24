package com.focusforceplus.app.ui.screens.settings

import com.focusforceplus.app.ui.components.verticalScrollWithHint
import com.focusforceplus.app.ui.theme.AeroPhoto
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.focusforceplus.app.ui.theme.ffBackdrop
import com.focusforceplus.app.ui.theme.aeroGlass
import com.focusforceplus.app.ui.theme.ThemeStyle
import com.focusforceplus.app.ui.components.SwitchRow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FocusForceTheme
import com.focusforceplus.app.ui.theme.ThemePresets
import com.focusforceplus.app.ui.theme.ThemeSpec
import com.focusforceplus.app.ui.theme.ThemeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val spec: StateFlow<ThemeSpec> = ThemeStore.spec

    /** Themes of your own, in the order they were saved. */
    val saved: StateFlow<List<ThemeSpec>> = settings.savedThemesJson
        .map { ThemeSpec.listFromJson(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Applies at once, everywhere, and is kept. */
    fun use(spec: ThemeSpec) {
        ThemeStore.apply(spec)
        viewModelScope.launch {
            settings.saveThemeJson(if (spec == ThemePresets.MIDNIGHT) null else spec.toJson())
        }
    }

    /** Keeps the theme in use under [name]; the same name again updates that one. */
    fun saveAs(name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        val theme = spec.value.copy(name = clean)
        val others = saved.value.filterNot { it.name.equals(clean, ignoreCase = true) }
        store((others + theme).takeLast(ThemeSpec.MAX_SAVED))
        use(theme)
    }

    fun rename(theme: ThemeSpec, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        store(saved.value.map { if (it == theme) it.copy(name = clean) else it })
        if (spec.value == theme) use(theme.copy(name = clean))
    }

    fun delete(theme: ThemeSpec) = store(saved.value - theme)

    private fun store(themes: List<ThemeSpec>) {
        viewModelScope.launch {
            settings.saveSavedThemesJson(if (themes.isEmpty()) null else ThemeSpec.listToJson(themes))
        }
    }
}

private enum class Role(val label: String) { BACKGROUND("Background"), SURFACE("Cards"), PRIMARY("Buttons"), ACCENT("Links and icons"), TEXT("Text") }

private fun ThemeSpec.get(role: Role) = when (role) {
    Role.BACKGROUND -> background
    Role.SURFACE -> surface
    Role.PRIMARY -> primary
    Role.ACCENT -> accent
    Role.TEXT -> text
}

private fun ThemeSpec.with(role: Role, color: Color) = when (role) {
    Role.BACKGROUND -> copy(background = color)
    Role.SURFACE -> copy(surface = color)
    Role.PRIMARY -> copy(primary = color)
    Role.ACCENT -> copy(accent = color)
    Role.TEXT -> copy(text = color)
}.named(name)

/** A preset's look keeps the preset's name; a changed preset becomes "Custom". */
private fun ThemeSpec.named(previous: String): ThemeSpec {
    ThemePresets.ALL.firstOrNull { it.sameColorsAs(this) }?.let { return copy(name = it.name) }
    return if (ThemePresets.ALL.any { it.name == previous }) copy(name = "Custom") else copy(name = previous)
}

/** WCAG contrast ratio between two colours. */
private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** Themes: six ready-made ones, your own colours, and sharing them as text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val spec by viewModel.spec.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val colors = FfTheme.colors
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Role?>(null) }
    var showAeroCredits by remember { mutableStateOf(false) }
    if (showAeroCredits) {
        com.focusforceplus.app.ui.components.AssetNoteDialog(
            title = "Picture in the Aero style",
            assetPath = AeroPhoto.NOTE_ASSET,
            onDismiss = { showAeroCredits = false },
        )
    }
    var naming by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<ThemeSpec?>(null) }
    var renaming by remember { mutableStateOf<ThemeSpec?>(null) }

    PageScaffold(title = "Theme", onBack = onNavigateBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScrollWithHint()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            FormSection(title = "Ready-made") {
                TileGrid(ThemePresets.ALL) { preset, modifier ->
                    ThemeTile(
                        spec = preset,
                        selected = spec.sameColorsAs(preset),
                        onClick = { viewModel.use(preset) },
                        modifier = modifier,
                    )
                }
            }

            if (saved.isNotEmpty()) {
                FormSection(title = "Your themes", hint = "Press and hold one to rename or delete it.") {
                    TileGrid(saved) { theme, modifier ->
                        ThemeTile(
                            spec = theme,
                            selected = spec == theme,
                            onClick = { viewModel.use(theme) },
                            onLongClick = { managing = theme },
                            modifier = modifier,
                        )
                    }
                }
            }

            FormSection(
                title = "Your colours",
                hint = "Five colours; every other shade is worked out from them. Changes apply right away.",
            ) {
                FfCard {
                    Role.entries.forEachIndexed { i, role ->
                        if (i > 0) RowDivider()
                        val c = spec.get(role)
                        ListRow(
                            title = role.label,
                            subtitle = ThemeSpec.hex(c),
                            onClick = { editing = role },
                            trailing = { Swatch(c, size = 30) },
                        )
                    }
                    RowDivider()
                    SwitchRow(
                        title = "Aero style",
                        subtitle = "Glass cards and liquid glass buttons over a summer sky, a green meadow and soap bubbles.",
                        checked = spec.style == ThemeStyle.AERO,
                        onCheckedChange = { on ->
                            viewModel.use(spec.copy(style = if (on) ThemeStyle.AERO else ThemeStyle.FLAT).named(spec.name))
                        },
                    )
                }
                TextAction(
                    "Save as one of your themes",
                    onClick = { naming = true },
                    modifier = Modifier.padding(top = 4.dp),
                )
                val ratio = contrast(spec.text, spec.background)
                if (ratio < 4.5f) {
                    Text(
                        "Text and background are close: some text may be hard to read.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.warning,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            FormSection(title = "Preview") {
                FocusForceTheme(colors = spec.toAppColors()) {
                    PreviewCard()
                }
                if (spec.style == ThemeStyle.AERO) {
                    // The licences of the photos in the picture ask for this credit.
                    Text(
                        AeroPhoto.CREDIT,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { showAeroCredits = true },
                    )
                }
            }

            FormSection(
                title = "Share a theme",
                hint = "A theme is a few lines of text. Send it to someone, or paste one you got.",
            ) {
                FfCard {
                    ListRow(title = "Copy this theme", onClick = { copy(context, spec.toJson()) })
                    RowDivider()
                    ListRow(title = "Send this theme", onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, spec.toJson()),
                                "Send theme",
                            )
                        )
                    })
                    RowDivider()
                    ListRow(title = "Paste a theme", onClick = {
                        val pasted = paste(context)?.let(ThemeSpec::fromJson)
                        if (pasted == null) {
                            Toast.makeText(context, "The clipboard has no theme in it.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.use(pasted)
                            Toast.makeText(context, "\"${pasted.name}\" is on.", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
            if (!spec.sameColorsAs(ThemePresets.MIDNIGHT)) {
                TextAction("Back to the default look", onClick = { viewModel.use(ThemePresets.MIDNIGHT) })
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (naming) {
        val suggested = spec.name.takeUnless { n -> n == "Custom" || ThemePresets.ALL.any { it.name == n } }.orEmpty()
        NameThemeDialog(
            title = "Save as one of your themes",
            initial = suggested,
            hint = if (saved.size >= ThemeSpec.MAX_SAVED) "You have ${ThemeSpec.MAX_SAVED}; the oldest one makes room." else
                "The same name again updates that theme.",
            onSave = { name -> viewModel.saveAs(name); naming = false },
            onDismiss = { naming = false },
        )
    }
    managing?.let { theme ->
        AlertDialog(
            onDismissRequest = { managing = null },
            containerColor = colors.surfaceVariant,
            title = { Text(theme.name, style = MaterialTheme.typography.titleLarge, color = colors.text) },
            text = {
                Column {
                    ListRow(title = "Use it", onClick = { viewModel.use(theme); managing = null })
                    ListRow(title = "Rename", onClick = { renaming = theme; managing = null })
                    ListRow(title = "Delete", titleColor = colors.error, onClick = { viewModel.delete(theme); managing = null })
                }
            },
            confirmButton = {
                TextButton(onClick = { managing = null }) {
                    Text("Close", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                }
            },
        )
    }
    renaming?.let { theme ->
        NameThemeDialog(
            title = "Rename theme",
            initial = theme.name,
            hint = null,
            onSave = { name -> viewModel.rename(theme, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    editing?.let { role ->
        ColorPickerDialog(
            title = role.label,
            initial = spec.get(role),
            onPick = {
                viewModel.use(spec.with(role, it))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Tiles three to a row; a short last row keeps the same tile width. */
@Composable
private fun <T> TileGrid(items: List<T>, tile: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile(it, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NameThemeDialog(
    title: String,
    initial: String,
    hint: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FfTextField(value = name, onValueChange = { name = it.take(40) }, label = "Name")
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text("Save", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeTile(
    spec: ThemeSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = FfTheme.colors
    Column(
        modifier = modifier
            .aeroGlass(RoundedCornerShape(14.dp), tint = colors.surface.copy(alpha = 0.34f))
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accent else colors.divider,
                shape = RoundedCornerShape(14.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = spec.name + if (selected) ", in use" else "" },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (spec.style == ThemeStyle.AERO) {
                        // The Aero picture in three colours: sky, haze at the horizon, meadow.
                        Modifier.background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xFF3892D9), Color(0xFFC9E8F7), Color(0xFF82C53F)),
                            )
                        )
                    } else Modifier.background(spec.background)
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(spec.surface, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.8f).height(5.dp).background(spec.text, CircleShape))
                Box(Modifier.fillMaxWidth(0.5f).height(5.dp).background(spec.accent, CircleShape))
            }
            Box(Modifier.fillMaxWidth().height(14.dp).background(spec.primary, RoundedCornerShape(5.dp)))
        }
        Text(
            spec.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PreviewCard() {
    val colors = FfTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .ffBackdrop()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FfCard {
            ListRow(title = "Water the plants", subtitle = "Today 18:00")
            RowDivider()
            ListRow(title = "Morning routine", subtitle = "Running now", subtitleColor = colors.accent)
        }
        FfPaddedCard(spacing = 4.dp) {
            Text("Quick focus", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text("A short text in the muted colour.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
        PrimaryButton(text = "Start focus", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Swatch(color: Color, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, FfTheme.colors.outline, CircleShape),
    )
}

private val QUICK = listOf(
    0xFF050508, 0xFF0F1419, 0xFFFFFFFF, 0xFFF4F5F7, 0xFF1D4ED8, 0xFF60A5FA,
    0xFF15803D, 0xFF4ADE80, 0xFFC2410C, 0xFFFB923C, 0xFF7C3AED, 0xFFE11D48,
).map { Color(it) }

/** Hue, saturation and brightness sliders, quick picks and a hex field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(
    title: String,
    initial: Color,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    val start = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val current = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    var hexText by remember { mutableStateOf(ThemeSpec.hex(initial)) }

    fun set(color: Color) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        hexText = ThemeSpec.hex(color)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        titleContentColor = colors.text,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatch(current, size = 52)
                    Spacer(Modifier.width(14.dp))
                    FfTextField(
                        value = hexText,
                        onValueChange = { t ->
                            hexText = t.take(7)
                            ThemeSpec.parseColor(t)?.let { c ->
                                val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                            }
                        },
                        label = "Hex",
                    )
                }
                PickerSlider("Hue", hue, 0f..360f) { hue = it; hexText = ThemeSpec.hex(Color(android.graphics.Color.HSVToColor(floatArrayOf(it, sat, value)))) }
                PickerSlider("Saturation", sat, 0f..1f) { sat = it; hexText = ThemeSpec.hex(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, it, value)))) }
                PickerSlider("Brightness", value, 0f..1f) { value = it; hexText = ThemeSpec.hex(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, it)))) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK.forEach { c ->
                        Box(Modifier.clip(CircleShape).clickable { set(c) }) { Swatch(c, size = 30) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(current) }) {
                Text("Use", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun PickerSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val colors = FfTheme.colors
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.surfaceHigh,
            ),
        )
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("FocusForce+ theme", text))
    Toast.makeText(context, "Theme copied", Toast.LENGTH_SHORT).show()
}

private fun paste(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

