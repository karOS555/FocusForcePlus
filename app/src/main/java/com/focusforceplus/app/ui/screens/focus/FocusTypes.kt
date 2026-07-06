package com.focusforceplus.app.ui.screens.focus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Focus session types are *presets*, not behavior switches: picking one suggests a
 * duration and toggle setup that fits the activity, and color-codes the session in
 * lists. Everything a preset sets can still be changed freely afterwards —
 * behavior always comes from the actual toggles, never from the type itself.
 */
data class FocusTypeMeta(
    val key: String,
    val label: String,
    /** Shown under the type chips in the editor. */
    val caption: String,
    val color: Color,
    val icon: ImageVector,
    /** null = no preset (Custom keeps whatever is currently configured). */
    val preset: FocusTypePreset?,
)

data class FocusTypePreset(
    val durationMinutes: Int,
    val enableDnd: Boolean,
    val blockNotifications: Boolean,
    val appBlockerEnabled: Boolean,
    /** Blocker group names this type suggests blocking, matched case-insensitively
     *  against existing groups; empty = block all configured apps. */
    val suggestedGroups: List<String> = emptyList(),
)

object FocusTypes {

    val STUDY = FocusTypeMeta(
        key = "STUDY",
        label = "Study",
        caption = "Study — 45 min with Do Not Disturb and app blocking; " +
            "notifications stay visible for study tools.",
        color = Color(0xFF60a5fa),
        icon = Icons.Filled.School,
        preset = FocusTypePreset(
            durationMinutes = 45,
            enableDnd = true,
            blockNotifications = false,
            appBlockerEnabled = true,
            suggestedGroups = listOf("Social Media", "Games", "Entertainment"),
        ),
    )

    val WORK = FocusTypeMeta(
        key = "WORK",
        label = "Work",
        caption = "Work — 50 min of total silence: DND, no notifications at all, " +
            "and app blocking.",
        color = Color(0xFF34d399),
        icon = Icons.Filled.Work,
        preset = FocusTypePreset(
            durationMinutes = 50,
            enableDnd = true,
            blockNotifications = true,
            appBlockerEnabled = true,
            suggestedGroups = listOf("Social Media", "Games", "Entertainment"),
        ),
    )

    val CREATIVE = FocusTypeMeta(
        key = "CREATIVE",
        label = "Creative",
        caption = "Creative — a long 90 min flow block with DND and app blocking, " +
            "notifications visible.",
        color = Color(0xFFa78bfa),
        icon = Icons.Filled.Palette,
        preset = FocusTypePreset(
            durationMinutes = 90,
            enableDnd = true,
            blockNotifications = false,
            appBlockerEnabled = true,
            suggestedGroups = listOf("Social Media", "Games"),
        ),
    )

    val CUSTOM = FocusTypeMeta(
        key = "CUSTOM",
        label = "Custom",
        caption = "Custom — no preset; configure everything below yourself.",
        color = Color(0xFF94a3b8),
        icon = Icons.Filled.Tune,
        preset = null,
    )

    val ALL = listOf(STUDY, WORK, CREATIVE, CUSTOM)

    fun of(key: String?): FocusTypeMeta = ALL.firstOrNull { it.key == key } ?: CUSTOM
}
