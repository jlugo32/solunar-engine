package com.solunar.core.solunar

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.floor
import kotlin.math.pow

/**
 * Julian Day / Julian Ephemeris Day conversions (Meeus ch.7) and the ΔT correction
 * (Meeus ch.10 + Espenak & Meeus polynomial expressions).
 *
 * JD is the continuous count of days since −4712 Jan 1, 12h UT. JDE is the same count on
 * the uniform Dynamical Time scale (JDE = JD + ΔT/86400). Sun/Moon positions are strictly
 * functions of JDE; sidereal time and civil clock times use JD (UT). Getting this split
 * right is what keeps rise/set within the ±2 min tolerance the tests demand.
 */
internal object JulianDate {

    /** Julian Day from a UTC instant (Meeus eq. 7.1). Valid for the full civil range we use. */
    fun fromUtc(instant: Instant): Double {
        // Decompose into calendar Y/M/D + fractional day, all in UTC.
        val ldt = instant.atOffset(ZoneOffset.UTC)
        val dayFraction = (ldt.hour + (ldt.minute + (ldt.second + ldt.nano / 1e9) / 60.0) / 60.0) / 24.0
        return fromCalendar(ldt.year, ldt.monthValue, ldt.dayOfMonth.toDouble() + dayFraction)
    }

    /** Julian Day from a (possibly fractional) Gregorian calendar date (Meeus eq. 7.1). */
    fun fromCalendar(year: Int, month: Int, day: Double): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        // Gregorian calendar term B (all dates we handle are post-1582, i.e. Gregorian).
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Julian centuries of 36525 days from epoch J2000.0 (JD 2451545.0) — Meeus eq. 22.1. */
    fun centuriesSinceJ2000(jd: Double): Double = (jd - 2451545.0) / 36525.0

    /**
     * ΔT = TD − UT, in seconds. Espenak–Meeus polynomial expressions
     * (https://eclipse.gsfc.nasa.gov/SEhelp/deltatpoly2004.html), which Meeus ch.10 also
     * tabulates. Covers all years the app can realistically be used in; for the 2005–2050
     * window (which includes every test date) it uses the dedicated fit.
     */
    fun deltaTSeconds(year: Int, month: Int): Double {
        val y = year + (month - 0.5) / 12.0
        return when {
            year in 2005..2050 -> {
                val t = y - 2000.0
                62.92 + 0.32217 * t + 0.005589 * t * t
            }
            year in 1986..2004 -> {
                val t = y - 2000.0
                63.86 + 0.3345 * t - 0.060374 * t * t +
                    0.0017275 * t.pow(3) + 0.000651814 * t.pow(4) + 0.00002373599 * t.pow(5)
            }
            year in 2050..2150 -> {
                val u = (y - 1820.0) / 100.0
                -20.0 + 32.0 * u * u - 0.5628 * (2150.0 - y)
            }
            else -> {
                // Long-term parabola (Meeus eq. 10.2) as a safe fallback outside fitted ranges.
                val u = (y - 1820.0) / 100.0
                -20.0 + 32.0 * u * u
            }
        }
    }

    /** JDE (Dynamical Time) from JD (UT), given the calendar year/month for ΔT lookup. */
    fun toJde(jd: Double, year: Int, month: Int): Double =
        jd + deltaTSeconds(year, month) / 86400.0
}
