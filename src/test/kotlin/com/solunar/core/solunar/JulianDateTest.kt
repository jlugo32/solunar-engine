package com.solunar.core.solunar

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Julian Day conversions validated against Meeus, *Astronomical Algorithms* (2nd ed.),
 * worked examples in ch.7. These fix the foundation every later routine depends on.
 */
class JulianDateTest {

    @Test
    fun `Meeus example 7a — 1957 Oct 4_81 UT is JD 2436116_31`() {
        // Meeus ch.7, Example 7.a (Sputnik launch).
        val jd = JulianDate.fromCalendar(1957, 10, 4.81)
        assertEquals(2436116.31, jd, 1e-6)
    }

    @Test
    fun `J2000_0 epoch is JD 2451545_0`() {
        // 2000 Jan 1, 12:00 TT — the definitional J2000.0 epoch.
        val jd = JulianDate.fromCalendar(2000, 1, 1.5)
        assertEquals(2451545.0, jd, 1e-6)
    }

    @Test
    fun `fromUtc matches fromCalendar for a known instant`() {
        // 2026-07-17T00:00:00Z
        val instant = Instant.parse("2026-07-17T00:00:00Z")
        val jd = JulianDate.fromUtc(instant)
        val expected = JulianDate.fromCalendar(2026, 7, 17.0)
        assertEquals(expected, jd, 1e-9)
    }

    @Test
    fun `deltaT for 2026 is in the expected ~73-77 second band`() {
        // Espenak–Meeus 2005–2050 polynomial: 62.92 + 0.32217·t + 0.005589·t² with t = y−2000.
        // At mid-2026 (t≈26.5) this gives ≈75.4 s, consistent with published ΔT projections.
        val dt = JulianDate.deltaTSeconds(2026, 7)
        assertTrue(dt in 73.0..77.0, "ΔT(2026) was $dt, expected ~75 s")
    }

    @Test
    fun `centuries since J2000 is zero at the epoch`() {
        assertEquals(0.0, JulianDate.centuriesSinceJ2000(2451545.0), 1e-12)
    }

    @Test
    fun `deltaT is a polynomial of year — it varies, not a pinned 2026 constant`() {
        // Guards against ΔT ever being frozen to a single value: a body that grows roughly
        // quadratically over the coming decades must return materially different numbers for
        // 2026, 2040, and 2100. If someone replaced the polynomial with `return 75.0`, this
        // fails immediately.
        val dt2026 = JulianDate.deltaTSeconds(2026, 1)
        val dt2040 = JulianDate.deltaTSeconds(2040, 1)
        val dt2100 = JulianDate.deltaTSeconds(2100, 1)
        assertTrue(dt2040 - dt2026 > 3.0, "ΔT should grow ~5-6 s from 2026→2040, got ${dt2040 - dt2026}")
        assertTrue(dt2100 - dt2040 > 10.0, "ΔT should grow substantially 2040→2100, got ${dt2100 - dt2040}")
    }

    @Test
    fun `deltaT far-future date does not drift to an absurd value`() {
        // Year 2400 uses the Meeus long-term parabola fallback; result must be finite and
        // physically plausible (hundreds of seconds), never NaN/∞ or a stale 75 s.
        val dt = JulianDate.deltaTSeconds(2400, 6)
        assertTrue(dt.isFinite() && dt > 150.0, "far-future ΔT should be a large finite value, got $dt")
    }
}
