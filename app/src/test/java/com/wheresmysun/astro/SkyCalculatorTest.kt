package com.wheresmysun.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Reference rise/set times are the published almanac values (timeanddate.com / NOAA), rounded to the
 * minute. A three-minute tolerance absorbs rounding and year-to-year drift without hiding real bugs
 * like a wrong time zone (hours) or a swapped longitude sign (minutes to hours).
 */
class SkyCalculatorTest {

    private val london = GeoPoint(51.5074, -0.1278)
    private val londonZone = ZoneId.of("Europe/London")
    private val newYork = GeoPoint(40.7128, -74.0060)
    private val sydney = GeoPoint(-33.8688, 151.2093)
    private val tromso = GeoPoint(69.6492, 18.9553)

    private fun assertTimeNear(expected: String, actual: ZonedDateTime?, toleranceMinutes: Long = 3) {
        assertNotNull("expected an event near $expected", actual)
        val diff = Duration.between(LocalTime.parse(expected), actual!!.toLocalTime()).abs()
        assertTrue("expected $expected, got ${actual.toLocalTime()}", diff.toMinutes() <= toleranceMinutes)
    }

    @Test fun londonSummerSolstice() {
        val day = SkyCalculator.computeDay(london, LocalDate.of(2026, 6, 21), londonZone)
        assertTimeNear("04:43", day.sun.rise?.time)
        assertTimeNear("21:21", day.sun.set?.time)
        // Midsummer sunrise is in the north-east, sunset in the north-west.
        assertEquals(49.0, day.sun.rise!!.azimuth, 2.0)
        assertEquals(311.0, day.sun.set!!.azimuth, 2.0)
        assertFalse(day.sun.alwaysUp)
    }

    @Test fun newYorkWinterSolstice() {
        val day = SkyCalculator.computeDay(newYork, LocalDate.of(2026, 12, 21), ZoneId.of("America/New_York"))
        assertTimeNear("07:17", day.sun.rise?.time)
        assertTimeNear("16:32", day.sun.set?.time)
    }

    @Test fun sydneySummerSolstice() {
        val day = SkyCalculator.computeDay(sydney, LocalDate.of(2026, 12, 21), ZoneId.of("Australia/Sydney"))
        assertTimeNear("05:41", day.sun.rise?.time)
        assertTimeNear("20:05", day.sun.set?.time)
    }

    @Test fun noonSunIsDueSouthInTheNorth() {
        val day = SkyCalculator.computeDay(london, LocalDate.of(2026, 6, 21), londonZone)
        val noon = SkyCalculator.sunPosition(london, day.solarNoon!!.toInstant())
        assertEquals(180.0, noon.azimuth, 0.5)
        // 90 - latitude + 23.44 (solstice declination)
        assertEquals(61.9, noon.altitude, 0.5)
    }

    @Test fun noonSunIsDueNorthInTheSouth() {
        val day = SkyCalculator.computeDay(sydney, LocalDate.of(2026, 12, 21), ZoneId.of("Australia/Sydney"))
        val noon = SkyCalculator.sunPosition(sydney, day.solarNoon!!.toInstant())
        assertEquals(0.0, SkyPath.signedDelta(0.0, noon.azimuth), 1.0)
        assertEquals(79.6, noon.altitude, 0.5)
    }

    @Test fun equinoxNoonAltitudeIsColatitude() {
        val day = SkyCalculator.computeDay(london, LocalDate.of(2026, 3, 20), londonZone)
        val noon = SkyCalculator.sunPosition(london, day.solarNoon!!.toInstant())
        assertEquals(90.0 - london.latitude, noon.altitude, 0.5)
    }

    @Test fun midnightSunAndPolarNight() {
        val zone = ZoneId.of("Europe/Oslo")
        val summer = SkyCalculator.computeDay(tromso, LocalDate.of(2026, 6, 21), zone)
        assertTrue(summer.sun.alwaysUp)
        assertNull(summer.sun.rise)
        assertNull(summer.sun.set)
        assertEquals(Duration.ofHours(24), summer.dayLength)
        assertEquals(1, summer.sun.visibleSegments().size)

        val winter = SkyCalculator.computeDay(tromso, LocalDate.of(2026, 12, 21), zone)
        assertTrue(winter.sun.alwaysDown)
        assertTrue(winter.sun.visibleSegments().isEmpty())
        assertFalse(winter.sun.isVisibleSometime)
    }

    @Test fun goldenHourSitsInsideDaylight() {
        val day = SkyCalculator.computeDay(london, LocalDate.of(2026, 10, 20), londonZone)
        val rise = day.sun.rise!!.time
        val set = day.sun.set!!.time
        val goldenEnd = day.morningGoldenHourEnd!!
        val goldenStart = day.eveningGoldenHourStart!!
        assertTrue(goldenEnd.isAfter(rise) && goldenEnd.isBefore(day.solarNoon))
        assertTrue(goldenStart.isAfter(day.solarNoon) && goldenStart.isBefore(set))
        // In mid-October at London's latitude the sun takes roughly an hour to climb 6°.
        val morning = Duration.between(rise, goldenEnd).toMinutes()
        assertTrue("golden hour was $morning min", morning in 40..90)
    }

    @Test fun samplesCoverTheWholeDayAcrossDstChange() {
        // Clocks go forward on 2026-03-29 in the UK, so the day has 23 hours.
        val day = SkyCalculator.computeDay(london, LocalDate.of(2026, 3, 29), londonZone)
        assertEquals(23 * 12 + 1, day.sun.samples.size)
        assertEquals(LocalTime.MIDNIGHT, day.sun.samples.first().time.toLocalTime())
        assertEquals(LocalDate.of(2026, 3, 30), day.sun.samples.last().time.toLocalDate())
    }

    @Test fun pathStartsAndEndsOnTheHorizonAtRiseAndSet() {
        val day = SkyCalculator.computeDay(london, LocalDate.of(2026, 6, 21), londonZone)
        val segments = day.sun.visibleSegments()
        assertEquals(1, segments.size)
        val path = segments.single()
        assertEquals(day.sun.horizonAltitude, path.first().position.altitude, 1e-9)
        assertEquals(day.sun.horizonAltitude, path.last().position.altitude, 1e-9)
        assertTrue(day.sun.horizonAltitude < 0.0)
        val riseGap = Duration.between(day.sun.rise!!.time, path.first().time).abs().toMinutes()
        val setGap = Duration.between(day.sun.set!!.time, path.last().time).abs().toMinutes()
        assertTrue("rise ${day.sun.rise!!.time} vs path ${path.first().time}", riseGap <= 1)
        assertTrue("set ${day.sun.set!!.time} vs path ${path.last().time}", setGap <= 1)
        assertEquals(day.sun.rise!!.azimuth, path.first().position.azimuth, 1.0)
    }

    @Test fun hourMarksAreOnWholeHoursAboveTheHorizon() {
        val day = SkyCalculator.computeDay(london, LocalDate.of(2026, 6, 21), londonZone)
        val marks = day.sun.hourMarks()
        // Sunrise 04:43 to sunset 21:21 -> 05:00 through 21:00.
        assertEquals((5..21).toList(), marks.map { it.time.hour })
    }

    @Test fun moonRiseAndSetAreOnTheHorizon() {
        // Check a whole month so both "rises but doesn't set" and "sets but doesn't rise" days occur.
        var date = LocalDate.of(2026, 10, 1)
        var daysMissingAnEvent = 0
        repeat(30) {
            val day = SkyCalculator.computeDay(london, date, londonZone)
            for (event in listOfNotNull(day.moon.rise, day.moon.set)) {
                val alt = SkyCalculator.moonPosition(london, event.time.toInstant()).altitude
                assertTrue("moon altitude at rise/set was $alt on $date", abs(alt) < 1.5)
            }
            if (day.moon.rise == null || day.moon.set == null) daysMissingAnEvent++
            date = date.plusDays(1)
        }
        // Moonrise slips ~50 minutes a day, so about once in each half-month one event falls outside the day.
        assertTrue(daysMissingAnEvent in 1..4)
    }

    @Test fun moonPhaseMatchesKnownEclipses() {
        // Total lunar eclipse 2026-03-03 (full moon); annular solar eclipse 2026-02-17 (new moon).
        val utc = ZoneId.of("UTC")
        val full = SkyCalculator.moonPhase(london, ZonedDateTime.of(2026, 3, 3, 11, 0, 0, 0, utc).toInstant())
        assertTrue(full.illumination > 0.97)
        assertEquals(MoonPhaseName.FULL_MOON, full.name)

        val new = SkyCalculator.moonPhase(london, ZonedDateTime.of(2026, 2, 17, 12, 0, 0, 0, utc).toInstant())
        assertTrue(new.illumination < 0.03)
        assertEquals(MoonPhaseName.NEW_MOON, new.name)

        val waxing = SkyCalculator.moonPhase(london, ZonedDateTime.of(2026, 2, 24, 12, 0, 0, 0, utc).toInstant())
        assertTrue(waxing.waxing)
        val waning = SkyCalculator.moonPhase(london, ZonedDateTime.of(2026, 3, 10, 12, 0, 0, 0, utc).toInstant())
        assertFalse(waning.waxing)
    }

    @Test fun moonIsCorrectedForParallax() {
        // Seen from the surface the moon sits lower than from the Earth's centre, by about
        // 0.95° x cos(altitude). Check over a night when the moon is well up.
        val start = ZonedDateTime.of(2026, 10, 20, 18, 0, 0, 0, londonZone)
        var checked = 0
        for (h in 0 until 24) {
            val t = start.plusMinutes(h * 30L).toInstant()
            val geo = org.shredzone.commons.suncalc.MoonPosition.compute().at(london.latitude, london.longitude).on(t).execute()
            if (geo.altitude < 10.0) continue
            val ours = SkyCalculator.moonPosition(london, t)
            val shift = geo.altitude - ours.altitude
            val expected = Math.toDegrees(Math.asin(6378.14 / geo.distance * Math.cos(Math.toRadians(geo.trueAltitude))))
            assertEquals(expected, shift, 0.05)
            assertTrue("parallax was $shift", shift in 0.4..1.05)
            assertEquals(geo.azimuth, ours.azimuth, 1e-9)
            checked++
        }
        assertTrue(checked > 0)
    }

    @Test fun compassNames() {
        assertEquals("N", compassName(0.0))
        assertEquals("N", compassName(359.0))
        assertEquals("NE", compassName(45.0))
        assertEquals("SE", compassName(130.0))
        assertEquals("W", compassName(-90.0))
    }

    @Test fun signedDeltaWrapsAround() {
        assertEquals(20.0, SkyPath.signedDelta(350.0, 10.0), 1e-9)
        assertEquals(-20.0, SkyPath.signedDelta(10.0, 350.0), 1e-9)
    }
}
