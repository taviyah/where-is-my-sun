package com.wheresmysun.orientation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagneticMonitorTest {
    // Values from the HiBreak log, central Israel.
    private val expected = ExpectedField(45.6, 49.2)

    /** Feeds [seconds] of 16 Hz readings starting at [fromMs]; returns the time after the last one. */
    private fun MagneticMonitor.feed(strength: Double, dip: Double, speed: Double, fromMs: Long, seconds: Double): Long {
        var t = fromMs
        val end = fromMs + (seconds * 1000).toLong()
        while (t < end) {
            onReading(strength, dip, speed, expected, t)
            t += 62
        }
        return t
    }

    @Test fun flagsAStillPhoneInADisturbedField() {
        val m = MagneticMonitor()
        // Before calibration: 41.5 µT, dip 60°, phone at rest.
        m.feed(41.5, 60.0, speed = 2.0, fromMs = 0, seconds = 4.0)
        assertTrue(m.disturbed)
    }

    @Test fun ignoresWildReadingsWhileTurning() {
        val m = MagneticMonitor()
        var t = m.feed(41.5, 48.0, speed = 2.0, fromMs = 0, seconds = 4.0)
        assertFalse(m.disturbed)
        // A figure-8: dip swings all over the place, but the phone is turning fast.
        for (dip in listOf(-41.0, 57.0, 3.0, 35.0, -15.0, 24.0)) {
            t = m.feed(70.0, dip, speed = 120.0, fromMs = t, seconds = 0.5)
        }
        assertFalse(m.disturbed)
    }

    @Test fun clearsOnceTheFieldIsCleanAgain() {
        val m = MagneticMonitor()
        var t = m.feed(41.5, 60.0, speed = 2.0, fromMs = 0, seconds = 4.0)
        assertTrue(m.disturbed)
        // Calibrated: the HiBreak then read ~42 µT, dip ~47°.
        t = m.feed(42.5, 47.0, speed = 3.0, fromMs = t, seconds = 1.5)
        t = m.feed(42.5, 47.0, speed = 3.0, fromMs = t, seconds = 3.0)
        assertFalse(m.disturbed)
    }

    @Test fun aSingleOddCheckDoesNotFlipTheVerdict() {
        val m = MagneticMonitor()
        var t = m.feed(44.0, 49.0, speed = 2.0, fromMs = 0, seconds = 3.0)
        // Under a second of a bad reading, then clean again: not enough agreeing checks.
        t = m.feed(70.0, 30.0, speed = 2.0, fromMs = t, seconds = 0.9)
        m.feed(44.0, 49.0, speed = 2.0, fromMs = t, seconds = 3.0)
        assertFalse(m.disturbed)
    }

    @Test fun nearALaptopIsFlagged() {
        val m = MagneticMonitor()
        // End of the HiBreak log: 56-66 µT, dip ~30°, phone resting near a laptop.
        m.feed(63.0, 31.0, speed = 1.0, fromMs = 0, seconds = 5.0)
        assertTrue(m.disturbed)
    }
}
