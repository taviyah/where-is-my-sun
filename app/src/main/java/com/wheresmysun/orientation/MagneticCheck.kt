package com.wheresmysun.orientation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** The Earth's field as it should be here, from the World Magnetic Model (GeomagneticField). */
data class ExpectedField(val strengthUt: Double, val inclinationDeg: Double)

/** What the magnetometer measures right now. */
data class MeasuredField(val strengthUt: Double, val inclinationDeg: Double)

/**
 * Tells whether the compass can be trusted, independently of Android's own accuracy flag.
 *
 * Away from interference the magnetometer should read the Earth's field: a known strength (25-65 µT
 * depending on where you are) dipping into the ground at a known angle. A car, a steel railing,
 * rebar or a laptop adds its own field, which changes the strength, the dip or both, and bends
 * the compass heading with them. Android's accuracy flag only reflects the sensor's
 * self-calibration and often stays "high" right next to a car.
 */
object MagneticCheck {

    /** Dip angle of a field given in world coordinates (x East, y North, z Up). Positive = into the ground. */
    fun inclination(world: Vec3): Double = Math.toDegrees(atan2(-world.z, hypot(world.x, world.y)))

    fun strength(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    /**
     * True when the measurement is too far from the Earth's field to trust. [wasDisturbed] adds
     * hysteresis: once flagged, the reading must come well back inside the limits before the
     * warning clears, so it doesn't flicker on the boundary.
     */
    fun isDisturbed(measured: MeasuredField, expected: ExpectedField, wasDisturbed: Boolean): Boolean {
        val strengthError = abs(measured.strengthUt / expected.strengthUt - 1.0)
        val dipError = abs(measured.inclinationDeg - expected.inclinationDeg)
        val (maxStrength, maxDip) = if (wasDisturbed) 0.12 to 6.0 else 0.20 to 10.0
        return strengthError > maxStrength || dipError > maxDip
    }
}
