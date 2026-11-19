package com.focusforceplus.app.data.tasks

/**
 * What both sides agree on: the parts of a todo that a tasks app knows too.
 * Checklists, snoozes and reminders stay in FocusForce+.
 */
data class SyncFields(
    val title: String,
    /** Minute precision; seconds never decide whether something changed. */
    val due: Long?,
    val done: Boolean,
    /** FocusForce+'s scale: 0 low, 1 medium, 2 high. */
    val priority: Int,
    val note: String?,
) {
    /**
     * Both sides in the form a todo can hold. A todo's title is cut at
     * [MAX_TITLE] characters and is never empty, so a longer or empty title in the
     * tasks app must compare as equal to what was made of it here. Otherwise the
     * shortened copy would look like an edit and be written back over the original.
     */
    fun normalized() = copy(
        title = title.trim().take(MAX_TITLE).ifEmpty { NO_TITLE },
        due = due?.let { it / 60_000L * 60_000L },
        priority = priority.coerceIn(0, 2),
        note = note?.trim()?.ifEmpty { null },
    )

    /** A short fingerprint, stored after every sync to see later which side changed. */
    fun fingerprint(): String {
        val n = normalized()
        val text = "${n.title}\u0001${n.due}\u0001${n.done}\u0001${n.priority}\u0001${n.note.orEmpty()}"
        var hash = -0x340d631b7bdddcdbL // FNV-1a 64
        for (ch in text) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L
        }
        return java.lang.Long.toHexString(hash)
    }

    companion object {
        const val MAX_TITLE = 200
        const val NO_TITLE = "(No title)"
    }
}

/** A todo as the planner sees it. */
data class LocalTask(
    val id: Long,
    val remoteId: Long?,
    /** Fingerprint when both sides last agreed. */
    val synced: String?,
    val fields: SyncFields,
    val inTrash: Boolean,
)

/** A task in the tasks app's list. */
data class RemoteTask(val id: Long, val fields: SyncFields)

sealed interface SyncAction {
    /** A todo the tasks app does not have yet. */
    data class PushNew(val localId: Long, val fields: SyncFields) : SyncAction
    data class PushUpdate(val localId: Long, val remoteId: Long, val fields: SyncFields) : SyncAction
    /** A task FocusForce+ does not have yet. */
    data class PullNew(val remoteId: Long, val fields: SyncFields) : SyncAction
    data class PullUpdate(val localId: Long, val remoteId: Long, val fields: SyncFields) : SyncAction
    /** The todo went to the trash here: remove the task there. */
    data class DeleteRemote(val localId: Long, val remoteId: Long) : SyncAction
    /** The task was deleted there: the todo goes to the trash here (it can be restored). */
    data class TrashLocal(val localId: Long) : SyncAction
    /** Forget the link (the other side is gone, nothing else to do). */
    data class Unlink(val localId: Long) : SyncAction
    /** Same on both sides already (or matched by title and time): remember it. */
    data class Link(val localId: Long, val remoteId: Long, val fingerprint: String) : SyncAction
}

/**
 * Plans one two-way sync. Pure: no database, no provider, so every rule is tested.
 *
 * - A linked pair compares both sides with the fingerprint from the last sync:
 *   whichever side changed wins; when both changed, FocusForce+ wins.
 * - A todo in the trash removes its task; a task deleted there trashes its todo.
 * - Open todos without a link are pushed; open tasks the planner has never seen
 *   ([seenRemoteIds]) are pulled. A task seen before but not linked is left
 *   alone: it was done when it came, or its todo was deleted for good, and
 *   deleting someone's tasks on a guess is worse than leaving one behind.
 * - On both sides, an open item with the same title and time is linked instead
 *   of copied, so a first sync does not make doubles.
 */
object TaskSyncPlanner {

    fun plan(allLocal: List<LocalTask>, remote: List<RemoteTask>, seenRemoteIds: Set<Long>): List<SyncAction> {
        val actions = mutableListOf<SyncAction>()
        val remoteById = remote.associateBy { it.id }

        // Two todos on one task (an older copy that took the link along): the one
        // that still matches the last sync keeps it, the others start over unlinked.
        val local = allLocal.groupBy { it.remoteId }.flatMap { (remoteId, group) ->
            if (remoteId == null || group.size == 1) return@flatMap group
            val keeper = group.firstOrNull { it.synced == it.fields.fingerprint() && !it.inTrash } ?: group.minBy { it.id }
            group.map { todo ->
                if (todo === keeper) todo
                else {
                    actions += SyncAction.Unlink(todo.id)
                    todo.copy(remoteId = null, synced = null)
                }
            }
        }
        val linkedRemoteIds = local.mapNotNull { it.remoteId }.toSet()

        // ── Linked pairs ─────────────────────────────────────────────────────
        for (todo in local) {
            val remoteId = todo.remoteId ?: continue
            val task = remoteById[remoteId]
            when {
                todo.inTrash -> actions += if (task != null) SyncAction.DeleteRemote(todo.id, remoteId) else SyncAction.Unlink(todo.id)
                task == null -> actions += if (todo.fields.done) SyncAction.Unlink(todo.id) else SyncAction.TrashLocal(todo.id)
                else -> {
                    val here = todo.fields.fingerprint()
                    val there = task.fields.fingerprint()
                    val base = todo.synced
                    when {
                        here == there -> if (base != here) actions += SyncAction.Link(todo.id, remoteId, here)
                        base != null && here == base -> actions += SyncAction.PullUpdate(todo.id, remoteId, task.fields)
                        else -> actions += SyncAction.PushUpdate(todo.id, remoteId, todo.fields)
                    }
                }
            }
        }

        // ── New on either side ───────────────────────────────────────────────
        val newRemote = remote.filter { it.id !in linkedRemoteIds && it.id !in seenRemoteIds && !it.fields.done }
            .toMutableList()
        for (todo in local) {
            if (todo.remoteId != null || todo.inTrash || todo.fields.done) continue
            val key = matchKey(todo.fields)
            val twin = newRemote.firstOrNull { matchKey(it.fields) == key }
            if (twin != null) {
                newRemote.remove(twin)
                actions += SyncAction.Link(todo.id, twin.id, todo.fields.fingerprint())
                if (twin.fields.fingerprint() != todo.fields.fingerprint()) {
                    actions += SyncAction.PushUpdate(todo.id, twin.id, todo.fields)
                }
            } else {
                actions += SyncAction.PushNew(todo.id, todo.fields)
            }
        }
        newRemote.forEach { actions += SyncAction.PullNew(it.id, it.fields) }
        return actions
    }

    private fun matchKey(fields: SyncFields): String {
        val n = fields.normalized()
        return n.title.lowercase() + "\u0001" + n.due
    }

    // ── Priority: FocusForce+ 0..2, iCalendar 1 (highest) .. 9 (lowest), 0 = none ──

    fun toRemotePriority(local: Int): Int = when (local) {
        2 -> 1
        0 -> 9
        else -> 5
    }

    fun fromRemotePriority(remote: Int): Int = when (remote) {
        in 1..4 -> 2
        in 6..9 -> 0
        else -> 1
    }
}
