package com.cdnhunter.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The connect line's motion is pure maths, so its character is pinned here rather than by eye. */
class LoaderMotionTest {

    private val rev = LoaderMotion.ConnectRevMs / 1000f
    private val steps = 600

    private fun heads() = (0..steps).map { LoaderMotion.headDegrees(it * rev / steps) }
    private fun lengths() = (0..steps).map { LoaderMotion.lengthDegrees(it * rev / steps) }
    private fun speeds() = heads().zipWithNext { a, b -> (b - a) / (rev / steps) }

    @Test fun revolutionIsInTheSpecifiedWindow() {
        assertTrue(LoaderMotion.ConnectRevMs in 1200..1600)
    }

    @Test fun lineNeverStopsOrReverses() {
        assertTrue(speeds().all { it > 0f })
    }

    @Test fun speedVariesClearlyButNotExtremely() {
        val s = speeds()
        val ratio = s.max() / s.min()
        assertTrue("ratio $ratio", ratio in 3f..6f)
    }

    @Test fun lengthStaysWithinBoundsAndNeverBecomesARing() {
        val l = lengths()
        assertTrue(l.min() >= LoaderMotion.MIN_LENGTH - 0.01f)
        assertTrue(l.max() <= LoaderMotion.MAX_LENGTH + 0.01f)
        assertTrue(l.max() < 360f * 0.4f)
        assertTrue(l.max() - l.min() > 50f) // visibly changes
    }

    @Test fun loopHasNoSeam() {
        assertEquals(LoaderMotion.headDegrees(0f) + 360f, LoaderMotion.headDegrees(rev), 0.01f)
        assertEquals(LoaderMotion.lengthDegrees(0f), LoaderMotion.lengthDegrees(rev - 1e-4f), 0.5f)
        val s = speeds()
        assertEquals(s.first(), s.last(), s.first() * 0.05f) // speed continues across the wrap
    }

    @Test fun lengthIsNotLockedToTheSpeed() {
        val speedPeak = speeds().let { it.indexOf(it.max()) }
        val lengthPeak = lengths().let { it.indexOf(it.max()) }
        assertTrue("speed peak $speedPeak, length peak $lengthPeak", lengthPeak > speedPeak + steps / 20)
    }
}
