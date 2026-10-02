package com.solunar.core.solunar

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates the abbreviated ELP moon series against Meeus, *Astronomical Algorithms* (2nd
 * ed.), **Example 47.a**: 1992 April 12, at 0h Dynamical Time.
 *
 * Meeus's published results for that instant:
 *   apparent ecliptic longitude λ = 133.162655°
 *   ecliptic latitude              β =  −3.229126°
 *   distance                       Δ = 368409.7 km
 *   equatorial horizontal parallax π =   0.991990°  (≈ 0° 59′ 31″.2)
 *
 * These are the tightest, least-ambiguous published checks available for the moon routine,
 * so they anchor the whole engine's accuracy. Tolerances allow for the truncated series
 * (Meeus states the abbreviated tables give ~10″ in longitude ≈ 0.003°).
 */
class MoonPositionTest {

    // 1992 April 12.0 TD. Meeus works in Dynamical Time directly, so JDE == this JD.
    private val jde1992 = JulianDate.fromCalendar(1992, 4, 12.0)

    @Test
    fun `longitude matches Meeus example 47a within truncation tolerance`() {
        val pos = Moon.position(jde1992)
        // The apparent-longitude nutation term (~0.005°) is folded in; compare to Meeus's
        // apparent λ with a tolerance covering series truncation.
        assertEquals(133.162655, pos.eclipticLongitudeDeg, 0.02,
            "moon longitude off vs Meeus 47.a")
    }

    @Test
    fun `latitude matches Meeus example 47a`() {
        val pos = Moon.position(jde1992)
        assertEquals(-3.229126, pos.eclipticLatitudeDeg, 0.02,
            "moon latitude off vs Meeus 47.a")
    }

    @Test
    fun `distance matches Meeus example 47a within a few km`() {
        val pos = Moon.position(jde1992)
        assertEquals(368409.7, pos.distanceKm, 30.0,
            "moon distance off vs Meeus 47.a")
    }

    @Test
    fun `horizontal parallax matches Meeus example 47a`() {
        val pos = Moon.position(jde1992)
        assertEquals(0.991990, pos.parallaxDeg, 0.001,
            "moon parallax off vs Meeus 47.a")
    }
}
