package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An app group for the blocker (e.g. "Social Media"). Membership lives on
 * [BlockedAppEntity.groupName] (matched by name).
 *
 * What a group does:
 * - [sharedDailyLimitMinutes]: one combined daily budget for ALL member apps —
 *   once their summed usage reaches it, every member is blocked until midnight.
 * - Blocking schedule ([windowStartMinutes]/[windowEndMinutes] on [windowDaysCsv]):
 *   every member app is blocked inside the window on the selected days. Capped at
 *   12h per ComplianceLimits.MAX_APP_BLOCK_WINDOW; overnight windows wrap.
 * - Focus sessions can select groups as their blocking scope.
 *
 * Grouped apps are governed by the group (shared limit + schedule + session
 * selection) plus their own limits/windows; the plain "always blocked" mode
 * applies only to ungrouped apps.
 *
 * [invincibleMode] follows the standard state machine: it locks the group (and
 * its members' escape hatches) only while the shared limit is reached (natural
 * end at midnight) or the schedule window is running (natural end: window end).
 * Weakening an invincible group is additionally Tamper-Protection gated.
 */
@Entity(
    tableName = "blocker_groups",
    indices = [Index(value = ["name"], unique = true)]
)
data class BlockerGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Combined daily budget in minutes for all member apps; null = no shared limit. */
    val sharedDailyLimitMinutes: Int? = null,
    val invincibleMode: Boolean = false,
    /** Schedule window start, minutes since midnight (null = no schedule). */
    val windowStartMinutes: Int? = null,
    /** Schedule window end, minutes since midnight (null = no schedule). */
    val windowEndMinutes: Int? = null,
    /** Days the schedule applies, CSV of routine day codes ("MO,DI,…"); null = every day. */
    val windowDaysCsv: String? = null,
)
