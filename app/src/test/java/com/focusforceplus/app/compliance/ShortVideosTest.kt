package com.focusforceplus.app.compliance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortVideosTest {

    @Test
    fun `a feed in a block goes by a name no app can have`() {
        ShortFeed.entries.forEach { feed ->
            assertEquals(feed, ShortFeed.fromRuleKey(feed.ruleKey))
            assertTrue(feed.ruleKey.startsWith(feed.packageName + "/"))
            // A slash is not allowed in a package name, so the app blocker never meets it.
            assertNull(ShortFeed.fromRuleKey(feed.packageName))
        }
        assertEquals("com.instagram.android/reels", ShortFeed.INSTAGRAM.ruleKey)
        assertEquals("com.google.android.youtube/shorts", ShortFeed.YOUTUBE.ruleKey)
    }

    private val minute = 60_000L
    private val today = 20_780L

    private fun used(instagram: Int = 0, youtube: Int = 0) = ShortVideoUsage(
        day = today,
        millis = mapOf(ShortFeed.INSTAGRAM to instagram * minute, ShortFeed.YOUTUBE to youtube * minute),
    )

    @Test
    fun withNoTimeAllowedAFeedIsAlwaysLeft() {
        assertTrue(shortVideoTimeIsUp(ShortFeed.INSTAGRAM, ShortVideoAllowance(0), ShortVideoUsage(today)))
        assertTrue(shortVideoTimeIsUp(ShortFeed.YOUTUBE, ShortVideoAllowance(0, each = true), used(youtube = 3)))
    }

    @Test
    fun oneTimeForAllIsUsedUpByAllOfThem() {
        val thirty = ShortVideoAllowance(30)
        assertFalse(shortVideoTimeIsUp(ShortFeed.INSTAGRAM, thirty, used(instagram = 10, youtube = 19)))
        // Twenty in one and ten in the other is thirty, for both.
        assertTrue(shortVideoTimeIsUp(ShortFeed.INSTAGRAM, thirty, used(instagram = 20, youtube = 10)))
        assertTrue(shortVideoTimeIsUp(ShortFeed.YOUTUBE, thirty, used(instagram = 20, youtube = 10)))
    }

    @Test
    fun withATimeForEachOneFeedDoesNotUseUpTheOther() {
        val each = ShortVideoAllowance(30, each = true)
        assertTrue(shortVideoTimeIsUp(ShortFeed.INSTAGRAM, each, used(instagram = 30, youtube = 5)))
        assertFalse(shortVideoTimeIsUp(ShortFeed.YOUTUBE, each, used(instagram = 30, youtube = 5)))
    }

    @Test
    fun theTimeIsUpExactlyAtTheLimit() {
        val five = ShortVideoAllowance(5, each = true)
        val almost = ShortVideoUsage(today, mapOf(ShortFeed.YOUTUBE to 5 * minute - 1))
        assertFalse(shortVideoTimeIsUp(ShortFeed.YOUTUBE, five, almost))
        assertTrue(shortVideoTimeIsUp(ShortFeed.YOUTUBE, five, almost.plus(ShortFeed.YOUTUBE, 1, today)))
    }

    @Test
    fun timeAddsUpWithinADay() {
        val usage = ShortVideoUsage()
            .plus(ShortFeed.INSTAGRAM, 90_000, today)
            .plus(ShortFeed.INSTAGRAM, 45_000, today)
            .plus(ShortFeed.YOUTUBE, 61_000, today)
        assertEquals(today, usage.day)
        assertEquals(2, usage.minutes(ShortFeed.INSTAGRAM))
        assertEquals(1, usage.minutes(ShortFeed.YOUTUBE))
        assertEquals(3, usage.totalMinutes)
    }

    @Test
    fun aNewDayStartsAtZero() {
        val yesterday = used(instagram = 29, youtube = 12)
        assertEquals(0, yesterday.on(today + 1).totalMinutes)
        val next = yesterday.plus(ShortFeed.YOUTUBE, 2 * minute, today + 1)
        assertEquals(today + 1, next.day)
        assertEquals(0, next.minutes(ShortFeed.INSTAGRAM))
        assertEquals(2, next.minutes(ShortFeed.YOUTUBE))
        // Nothing to add still moves the numbers on to the new day.
        assertEquals(today + 1, yesterday.plus(ShortFeed.YOUTUBE, 0, today + 1).day)
        assertFalse(shortVideoTimeIsUp(ShortFeed.INSTAGRAM, ShortVideoAllowance(30), yesterday.on(today + 1)))
    }

    @Test
    fun moreTimeIsALooseningAndLessIsNot() {
        val none = ShortVideoAllowance(0)
        val thirty = ShortVideoAllowance(30)
        assertTrue(loosensShortVideos(none, thirty))
        assertTrue(loosensShortVideos(thirty, ShortVideoAllowance(45)))
        assertTrue(loosensShortVideos(thirty, ShortVideoAllowance(30, each = true)))
        assertFalse(loosensShortVideos(thirty, none))
        assertFalse(loosensShortVideos(thirty, ShortVideoAllowance(15)))
        assertFalse(loosensShortVideos(ShortVideoAllowance(30, each = true), thirty))
        assertFalse(loosensShortVideos(thirty, thirty))
        // Each feed on its own with less time can still be more in total: that counts.
        assertTrue(loosensShortVideos(thirty, ShortVideoAllowance(20, each = true)))
    }

    @Test
    fun theCatalogueKeepsFeedsAndWholeAppsApart() {
        // An app with a feed in it is never offered as "block the whole app".
        ShortFeed.entries.forEach { assertFalse(it.packageName in SHORT_VIDEO_ONLY_APPS) }
        // And nothing that must never be blocked is in the list.
        assertTrue(SHORT_VIDEO_ONLY_APPS.none { it in NEVER_BLOCK_PACKAGES })
    }
}
