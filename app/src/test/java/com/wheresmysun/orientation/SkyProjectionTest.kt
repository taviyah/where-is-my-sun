package com.wheresmysun.orientation

import com.wheresmysun.astro.SkyPath
import com.wheresmysun.astro.SkyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

class SkyProjectionTest {

    /** Phone held upright in portrait, camera facing north (world = R * device, row-major). */
    private val uprightNorth = floatArrayOf(
        1f, 0f, 0f,   // East  <- device x
        0f, 0f, -1f,  // North <- device -z
        0f, 1f, 0f,   // Up    <- device y
    )

    /** Upright, then turned to face [azimuth] degrees (clockwise from north). */
    private fun upright(azimuth: Double): FloatArray {
        val a = Math.toRadians(azimuth)
        // Device x points to azimuth + 90°, device -z to azimuth, device y up.
        val xE = cos(a); val xN = -sin(a)
        val fE = sin(a); val fN = cos(a)
        return floatArrayOf(
            xE.toFloat(), 0f, (-fE).toFloat(),
            xN.toFloat(), 0f, (-fN).toFloat(),
            0f, 1f, 0f,
        )
    }

    /** Lying flat on a table, screen up, top edge pointing north. Identity matrix. */
    private val flatNorth = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private fun projection(r: FloatArray, offset: Double = 0.0) =
        SkyProjection(r, offset, focalPx = 1000.0, width = 1080f, height = 2000f)

    @Test fun pointStraightAheadLandsInTheCentre() {
        val s = projection(uprightNorth).project(SkyPoint(0.0, 0.0))
        assertTrue(s.inFront)
        assertEquals(540f, s.x, 0.01f)
        assertEquals(1000f, s.y, 0.01f)
    }

    @Test fun higherIsUpAndEastIsRight() {
        val p = projection(uprightNorth)
        val up = p.project(SkyPoint(0.0, 10.0))
        assertTrue(up.y < 1000f)
        // tan(10°) * focal
        assertEquals(1000f - 176.3f, up.y, 0.5f)
        val right = p.project(SkyPoint(10.0, 0.0))
        assertTrue(right.x > 540f)
        assertEquals(1000f, right.y, 0.5f)
    }

    @Test fun pointBehindIsFlagged() {
        assertFalse(projection(uprightNorth).project(SkyPoint(180.0, 0.0)).inFront)
        assertFalse(projection(uprightNorth).isOnScreen(SkyPoint(180.0, 0.0)))
    }

    @Test fun declinationShiftsTheView() {
        // With 10° east declination, magnetic north is 10° true. A phone facing magnetic north
        // therefore looks at true azimuth 10°.
        val p = projection(uprightNorth, offset = 10.0)
        val s = p.project(SkyPoint(10.0, 0.0))
        assertEquals(540f, s.x, 0.01f)
        assertEquals(10.0, p.cameraDirection().azimuth, 1e-6)
    }

    @Test fun unprojectInvertsProject() {
        val p = projection(upright(123.0), offset = 4.5)
        for (target in listOf(SkyPoint(130.0, 20.0), SkyPoint(100.0, 5.0), SkyPoint(140.0, 35.0))) {
            val s = p.project(target)
            val back = p.unproject(s.x, s.y)
            assertEquals(0.0, SkyPath.signedDelta(target.azimuth, back.azimuth), 1e-4)
            assertEquals(target.altitude, back.altitude, 1e-4)
        }
    }

    @Test fun offscreenArrowPointsTheRightWay() {
        val p = projection(uprightNorth)
        // East (90°) is to the right: arrow angle ~0.
        assertEquals(0.0, p.offscreenDirection(SkyPoint(90.0, 0.0)), 1e-6)
        // West: arrow to the left.
        assertEquals(PI, abs(p.offscreenDirection(SkyPoint(270.0, 0.0))), 1e-6)
        // High in the sky: arrow up (negative screen y).
        assertEquals(-PI / 2, p.offscreenDirection(SkyPoint(0.0, 80.0)), 1e-6)
        // South-east, behind and to the right: still turn right.
        val behindRight = p.offscreenDirection(SkyPoint(160.0, 0.0))
        assertTrue(abs(behindRight) < PI / 2)
    }

    @Test fun headingWorksFlatAndUpright() {
        assertEquals(0.0, SkyProjection.heading(flatNorth, 0.0), 1e-6)
        assertEquals(0.0, SkyProjection.heading(uprightNorth, 0.0), 1e-6)
        assertEquals(123.0, SkyProjection.heading(upright(123.0), 0.0), 1e-4)
        assertEquals(133.0, SkyProjection.heading(upright(123.0), 10.0), 1e-4)
    }

    @Test fun cameraAltitude() {
        assertEquals(0.0, SkyProjection.cameraAltitude(uprightNorth), 1e-6)
        // Flat, screen up: the back camera looks at the ground.
        assertEquals(-90.0, SkyProjection.cameraAltitude(flatNorth), 1e-6)
    }

    @Test fun focalLengthFromOptics() {
        // 4.0 mm lens on a 6.4 mm wide sensor, 4:3 stream, 1080x1440 view (exactly 3:4).
        val optics = CameraOptics(4.0, 6.4, 4.0 / 3.0)
        assertEquals(4.0 * 1440 / 6.4, optics.focalPx(1080f, 1440f), 1e-6)
        // A taller view (1080x2400) crops the sides; the long side still spans the sensor.
        assertEquals(4.0 * 2400 / 6.4, optics.focalPx(1080f, 2400f), 1e-6)
        // A wider view (1080x1200) crops top and bottom; the image is scaled to fill the width.
        assertEquals(4.0 * 1440 / 6.4, optics.focalPx(1080f, 1200f), 1e-6)
    }

    @Test fun fallbackFovIsAbout66Degrees() {
        val f = CameraOptics.FALLBACK.focalPx(1500f, 2000f)
        val fov = Math.toDegrees(2 * atan(1000.0 / f))
        assertEquals(66.0, fov, 1e-6)
    }
}
