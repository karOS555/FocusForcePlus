package com.focusforceplus.app.ui.screens.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricTest {

    private fun metric(vararg values: Int) = Metric(
        days = values.mapIndexed { i, v -> DayStat("d$i", v, isToday = i == values.lastIndex) },
    )

    @Test
    fun `today does not pull the average down while it is still running`() {
        // Six finished days of an hour each, and ten minutes so far today.
        assertEquals(60, metric(60, 60, 60, 60, 60, 60, 10).average)
    }

    @Test
    fun `empty days before first use do not count`() {
        assertEquals(40, metric(0, 0, 0, 30, 50, 0, 5).average)
    }

    @Test
    fun `on the first day today is all there is`() {
        assertEquals(25, metric(0, 0, 0, 0, 0, 0, 25).average)
        assertEquals(0, metric(0, 0, 0, 0, 0, 0, 0).average)
    }
}
