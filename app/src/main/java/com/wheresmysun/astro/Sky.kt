package com.wheresmysun.astro

import java.time.Duration
import java.time.ZonedDateTime

/** A direction in the sky. Azimuth is true (geographic) north = 0, clockwise, in degrees.
 *  Altitude is degrees above the horizon, negative when below it. */
data class SkyPoint(val azimuth: Double, val altitude: Double)

data class GeoPoint(val latitude: Double, val longitude: Double, val elevation: Double = 0.0)

data class PathSample(val time: ZonedDateTime, val position: SkyPoint)

/** A rise or set event: when it happens and which direction to look. */
data class HorizonEvent(val time: ZonedDateTime, val azimuth: Double)

enum class Body { SUN, MOON }

/** Everything about one body over one calendar day. */
data class BodyDay(
    val body: Body,
    /** Positions sampled through the whole day, including while the body is below the horizon. */
    val samples: List<PathSample>,
    /** Null when the body doesn't rise (or set) during this calendar day. */
    val rise: HorizonEvent?,
    val set: HorizonEvent?,
    val alwaysUp: Boolean,
    val alwaysDown: Boolean,
    /**
     * The altitude (as [SkyCalculator] reports it) of the body's centre at the moment it rises or
     * sets. Rise/set are defined by the upper edge touching the horizon, so this is a little below
     * 0°. Paths are clipped at this level so they end exactly at the rise and set points.
     */
    val horizonAltitude: Double,
) {
    /** The highest sample of the day. Useful as "where to look" when the body is below the horizon. */
    val highest: PathSample = samples.maxBy { it.position.altitude }

    val isVisibleSometime: Boolean get() = highest.position.altitude > horizonAltitude

    fun visibleSegments(): List<List<PathPoint>> = SkyPath.visibleSegments(samples, horizonAltitude)

    fun hourMarks(): List<PathSample> = SkyPath.hourMarks(samples, horizonAltitude)

    fun isUp(position: SkyPoint): Boolean = position.altitude >= horizonAltitude
}

enum class MoonPhaseName { NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }

data class MoonPhaseInfo(
    /** 0.0 (new) to 1.0 (full). */
    val illumination: Double,
    val name: MoonPhaseName,
    val waxing: Boolean,
)

data class DaySky(
    val location: GeoPoint,
    val start: ZonedDateTime,
    val sun: BodyDay,
    val moon: BodyDay,
    val solarNoon: ZonedDateTime?,
    /** Morning golden hour runs from sunrise until this time (the sun reaches +6°). */
    val morningGoldenHourEnd: ZonedDateTime?,
    /** Evening golden hour runs from this time (the sun drops to +6°) until sunset. */
    val eveningGoldenHourStart: ZonedDateTime?,
    /** Moon phase at noon of this day. */
    val moonPhase: MoonPhaseInfo,
) {
    val dayLength: Duration?
        get() = when {
            sun.alwaysUp -> Duration.ofHours(24)
            sun.alwaysDown -> Duration.ZERO
            sun.rise != null && sun.set != null && sun.set.time.isAfter(sun.rise.time) ->
                Duration.between(sun.rise.time, sun.set.time)
            else -> null
        }
}
