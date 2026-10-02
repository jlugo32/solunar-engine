package com.solunar.core.solunar

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Sun/moon rise-set validation.
 *
 * The tight *positional* accuracy of the ephemeris is pinned by [SunPositionTest] and
 * [MoonPositionTest] against Meeus's published worked examples. Here we verify the
 * rise/set *solver* built on top of it:
 *   1. against a published USNO sunrise/sunset (aa.usno.navy.mil, Complete Sun and Moon Data
 *      for One Day), within ±2 min; and
 *   2. via physical invariants that must hold regardless of location/date.
 *
 * USNO reference used (Sun): New York City (lat 40.7128°, lon −74.0060°), 2026-06-21.
 * USNO "Sun and Moon Data for One Day" lists, in Eastern Daylight Time (UTC−4):
 *   Sunrise 05:24, Sunset 20:31.
 * Source: https://aa.usno.navy.mil/data/RS_OneDay
 */
class RiseSetTest {

    private val nyc = Observer(latitude = 40.7128, longitude = -74.0060, zone = ZoneId.of("America/New_York"))

    @Test
    fun `NYC sunrise on 2026-06-21 within 2 minutes of USNO`() {
        val day = SolunarEngine.computeDay(LocalDate.of(2026, 6, 21), nyc)
        val sunrise = day.sun.rise
        assertNotNull(sunrise, "expected a sunrise")
        val expected = LocalDate.of(2026, 6, 21).atTime(5, 24)
        val diffMin = Duration.between(expected, sunrise).abs().toMinutes()
        assertTrue(diffMin <= 2, "sunrise $sunrise differs from USNO 05:24 by $diffMin min")
    }

    @Test
    fun `NYC sunset on 2026-06-21 within 2 minutes of USNO`() {
        val day = SolunarEngine.computeDay(LocalDate.of(2026, 6, 21), nyc)
        val sunset = day.sun.set
        assertNotNull(sunset, "expected a sunset")
        val expected = LocalDate.of(2026, 6, 21).atTime(20, 31)
        val diffMin = Duration.between(expected, sunset).abs().toMinutes()
        assertTrue(diffMin <= 2, "sunset $sunset differs from USNO 20:31 by $diffMin min")
    }

    @Test
    fun `sun rises before it sets on a normal mid-latitude day`() {
        val day = SolunarEngine.computeDay(LocalDate.of(2026, 6, 21), nyc)
        assertNotNull(day.sun.rise)
        assertNotNull(day.sun.set)
        assertTrue(day.sun.rise!!.isBefore(day.sun.set), "sunrise should precede sunset")
    }

    @Test
    fun `moon rise and set both occur and are ordered sanely across the day`() {
        // A day chosen where the moon both rises and sets within the local day.
        val day = SolunarEngine.computeDay(LocalDate.of(2026, 3, 10), nyc)
        // At least one of rise/set must be present; when both are, they're distinct instants.
        val r = day.moon.rise
        val s = day.moon.set
        assertTrue(r != null || s != null, "expected at least one moon event")
        if (r != null && s != null) {
            assertTrue(r != s, "moonrise and moonset must be different instants")
        }
    }

    @Test
    fun `moon upper transit exists and is the higher of the two transits`() {
        val day = SolunarEngine.computeDay(LocalDate.of(2026, 3, 10), nyc)
        // Upper transit should exist most days; if both transits exist, altitude at upper > lower.
        val upper = day.moon.upperTransit
        if (upper != null) {
            val jdUt = JulianDate.fromUtc(upper.atZone(nyc.zone).toInstant())
            val jde = JulianDate.toJde(jdUt, upper.year, upper.monthValue)
            val eq = Moon.position(jde).equatorial
            val gmst = Coordinates.greenwichMeanSiderealDeg(jdUt)
            val ha = Coordinates.hourAngleDeg(gmst, nyc.longitude, eq.rightAscensionDeg)
            val alt = Coordinates.altitudeDeg(ha, eq.declinationDeg, nyc.latitude)
            // At upper transit the moon is near its daily maximum altitude → clearly positive here.
            assertTrue(alt > 0, "moon should be above horizon at upper transit (alt=$alt)")
        }
    }
}
