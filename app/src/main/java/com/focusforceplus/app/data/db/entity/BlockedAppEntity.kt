package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One app-blocking rule.
 *
 * Blocking semantics (evaluated in `compliance/BlockDecision.kt`):
 * - No limit and no window → hard manual block whenever the rule is enabled.
 * - [dailyLimitMinutes] set → app is allowed until today's usage reaches the limit.
 * - Window set → app is blocked only between [windowStartMinutes] and [windowEndMinutes]
 *   (minutes since midnight; capped at 12h per ComplianceLimits.MAX_APP_BLOCK_WINDOW).
 * - [blockDuringRoutines] / [blockDuringFocus] → additionally blocked while a routine
 *   with app blocking / a focus session with app blocking is running.
 *
 * [invincibleMode] locks the rule only while it is in its ACTIVE state (limit reached
 * or inside the window) and releases at the natural end (midnight / window end) —
 * see `.claude/PLAY_STORE_COMPLIANCE.md` section 2.1.
 */
@Entity(
    tableName = "blocked_apps",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class BlockedAppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    /** null = hard block (no daily allowance), value = daily limit in minutes */
    val dailyLimitMinutes: Int? = null,
    val usedTodayMinutes: Int = 0,
    val isBlocked: Boolean = true,
    val blockDuringRoutines: Boolean = true,
    val blockDuringFocus: Boolean = true,
    /** Locks this rule while it is actively blocking (limit hit / inside window). */
    val invincibleMode: Boolean = false,
    /** Time-window blocking: start, minutes since midnight (null = no window). */
    val windowStartMinutes: Int? = null,
    /** Time-window blocking: end, minutes since midnight (null = no window). */
    val windowEndMinutes: Int? = null,
    /** Epoch millis until which a temporary 5-minute exception suspends this rule. */
    val exceptionUntilMillis: Long = 0,
    /** Exceptions granted today (resets at midnight, capped at 2 per day). */
    val exceptionsUsedToday: Int = 0,
    /** Optional user-defined group label, e.g. "Social Media". */
    val groupName: String? = null,
)
