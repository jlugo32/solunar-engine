package com.solunar.core.solunar

import com.solunar.core.solunar.Angles.DEG2RAD
import com.solunar.core.solunar.Angles.norm360
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apparent lunar position via the abbreviated ELP-2000/82 series of Meeus ch.47
 * (Tables 47.A and 47.B). This truncated set reproduces the moon's geocentric ecliptic
 * longitude to ~10″ and latitude to ~4″ near J2000 — an order of magnitude tighter than
 * the ±2 min rise/set tolerance the engine is validated against.
 *
 * Returns geocentric ecliptic longitude/latitude (degrees), distance (km), the derived
 * apparent equatorial position, and the equatorial horizontal parallax π used to place the
 * moon's rise/set standard altitude (spec §4.F).
 */
internal object Moon {

    data class Position(
        val eclipticLongitudeDeg: Double,
        val eclipticLatitudeDeg: Double,
        val distanceKm: Double,
        val equatorial: Coordinates.Equatorial,
        val parallaxDeg: Double,
    )

    // --- Periodic-term tables (Meeus Table 47.A for lon & dist, 47.B for lat) ---
    // Each row: D, M, M', F multipliers, then the coefficient.
    // Longitude coefficient in 1e-6 degrees (Σl); distance in 1e-3 km (Σr).
    private data class TermLR(val d: Int, val m: Int, val mp: Int, val f: Int, val sl: Double, val sr: Double)
    private data class TermB(val d: Int, val m: Int, val mp: Int, val f: Int, val sb: Double)

    // Table 47.A — the 60 terms for longitude (Σl) and distance (Σr).
    private val TABLE_LR = arrayOf(
        TermLR(0, 0, 1, 0, 6288774.0, -20905355.0),
        TermLR(2, 0, -1, 0, 1274027.0, -3699111.0),
        TermLR(2, 0, 0, 0, 658314.0, -2955968.0),
        TermLR(0, 0, 2, 0, 213618.0, -569925.0),
        TermLR(0, 1, 0, 0, -185116.0, 48888.0),
        TermLR(0, 0, 0, 2, -114332.0, -3149.0),
        TermLR(2, 0, -2, 0, 58793.0, 246158.0),
        TermLR(2, -1, -1, 0, 57066.0, -152138.0),
        TermLR(2, 0, 1, 0, 53322.0, -170733.0),
        TermLR(2, -1, 0, 0, 45758.0, -204586.0),
        TermLR(0, 1, -1, 0, -40923.0, -129620.0),
        TermLR(1, 0, 0, 0, -34720.0, 108743.0),
        TermLR(0, 1, 1, 0, -30383.0, 104755.0),
        TermLR(2, 0, 0, -2, 15327.0, 10321.0),
        TermLR(0, 0, 1, 2, -12528.0, 0.0),
        TermLR(0, 0, 1, -2, 10980.0, 79661.0),
        TermLR(4, 0, -1, 0, 10675.0, -34782.0),
        TermLR(0, 0, 3, 0, 10034.0, -23210.0),
        TermLR(4, 0, -2, 0, 8548.0, -21636.0),
        TermLR(2, 1, -1, 0, -7888.0, 24208.0),
        TermLR(2, 1, 0, 0, -6766.0, 30824.0),
        TermLR(1, 0, -1, 0, -5163.0, -8379.0),
        TermLR(1, 1, 0, 0, 4987.0, -16675.0),
        TermLR(2, -1, 1, 0, 4036.0, -12831.0),
        TermLR(2, 0, 2, 0, 3994.0, -10445.0),
        TermLR(4, 0, 0, 0, 3861.0, -11650.0),
        TermLR(2, 0, -3, 0, 3665.0, 14403.0),
        TermLR(0, 1, -2, 0, -2689.0, -7003.0),
        TermLR(2, 0, -1, 2, -2602.0, 0.0),
        TermLR(2, -1, -2, 0, 2390.0, 10056.0),
        TermLR(1, 0, 1, 0, -2348.0, 6322.0),
        TermLR(2, -2, 0, 0, 2236.0, -9884.0),
        TermLR(0, 1, 2, 0, -2120.0, 5751.0),
        TermLR(0, 2, 0, 0, -2069.0, 0.0),
        TermLR(2, -2, -1, 0, 2048.0, -4950.0),
        TermLR(2, 0, 1, -2, -1773.0, 4130.0),
        TermLR(2, 0, 0, 2, -1595.0, 0.0),
        TermLR(4, -1, -1, 0, 1215.0, -3958.0),
        TermLR(0, 0, 2, 2, -1110.0, 0.0),
        TermLR(3, 0, -1, 0, -892.0, 3258.0),
        TermLR(2, 1, 1, 0, -810.0, 2616.0),
        TermLR(4, -1, -2, 0, 759.0, -1897.0),
        TermLR(0, 2, -1, 0, -713.0, -2117.0),
        TermLR(2, 2, -1, 0, -700.0, 2354.0),
        TermLR(2, 1, -2, 0, 691.0, 0.0),
        TermLR(2, -1, 0, -2, 596.0, 0.0),
        TermLR(4, 0, 1, 0, 549.0, -1423.0),
        TermLR(0, 0, 4, 0, 537.0, -1117.0),
        TermLR(4, -1, 0, 0, 520.0, -1571.0),
        TermLR(1, 0, -2, 0, -487.0, -1739.0),
        TermLR(2, 1, 0, -2, -399.0, 0.0),
        TermLR(0, 0, 2, -2, -381.0, -4421.0),
        TermLR(1, 1, 1, 0, 351.0, 0.0),
        TermLR(3, 0, -2, 0, -340.0, 0.0),
        TermLR(4, 0, -3, 0, 330.0, 0.0),
        TermLR(2, -1, 2, 0, 327.0, 0.0),
        TermLR(0, 2, 1, 0, -323.0, 1165.0),
        TermLR(1, 1, -1, 0, 299.0, 0.0),
        TermLR(2, 0, 3, 0, 294.0, 0.0),
        TermLR(2, 0, -1, -2, 0.0, 8752.0),
    )

    // Table 47.B — the 60 terms for latitude (Σb), coefficient in 1e-6 degrees.
    private val TABLE_B = arrayOf(
        TermB(0, 0, 0, 1, 5128122.0),
        TermB(0, 0, 1, 1, 280602.0),
        TermB(0, 0, 1, -1, 277693.0),
        TermB(2, 0, 0, -1, 173237.0),
        TermB(2, 0, -1, 1, 55413.0),
        TermB(2, 0, -1, -1, 46271.0),
        TermB(2, 0, 0, 1, 32573.0),
        TermB(0, 0, 2, 1, 17198.0),
        TermB(2, 0, 1, -1, 9266.0),
        TermB(0, 0, 2, -1, 8822.0),
        TermB(2, -1, 0, -1, 8216.0),
        TermB(2, 0, -2, -1, 4324.0),
        TermB(2, 0, 1, 1, 4200.0),
        TermB(2, 1, 0, -1, -3359.0),
        TermB(2, -1, -1, 1, 2463.0),
        TermB(2, -1, 0, 1, 2211.0),
        TermB(2, -1, -1, -1, 2065.0),
        TermB(0, 1, -1, -1, -1870.0),
        TermB(4, 0, -1, -1, 1828.0),
        TermB(0, 1, 0, 1, -1794.0),
        TermB(0, 0, 0, 3, -1749.0),
        TermB(0, 1, -1, 1, -1565.0),
        TermB(1, 0, 0, 1, -1491.0),
        TermB(0, 1, 1, 1, -1475.0),
        TermB(0, 1, 1, -1, -1410.0),
        TermB(0, 1, 0, -1, -1344.0),
        TermB(1, 0, 0, -1, -1335.0),
        TermB(0, 0, 3, 1, 1107.0),
        TermB(4, 0, 0, -1, 1021.0),
        TermB(4, 0, -1, 1, 833.0),
        TermB(0, 0, 1, -3, 777.0),
        TermB(4, 0, -2, 1, 671.0),
        TermB(2, 0, 0, -3, 607.0),
        TermB(2, 0, 2, -1, 596.0),
        TermB(2, -1, 1, -1, 491.0),
        TermB(2, 0, -2, 1, -451.0),
        TermB(0, 0, 3, -1, 439.0),
        TermB(2, 0, 2, 1, 422.0),
        TermB(2, 0, -3, -1, 421.0),
        TermB(2, 1, -1, 1, -366.0),
        TermB(2, 1, 0, 1, -351.0),
        TermB(4, 0, 0, 1, 331.0),
        TermB(2, -1, 1, 1, 315.0),
        TermB(2, -2, 0, -1, 302.0),
        TermB(0, 0, 1, 3, -283.0),
        TermB(2, 1, 1, -1, -229.0),
        TermB(1, 1, 0, -1, 223.0),
        TermB(1, 1, 0, 1, 223.0),
        TermB(0, 1, -2, -1, -220.0),
        TermB(2, 1, -1, -1, -220.0),
        TermB(1, 0, 1, 1, -185.0),
        TermB(2, -1, -2, -1, 181.0),
        TermB(0, 1, 2, 1, -177.0),
        TermB(4, 0, -2, -1, 176.0),
        TermB(4, -1, -1, -1, 166.0),
        TermB(1, 0, 1, -1, -164.0),
        TermB(4, 0, 1, -1, 132.0),
        TermB(1, 0, -1, -1, -119.0),
        TermB(4, -1, 0, -1, 115.0),
        TermB(2, -2, 0, 1, 107.0),
    )

    /** Apparent lunar position at the given JDE. */
    fun position(jde: Double): Position {
        val t = JulianDate.centuriesSinceJ2000(jde)

        // Fundamental arguments (Meeus eqs. 47.1–47.5), degrees.
        val lp = norm360(218.3164477 + 481267.88123421 * t - 0.0015786 * t * t +
            t * t * t / 538841.0 - t * t * t * t / 65194000.0)               // moon mean longitude L'
        val d = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t +
            t * t * t / 545868.0 - t * t * t * t / 113065000.0)              // mean elongation D
        val m = norm360(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t +
            t * t * t / 24490000.0)                                         // sun mean anomaly M
        val mp = norm360(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t +
            t * t * t / 69699.0 - t * t * t * t / 14712000.0)               // moon mean anomaly M'
        val f = norm360(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t -
            t * t * t / 3526000.0 + t * t * t * t / 863310000.0)            // argument of latitude F

        // Eccentricity correction E (eq. 47.6); powers applied per |M| multiplier.
        val e = 1.0 - 0.002516 * t - 0.0000074 * t * t

        // Additive arguments A1, A2, A3 (eq. 47.7) for planetary perturbations.
        val a1 = norm360(119.75 + 131.849 * t)
        val a2 = norm360(53.09 + 479264.290 * t)
        val a3 = norm360(313.45 + 481266.484 * t)

        var sumL = 0.0
        var sumR = 0.0
        for (term in TABLE_LR) {
            val arg = (term.d * d + term.m * m + term.mp * mp + term.f * f) * DEG2RAD
            var eFactor = 1.0
            when (kotlin.math.abs(term.m)) {
                1 -> eFactor = e
                2 -> eFactor = e * e
            }
            sumL += term.sl * eFactor * sin(arg)
            sumR += term.sr * eFactor * cos(arg)
        }

        var sumB = 0.0
        for (term in TABLE_B) {
            val arg = (term.d * d + term.m * m + term.mp * mp + term.f * f) * DEG2RAD
            var eFactor = 1.0
            when (kotlin.math.abs(term.m)) {
                1 -> eFactor = e
                2 -> eFactor = e * e
            }
            sumB += term.sb * eFactor * sin(arg)
        }

        // Additive corrections (Meeus eq. 47 text).
        sumL += 3958.0 * sin(a1 * DEG2RAD) +
            1962.0 * sin((lp - f) * DEG2RAD) +
            318.0 * sin(a2 * DEG2RAD)
        sumB += -2235.0 * sin(lp * DEG2RAD) +
            382.0 * sin(a3 * DEG2RAD) +
            175.0 * sin((a1 - f) * DEG2RAD) +
            175.0 * sin((a1 + f) * DEG2RAD) +
            127.0 * sin((lp - mp) * DEG2RAD) -
            115.0 * sin((lp + mp) * DEG2RAD)

        // Geocentric ecliptic coordinates.
        val lambda = norm360(lp + sumL / 1_000_000.0)      // apparent longitude, deg
        val beta = sumB / 1_000_000.0                       // latitude, deg
        val distanceKm = 385000.56 + sumR / 1000.0          // Earth–Moon distance, km

        // Equatorial horizontal parallax π (eq. 47.text): sin π = 6378.14 / Δ.
        val parallaxDeg = asin(6378.14 / distanceKm) * Angles.RAD2DEG

        // Apparent longitude gets a small nutation-in-longitude term; at our tolerance we
        // fold in the dominant Δψ ≈ −0.00479° sin Ω to stay consistent with the Sun routine.
        val omega = 125.04452 - 1934.136261 * t
        val lambdaApparent = lambda - 0.00479 * sin(omega * DEG2RAD)

        val eps = Coordinates.meanObliquityDeg(jde) + 0.00256 * cos(omega * DEG2RAD)
        val equatorial = Coordinates.eclipticToEquatorial(lambdaApparent, beta, eps)

        return Position(lambdaApparent, beta, distanceKm, equatorial, parallaxDeg)
    }

    /**
     * Standard altitude of the moon's centre at rise/set (spec §4.F):
     * h0 = 0.7275·π − 0.5667°, with π the equatorial horizontal parallax in degrees.
     */
    fun riseSetAltitudeDeg(parallaxDeg: Double): Double = 0.7275 * parallaxDeg - 0.5667
}
