package com.cdnhunter.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The travelling light's seam handling and brightness ramp are plain arithmetic: pinned here. */
class PerimeterLightTest {

    private val out = FloatArray(4)

    @Test fun aStretchInsideTheOutlineIsOnePiece() {
        val n = wrapSegment(10f, 30f, 100f, out)
        assertEquals(1, n)
        assertEquals(10f, out[0], 0f)
        assertEquals(30f, out[1], 0f)
    }

    @Test fun aStretchPastTheEndWrapsToTheStart() {
        val n = wrapSegment(90f, 110f, 100f, out)
        assertEquals(2, n)
        assertEquals(90f, out[0], 0f)
        assertEquals(100f, out[1], 0f)
        assertEquals(0f, out[2], 0f)
        assertEquals(10f, out[3], 0f)
    }

    @Test fun aStretchBeforeTheStartComesInFromTheEnd() {
        val n = wrapSegment(-5f, 5f, 100f, out)
        assertEquals(2, n)
        assertEquals(95f, out[0], 0f)
        assertEquals(100f, out[1], 0f)
        assertEquals(0f, out[2], 0f)
        assertEquals(5f, out[3], 0f)
    }

    @Test fun wrappedPiecesAddUpToTheOriginalLength() {
        for (start in listOf(-40f, -1f, 0f, 55f, 99f, 130f, 260f)) {
            val n = wrapSegment(start, start + 12f, 100f, out)
            var sum = 0f
            for (k in 0 until n) sum += out[k * 2 + 1] - out[k * 2]
            assertEquals("start $start", 12f, sum, 1e-3f)
        }
    }

    @Test fun lightGrowsBrighterTowardTheHead() {
        val a = (0..7).map { pieceAlpha(it, 0.9f) }
        assertTrue(a.zipWithNext().all { (x, y) -> y > x })
        assertEquals(0.9f, a.last(), 1e-6f)
        assertTrue(a.first() < 0.05f)
    }
}
