package com.wheresmysun.orientation

import com.wheresmysun.astro.SkyPath
import com.wheresmysun.astro.SkyPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Double, val y: Double, val z: Double) {
    fun normalized(): Vec3 {
        val n = sqrt(x * x + y * y + z * z)
        return if (n == 0.0) this else Vec3(x / n, y / n, z / n)
    }
}

/** Where a sky point lands on screen. [inFront] is false when it is behind the camera. */
data class ScreenPoint(val x: Float, val y: Float, val inFront: Boolean)

/**
 * Maps sky directions to screen pixels and back.
 *
 * Coordinate frames:
 *  - World: x = East, y = North, z = Up, with "North" being whatever the rotation sensor uses
 *    (magnetic north). [northOffsetDeg] turns true azimuths into that frame: it is the magnetic
 *    declination plus any manual alignment, so frameAzimuth = trueAzimuth - northOffsetDeg.
 *  - Device: Android's sensor frame. x to the right of the screen, y to the top of the screen,
 *    z out of the screen towards the user. The back camera looks along -z.
 *
 * [rotation] is the row-major 3x3 matrix from SensorManager.getRotationMatrix*, which maps
 * device vectors into world vectors (world = R * device).
 */
class SkyProjection(
    private val rotation: FloatArray,
    val northOffsetDeg: Double,
    /** Camera focal length in screen pixels. */
    val focalPx: Double,
    val width: Float,
    val height: Float,
) {
    private val cx = width / 2.0
    private val cy = height / 2.0

    fun toWorld(p: SkyPoint): Vec3 {
        val az = Math.toRadians(p.azimuth - northOffsetDeg)
        val alt = Math.toRadians(p.altitude)
        return Vec3(cos(alt) * sin(az), cos(alt) * cos(az), sin(alt))
    }

    fun worldToDevice(w: Vec3): Vec3 {
        val r = rotation
        return Vec3(
            r[0] * w.x + r[3] * w.y + r[6] * w.z,
            r[1] * w.x + r[4] * w.y + r[7] * w.z,
            r[2] * w.x + r[5] * w.y + r[8] * w.z,
        )
    }

    fun deviceToWorld(d: Vec3): Vec3 {
        val r = rotation
        return Vec3(
            r[0] * d.x + r[1] * d.y + r[2] * d.z,
            r[3] * d.x + r[4] * d.y + r[5] * d.z,
            r[6] * d.x + r[7] * d.y + r[8] * d.z,
        )
    }

    fun worldToSky(w: Vec3): SkyPoint {
        val n = w.normalized()
        val az = Math.toDegrees(atan2(n.x, n.y)) + northOffsetDeg
        return SkyPoint(SkyPath.normalize(az), Math.toDegrees(asin(n.z.coerceIn(-1.0, 1.0))))
    }

    /** Projects a sky point onto the screen. Points behind the camera still get coordinates (mirrored),
     *  flagged with inFront = false, so callers can decide what to do with them. */
    fun project(p: SkyPoint): ScreenPoint {
        val d = worldToDevice(toWorld(p))
        val forward = -d.z
        val safe = if (forward > 1e-6) forward else 1e-6
        val x = cx + focalPx * d.x / safe
        val y = cy - focalPx * d.y / safe
        return ScreenPoint(x.toFloat(), y.toFloat(), forward > 1e-6)
    }

    fun isOnScreen(p: SkyPoint, margin: Float = 0f): Boolean {
        val s = project(p)
        return s.inFront && s.x >= -margin && s.x <= width + margin && s.y >= -margin && s.y <= height + margin
    }

    /** The sky direction under a screen pixel. Used by tap-to-align. */
    fun unproject(x: Float, y: Float): SkyPoint {
        val d = Vec3((x - cx) / focalPx, -(y - cy) / focalPx, -1.0)
        return worldToSky(deviceToWorld(d))
    }

    /**
     * Direction to turn the phone to bring [p] into view, as an angle on screen in radians
     * (0 = right, PI/2 = down, screen convention). Works for points behind the camera too.
     */
    fun offscreenDirection(p: SkyPoint): Double {
        val d = worldToDevice(toWorld(p))
        var dx = d.x
        var dy = -d.y
        if (hypot(dx, dy) < 1e-6) {
            // Exactly behind the camera: any way works, suggest turning up.
            dx = 0.0; dy = -1.0
        }
        return atan2(dy, dx)
    }

    /** Where the camera is pointing (the screen centre). */
    fun cameraDirection(): SkyPoint = worldToSky(deviceToWorld(Vec3(0.0, 0.0, -1.0)))

    companion object {
        /**
         * Compass heading for the sky map: the direction the phone is "facing", whether it is held
         * flat (top edge points forward) or upright (camera points forward). Uses the horizontal
         * part of the device vector (0, 1, -1), which points forward in both poses.
         */
        fun heading(rotation: FloatArray, northOffsetDeg: Double): Double {
            val r = rotation
            val ex = (r[1] - r[2]).toDouble()
            val ny = (r[4] - r[5]).toDouble()
            return SkyPath.normalize(Math.toDegrees(atan2(ex, ny)) + northOffsetDeg)
        }

        /** Altitude the camera points at: -90 straight down, +90 straight up. */
        fun cameraAltitude(rotation: FloatArray): Double =
            Math.toDegrees(asin((-rotation[8]).toDouble().coerceIn(-1.0, 1.0)))
    }
}
