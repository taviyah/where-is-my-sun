package com.wheresmysun.astro

import java.time.ZonedDateTime
import kotlin.math.abs

/** A point on a drawn path. [time] is set for real samples and for the interpolated horizon crossings. */
data class PathPoint(val time: ZonedDateTime, val position: SkyPoint)

object SkyPath {

    /**
     * Splits a day of samples into the parts above the horizon. Each part starts and ends exactly
     * on the horizon (interpolated between the samples on either side), so drawn paths touch it
     * instead of stopping a few minutes short.
     */
    fun visibleSegments(samples: List<PathSample>, minAltitude: Double = 0.0): List<List<PathPoint>> {
        val segments = mutableListOf<List<PathPoint>>()
        var current: MutableList<PathPoint>? = null
        for (i in samples.indices) {
            val s = samples[i]
            val up = s.position.altitude >= minAltitude
            if (up) {
                if (current == null) {
                    current = mutableListOf()
                    if (i > 0) current += crossing(samples[i - 1], s, minAltitude)
                }
                current += PathPoint(s.time, s.position)
            } else if (current != null) {
                current += crossing(samples[i - 1], s, minAltitude)
                segments += current
                current = null
            }
        }
        if (current != null) segments += current
        return segments
    }

    /** Samples falling on a whole hour that are above the horizon, for the hour marks along the path. */
    fun hourMarks(samples: List<PathSample>, minAltitude: Double = 0.0): List<PathSample> =
        samples.filter { it.time.minute == 0 && it.position.altitude >= minAltitude }

    private fun crossing(a: PathSample, b: PathSample, level: Double): PathPoint {
        val da = a.position.altitude - level
        val db = b.position.altitude - level
        val f = if (da == db) 0.5 else (da / (da - db)).coerceIn(0.0, 1.0)
        val az = normalize(a.position.azimuth + f * signedDelta(a.position.azimuth, b.position.azimuth))
        val seconds = java.time.Duration.between(a.time, b.time).seconds
        return PathPoint(a.time.plusSeconds((seconds * f).toLong()), SkyPoint(az, level))
    }

    /** Shortest signed angle from [from] to [to], in -180..180. */
    fun signedDelta(from: Double, to: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    fun normalize(deg: Double): Double {
        val d = deg % 360.0
        return if (d < 0) d + 360.0 else d
    }

    fun angularDistance(a: Double, b: Double): Double = abs(signedDelta(a, b))
}

/** Compass direction name for an azimuth, e.g. 122° -> "SE". */
fun compassName(azimuth: Double): String {
    val names = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val i = ((SkyPath.normalize(azimuth) + 11.25) / 22.5).toInt() % 16
    return names[i]
}
