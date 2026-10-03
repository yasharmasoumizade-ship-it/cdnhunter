package com.cdnhunter.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pull's resistance curve is pure maths: pinned here so the gesture's feel cannot drift. */
class PullMathTest {
    private val max = 96f
    private val threshold = 60f

    @Test fun startsAtZeroAndFollowsTheFingerClosely() {
        assertEquals(0f, PullMath.offset(0f, max), 0f)
        assertEquals(PullMath.SLOPE, PullMath.offset(1f, max) / 1f, 0.01f)
    }

    @Test fun neverPassesTheMaximumAndAlwaysGrows() {
        var prev = -1f
        for (raw in 0..2000 step 10) {
            val o = PullMath.offset(raw.toFloat(), max)
            assertTrue(o <= max)
            assertTrue(o >= prev)
            prev = o
        }
    }

    @Test fun thresholdIsReachableWithAShortPull() {
        var raw = 0f
        while (PullMath.offset(raw, max) < threshold) raw += 1f
        assertTrue("finger distance $raw", raw in 60f..200f)
    }

    @Test fun inverseRoundTrips() {
        for (raw in listOf(0f, 12f, 80f, 140f, 300f)) {
            assertEquals(raw, PullMath.inverse(PullMath.offset(raw, max), max), raw * 0.02f + 0.1f)
        }
    }

    @Test fun holdSitsBelowTheThresholdAndInsideTheGap() {
        assertTrue(PullHold.value < PullThreshold.value)
        assertTrue(PullThreshold.value < PullMax.value)
    }
}
