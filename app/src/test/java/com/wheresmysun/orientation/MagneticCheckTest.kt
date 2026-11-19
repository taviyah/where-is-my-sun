package com.wheresmysun.orientation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class MagneticCheckTest {

    // Roughly central Israel: ~44 µT, dipping ~44° into the ground.
    private val expected = ExpectedField(44.0, 44.0)

    @Test fun inclinationOfAFieldPointingNorthAndDown() {
        val dip = Math.toRadians(44.0)
        val world = Vec3(0.0, 44 * cos(dip), -44 * sin(dip))
        assertEquals(44.0, MagneticCheck.inclination(world), 1e-9)
        // Southern hemisphere: the field points up out of the ground.
        assertEquals(-30.0, MagneticCheck.inclination(Vec3(0.0, cos(Math.toRadians(30.0)), sin(Math.toRadians(30.0)))), 1e-9)
    }

    @Test fun cleanFieldPasses() {
        assertFalse(MagneticCheck.isDisturbed(MeasuredField(46.0, 45.0), expected, wasDisturbed = false))
    }

    @Test fun strongerFieldNearACarIsFlagged() {
        assertTrue(MagneticCheck.isDisturbed(MeasuredField(60.0, 45.0), expected, wasDisturbed = false))
    }

    @Test fun wrongDipIsFlaggedEvenWithTheRightStrength() {
        assertTrue(MagneticCheck.isDisturbed(MeasuredField(44.0, 58.0), expected, wasDisturbed = false))
    }

    @Test fun hysteresisKeepsTheWarningUntilClearlyBetter() {
        // 15% off: not enough to raise the warning, but not enough to clear it either.
        val borderline = MeasuredField(44.0 * 1.15, 44.0)
        assertFalse(MagneticCheck.isDisturbed(borderline, expected, wasDisturbed = false))
        assertTrue(MagneticCheck.isDisturbed(borderline, expected, wasDisturbed = true))
        assertFalse(MagneticCheck.isDisturbed(MeasuredField(45.0, 45.0), expected, wasDisturbed = true))
    }
}
