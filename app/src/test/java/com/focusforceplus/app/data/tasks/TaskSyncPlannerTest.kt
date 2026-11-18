package com.focusforceplus.app.data.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSyncPlannerTest {

    private val nine = 1_800_000_000_000L

    private fun fields(title: String = "Call mum", due: Long? = nine, done: Boolean = false, priority: Int = 1, note: String? = null) =
        SyncFields(title, due, done, priority, note)

    private fun linked(id: Long, remoteId: Long, here: SyncFields, base: SyncFields = here, inTrash: Boolean = false) =
        LocalTask(id, remoteId, base.fingerprint(), here, inTrash)

    @Test
    fun `nothing to do when both sides still agree`() {
        val f = fields()
        assertEquals(emptyList<SyncAction>(), TaskSyncPlanner.plan(listOf(linked(1, 10, f)), listOf(RemoteTask(10, f)), setOf(10)))
    }

    @Test
    fun `a change here is pushed`() {
        val base = fields()
        val here = base.copy(title = "Call mum back")
        val plan = TaskSyncPlanner.plan(listOf(linked(1, 10, here, base)), listOf(RemoteTask(10, base)), setOf(10))
        assertEquals(listOf(SyncAction.PushUpdate(1, 10, here)), plan)
    }

    @Test
    fun `a change there is pulled`() {
        val base = fields()
        val there = base.copy(done = true)
        val plan = TaskSyncPlanner.plan(listOf(linked(1, 10, base, base)), listOf(RemoteTask(10, there)), setOf(10))
        assertEquals(listOf(SyncAction.PullUpdate(1, 10, there)), plan)
    }

    @Test
    fun `when both changed, FocusForce+ wins`() {
        val base = fields()
        val here = base.copy(priority = 2)
        val there = base.copy(title = "Something else")
        val plan = TaskSyncPlanner.plan(listOf(linked(1, 10, here, base)), listOf(RemoteTask(10, there)), setOf(10))
        assertEquals(listOf(SyncAction.PushUpdate(1, 10, here)), plan)
    }

    @Test
    fun `the same change on both sides is only remembered`() {
        val base = fields()
        val both = base.copy(due = nine + 3_600_000)
        val plan = TaskSyncPlanner.plan(listOf(linked(1, 10, both, base)), listOf(RemoteTask(10, both)), setOf(10))
        assertEquals(listOf(SyncAction.Link(1, 10, both.fingerprint())), plan)
    }

    @Test
    fun `seconds and spaces do not count as a change`() {
        val base = fields(title = "Call mum")
        val there = base.copy(title = " Call mum ", due = nine + 42_000)
        val plan = TaskSyncPlanner.plan(listOf(linked(1, 10, base, base)), listOf(RemoteTask(10, there)), setOf(10))
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `trash here deletes there, a task deleted there trashes the todo`() {
        val f = fields()
        val trashed = TaskSyncPlanner.plan(listOf(linked(1, 10, f, inTrash = true)), listOf(RemoteTask(10, f)), setOf(10))
        assertEquals(listOf(SyncAction.DeleteRemote(1, 10)), trashed)
        val gone = TaskSyncPlanner.plan(listOf(linked(1, 10, f)), emptyList(), setOf(10))
        assertEquals(listOf(SyncAction.TrashLocal(1)), gone)
    }

    @Test
    fun `a done todo whose task is gone is only unlinked`() {
        val f = fields(done = true)
        assertEquals(listOf(SyncAction.Unlink(1)), TaskSyncPlanner.plan(listOf(linked(1, 10, f)), emptyList(), setOf(10)))
    }

    @Test
    fun `new open items move across, done ones and ones seen before do not`() {
        val local = listOf(
            LocalTask(1, null, null, fields(title = "New here"), inTrash = false),
            LocalTask(2, null, null, fields(title = "Done here", done = true), inTrash = false),
        )
        val remote = listOf(
            RemoteTask(20, fields(title = "New there")),
            RemoteTask(21, fields(title = "Done there", done = true)),
            RemoteTask(22, fields(title = "Seen before")),
        )
        val plan = TaskSyncPlanner.plan(local, remote, seenRemoteIds = setOf(22))
        assertEquals(
            listOf(
                SyncAction.PushNew(1, fields(title = "New here")),
                SyncAction.PullNew(20, fields(title = "New there")),
            ),
            plan,
        )
    }

    @Test
    fun `the same open item on both sides is linked, not copied`() {
        val here = fields(title = "Pay rent", priority = 2)
        val there = fields(title = "pay rent", priority = 1)
        val plan = TaskSyncPlanner.plan(listOf(LocalTask(1, null, null, here, false)), listOf(RemoteTask(30, there)), emptySet())
        assertEquals(
            listOf(SyncAction.Link(1, 30, here.fingerprint()), SyncAction.PushUpdate(1, 30, here)),
            plan,
        )
    }

    @Test
    fun `two todos on one task - the matching one keeps it, the copy is pushed as new`() {
        val original = fields(title = "Water the plants", due = null)
        val copy = original.copy(title = "Water the plants (copy)")
        val plan = TaskSyncPlanner.plan(
            listOf(linked(4, 2, original), LocalTask(10, 2, original.fingerprint(), copy, inTrash = false)),
            listOf(RemoteTask(2, original)),
            setOf(2),
        )
        assertEquals(listOf(SyncAction.Unlink(10), SyncAction.PushNew(10, copy)), plan)
    }

    @Test
    fun `priorities map both ways and survive a round trip`() {
        for (p in 0..2) assertEquals(p, TaskSyncPlanner.fromRemotePriority(TaskSyncPlanner.toRemotePriority(p)))
        assertEquals(2, TaskSyncPlanner.fromRemotePriority(3))
        assertEquals(1, TaskSyncPlanner.fromRemotePriority(0))
        assertEquals(0, TaskSyncPlanner.fromRemotePriority(7))
    }
}
