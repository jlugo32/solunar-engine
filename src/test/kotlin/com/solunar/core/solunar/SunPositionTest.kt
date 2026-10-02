package com.solunar.core.solunar

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates the low-precision solar routine against Meeus, *Astronomical Algorithms* (2nd
 * ed.), **Example 25.a/25.b**: 1992 October 13, at 0h Dynamical Time.
 *
 * Meeus's published results:
 *   geometric mean longitude L0 = 201.80720°
 *   apparent longitude          λ = 199.90895°  (≈ 199°54′32″, after nutation+aberration)
 *   apparent right ascension    α = 198.38083°  (13h 13m 31.4s → 198.38083° when ×15)
 *   apparent declination        δ =  −7.78507°  (−7°47′06″)
 *
 * Tolerances are generous enough for the truncated equation-of-centre yet tight enough to
 * catch any real error in the routine.
 */
class SunPositionTest {

    private val jde1992 = JulianDate.fromCalendar(1992, 10, 13.0)

    @Test
    fun `apparent longitude matches Meeus example 25a`() {
        val lambda = Sun.apparentLongitudeDeg(jde1992)
        assertEquals(199.90895, lambda, 0.01, "sun apparent longitude off vs Meeus 25.a")
    }

    @Test
    fun `apparent right ascension matches Meeus example 25b`() {
        val eq = Sun.position(jde1992)
        assertEquals(198.38083, eq.rightAscensionDeg, 0.02, "sun RA off vs Meeus 25.b")
    }

    @Test
    fun `apparent declination matches Meeus example 25b`() {
        val eq = Sun.position(jde1992)
        assertEquals(-7.78507, eq.declinationDeg, 0.02, "sun Dec off vs Meeus 25.b")
    }
}
