package com.wheresmysun.astro

import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPhase
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.MoonTimes
import org.shredzone.commons.suncalc.SunPosition
import org.shredzone.commons.suncalc.SunTimes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.tan

/** All astronomy in the app goes through here. Everything is computed offline from location and time. */
object SkyCalculator {

    const val DEFAULT_STEP_MINUTES = 5L

    fun sunPosition(at: GeoPoint, time: Instant): SkyPoint {
        val p = SunPosition.compute().at(at.latitude, at.longitude).elevation(at.elevation).on(time).execute()
        return SkyPoint(p.azimuth, p.altitude)
    }

    /**
     * The moon as seen from where you stand. suncalc's MoonPosition is geocentric (as seen from the
     * Earth's centre), which puts the moon up to ~1° too high: the moon is close enough that
     * standing on the surface, 6,378 km off-centre, shifts it visibly. This applies that parallax
     * and then atmospheric refraction.
     */
    fun moonPosition(at: GeoPoint, time: Instant): SkyPoint {
        val p = MoonPosition.compute().at(at.latitude, at.longitude).elevation(at.elevation).on(time).execute()
        return SkyPoint(p.azimuth, topocentricAltitude(p.trueAltitude, p.distance))
    }

    /** Geocentric true altitude -> apparent altitude for an observer on the surface. */
    internal fun topocentricAltitude(geocentricTrueAltitude: Double, distanceKm: Double): Double {
        val h = Math.toRadians(geocentricTrueAltitude)
        val parallax = Math.toDegrees(asin((EARTH_RADIUS_KM / distanceKm) * cos(h)))
        val trueTopo = geocentricTrueAltitude - parallax
        return trueTopo + refraction(trueTopo)
    }

    /** Sæmundsson's refraction formula, true -> apparent altitude, in degrees. Undefined well below
     *  the horizon, where it doesn't matter, so it is tapered to zero there. */
    private fun refraction(trueAltitude: Double): Double {
        if (trueAltitude < -2.0) return 0.0
        val h = trueAltitude.coerceAtLeast(-1.0)
        val arcMinutes = 1.02 / tan(Math.toRadians(h + 10.3 / (h + 5.11)))
        return arcMinutes / 60.0
    }

    private const val EARTH_RADIUS_KM = 6378.14

    fun position(body: Body, at: GeoPoint, time: Instant): SkyPoint = when (body) {
        Body.SUN -> sunPosition(at, time)
        Body.MOON -> moonPosition(at, time)
    }

    fun moonPhase(at: GeoPoint, time: Instant): MoonPhaseInfo {
        val ill = MoonIllumination.compute().at(at.latitude, at.longitude).on(time).execute()
        val name = when (ill.closestPhase) {
            MoonPhase.Phase.NEW_MOON -> MoonPhaseName.NEW_MOON
            MoonPhase.Phase.WAXING_CRESCENT -> MoonPhaseName.WAXING_CRESCENT
            MoonPhase.Phase.FIRST_QUARTER -> MoonPhaseName.FIRST_QUARTER
            MoonPhase.Phase.WAXING_GIBBOUS -> MoonPhaseName.WAXING_GIBBOUS
            MoonPhase.Phase.FULL_MOON -> MoonPhaseName.FULL_MOON
            MoonPhase.Phase.WANING_GIBBOUS -> MoonPhaseName.WANING_GIBBOUS
            MoonPhase.Phase.LAST_QUARTER -> MoonPhaseName.LAST_QUARTER
            MoonPhase.Phase.WANING_CRESCENT -> MoonPhaseName.WANING_CRESCENT
            null -> MoonPhaseName.NEW_MOON
        }
        // suncalc's phase runs from -180 (new, waxing) through 0 (full) to +180 (new, waning).
        return MoonPhaseInfo(ill.fraction, name, waxing = ill.phase < 0.0)
    }

    /** Computes the sun and moon over the calendar day [date] in [zone]. */
    fun computeDay(
        at: GeoPoint,
        date: LocalDate,
        zone: ZoneId,
        stepMinutes: Long = DEFAULT_STEP_MINUTES,
    ): DaySky {
        val start = date.atStartOfDay(zone)
        val end = date.plusDays(1).atStartOfDay(zone)

        val sunTimes = SunTimes.compute().timezone(zone).on(start)
            .at(at.latitude, at.longitude).elevation(at.elevation).oneDay().execute()
        val golden = SunTimes.compute().timezone(zone).on(start)
            .at(at.latitude, at.longitude).elevation(at.elevation)
            .twilight(SunTimes.Twilight.GOLDEN_HOUR).oneDay().execute()
        val moonTimes = MoonTimes.compute().timezone(zone).on(start)
            .at(at.latitude, at.longitude).elevation(at.elevation).oneDay().execute()

        val sun = bodyDay(
            Body.SUN, at, start, end, stepMinutes,
            rise = sunTimes.rise, set = sunTimes.set,
            alwaysUp = sunTimes.isAlwaysUp, alwaysDown = sunTimes.isAlwaysDown,
        )
        val moon = bodyDay(
            Body.MOON, at, start, end, stepMinutes,
            rise = moonTimes.rise, set = moonTimes.set,
            alwaysUp = moonTimes.isAlwaysUp, alwaysDown = moonTimes.isAlwaysDown,
        )

        // The "golden" twilight is the sun crossing +6°: its rise ends the morning golden hour,
        // its set starts the evening one. Only meaningful on days with a real sunrise/sunset.
        val goldenEnd = golden.rise.withinDay(start, end).takeIf { sun.rise != null }
        val goldenStart = golden.set.withinDay(start, end).takeIf { sun.set != null }

        return DaySky(
            location = at,
            start = start,
            sun = sun,
            moon = moon,
            solarNoon = sunTimes.noon.withinDay(start, end),
            morningGoldenHourEnd = goldenEnd,
            eveningGoldenHourStart = goldenStart,
            moonPhase = moonPhase(at, start.plusHours(12).toInstant()),
        )
    }

    private fun bodyDay(
        body: Body,
        at: GeoPoint,
        start: ZonedDateTime,
        end: ZonedDateTime,
        stepMinutes: Long,
        rise: ZonedDateTime?,
        set: ZonedDateTime?,
        alwaysUp: Boolean,
        alwaysDown: Boolean,
    ): BodyDay {
        val risePos = rise.withinDay(start, end)?.let { it to position(body, at, it.toInstant()) }
        val setPos = set.withinDay(start, end)?.let { it to position(body, at, it.toInstant()) }
        // Prefer the level measured at today's own events; on days without any, use the level at
        // the nearest events outside the day, falling back to the textbook value.
        val level = risePos?.second?.altitude
            ?: setPos?.second?.altitude
            ?: rise?.let { position(body, at, it.toInstant()).altitude }
            ?: set?.let { position(body, at, it.toInstant()).altitude }
            ?: DEFAULT_HORIZON_ALTITUDE
        return BodyDay(
            body = body,
            samples = sample(body, at, start, end, stepMinutes),
            rise = risePos?.let { HorizonEvent(it.first, it.second.azimuth) },
            set = setPos?.let { HorizonEvent(it.first, it.second.azimuth) },
            alwaysUp = alwaysUp,
            alwaysDown = alwaysDown,
            horizonAltitude = level.coerceIn(-2.0, 1.0),
        )
    }

    /** Refraction (34') plus the sun's radius (16'). */
    private const val DEFAULT_HORIZON_ALTITUDE = -0.833

    private fun sample(body: Body, at: GeoPoint, start: ZonedDateTime, end: ZonedDateTime, stepMinutes: Long): List<PathSample> {
        val out = ArrayList<PathSample>(300)
        var t = start
        while (!t.isAfter(end)) {
            out += PathSample(t, position(body, at, t.toInstant()))
            t = t.plusMinutes(stepMinutes)
        }
        return out
    }

    private fun ZonedDateTime?.withinDay(start: ZonedDateTime, end: ZonedDateTime): ZonedDateTime? =
        this?.takeIf { !it.isBefore(start) && it.isBefore(end) }
}
