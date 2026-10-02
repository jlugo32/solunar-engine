package com.solunar.core.solunar

import com.solunar.core.solunar.Angles.DEG2RAD
import com.solunar.core.solunar.Angles.RAD2DEG
import com.solunar.core.solunar.Angles.norm360
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sidereal time, obliquity of the ecliptic, and coordinate transforms (Meeus ch.11–13, 22).
 *
 * These are shared by both Sun and Moon: once each body's apparent right ascension and
 * declination are known, sidereal time + observer longitude give the hour angle, and hour
 * angle + declination + latitude give the horizontal altitude/azimuth used for rise/set.
 */
internal object Coordinates {

    /** An equatorial position: right ascension and declination, both in degrees. */
    data class Equatorial(val rightAscensionDeg: Double, val declinationDeg: Double)

    /**
     * Mean obliquity of the ecliptic ε0 in degrees (Meeus eq. 22.2). We use the mean value;
     * for rise/set at the ±2 min level the nutation-in-obliquity term (< 0.01°) is negligible.
     */
    fun meanObliquityDeg(jde: Double): Double {
        val t = JulianDate.centuriesSinceJ2000(jde)
        val u = t / 100.0
        return 23.0 + 26.0 / 60.0 + 21.448 / 3600.0 +
            (-4680.93 * u
                - 1.55 * u * u
                + 1999.25 * u * u * u
                - 51.38 * u * u * u * u
                - 249.67 * u * u * u * u * u
                - 39.05 * u * u * u * u * u * u
                + 7.12 * u * u * u * u * u * u * u
                + 27.87 * u * u * u * u * u * u * u * u
                + 5.79 * u * u * u * u * u * u * u * u * u
                + 2.45 * u * u * u * u * u * u * u * u * u * u) / 3600.0
    }

    /**
     * Convert an apparent ecliptic position (longitude λ, latitude β in degrees) to equatorial
     * coordinates using obliquity ε (Meeus eq. 13.3, 13.4).
     */
    fun eclipticToEquatorial(lambdaDeg: Double, betaDeg: Double, obliquityDeg: Double): Equatorial {
        val lambda = lambdaDeg * DEG2RAD
        val beta = betaDeg * DEG2RAD
        val eps = obliquityDeg * DEG2RAD
        val ra = atan2(
            sin(lambda) * cos(eps) - kotlin.math.tan(beta) * sin(eps),
            cos(lambda),
        )
        val dec = asin(sin(beta) * cos(eps) + cos(beta) * sin(eps) * sin(lambda))
        return Equatorial(norm360(ra * RAD2DEG), dec * RAD2DEG)
    }

    /**
     * Apparent (mean) sidereal time at Greenwich, in degrees, for a given JD (UT) — Meeus
     * eq. 12.4. We use mean sidereal time; the equation-of-the-equinoxes correction is far
     * below our tolerance.
     */
    fun greenwichMeanSiderealDeg(jdUt: Double): Double {
        val t = JulianDate.centuriesSinceJ2000(jdUt)
        val theta = 280.46061837 +
            360.98564736629 * (jdUt - 2451545.0) +
            0.000387933 * t * t -
            t * t * t / 38710000.0
        return norm360(theta)
    }

    /**
     * Local hour angle in degrees for a body of right ascension [raDeg], observed at
     * [gmstDeg] (Greenwich mean sidereal, degrees) and observer [longitudeEastDeg]
     * (east-positive). H = GMST + longitude − RA. Result normalised to [0, 360).
     */
    fun hourAngleDeg(gmstDeg: Double, longitudeEastDeg: Double, raDeg: Double): Double =
        norm360(gmstDeg + longitudeEastDeg - raDeg)

    /**
     * Altitude of a body above the horizon, in degrees (Meeus eq. 13.6), given local hour
     * angle H, declination δ and observer latitude φ (all degrees).
     */
    fun altitudeDeg(hourAngleDeg: Double, declinationDeg: Double, latitudeDeg: Double): Double {
        val h = hourAngleDeg * DEG2RAD
        val dec = declinationDeg * DEG2RAD
        val lat = latitudeDeg * DEG2RAD
        val sinAlt = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(h)
        return asin(sinAlt.coerceIn(-1.0, 1.0)) * RAD2DEG
    }
}
