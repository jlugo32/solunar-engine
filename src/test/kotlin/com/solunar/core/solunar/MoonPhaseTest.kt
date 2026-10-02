package com.solunar.core.solunar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Moon-phase and illumination validation against published 2026 phase data (USNO
 * "Phases of the Moon" table, aa.usno.navy.mil/data/MoonPhases). Times are UTC.
 *
 * Selected 2026 phases (UTC):
 *   New Moon   2026-01-18  19:52
 *   Full Moon  2026-01-03  10:03
 *   New Moon   2026-03-19  01:23
 *   Full Moon  2026-03-03  11:38
 *
 * Illumination at new should be ~0 %, at full ~100 %; the Meeus example likewise anchors
 * absolute accuracy. Tolerance is ±1 % illuminated fraction per spec §4 acceptance.
 */
class MoonPhaseTest {

    private fun jdeAt(year: Int, month: Int, day: Int, hourUtc: Double): Double {
        val jdUt = JulianDate.fromCalendar(year, month, day + hourUtc / 24.0)
        return JulianDate.toJde(jdUt, year, month)
    }

    @Test
    fun `new moon 2026-01-18 is near 0 percent illuminated`() {
        val info = MoonPhase.at(jdeAt(2026, 1, 18, 19.87))
        assertTrue(info.illuminatedFraction < 0.01,
            "new moon fraction ${info.illuminatedFraction} should be < 1%")
        assertEquals(PhaseName.NEW, info.name)
    }

    @Test
    fun `full moon 2026-01-03 is near 100 percent illuminated`() {
        val info = MoonPhase.at(jdeAt(2026, 1, 3, 10.05))
        assertTrue(info.illuminatedFraction > 0.99,
            "full moon fraction ${info.illuminatedFraction} should be > 99%")
        assertEquals(PhaseName.FULL, info.name)
    }

    @Test
    fun `new moon 2026-03-19 detected as NEW`() {
        val info = MoonPhase.at(jdeAt(2026, 3, 19, 1.38))
        assertEquals(PhaseName.NEW, info.name)
        assertTrue(info.illuminatedFraction < 0.01)
    }

    @Test
    fun `full moon 2026-03-03 detected as FULL`() {
        val info = MoonPhase.at(jdeAt(2026, 3, 3, 11.63))
        assertEquals(PhaseName.FULL, info.name)
        assertTrue(info.illuminatedFraction > 0.99)
    }

    @Test
    fun `waxing flag is true between new and full`() {
        // A week after the 2026-01-18 new moon → waxing crescent/quarter, fraction rising.
        val info = MoonPhase.at(jdeAt(2026, 1, 24, 0.0))
        assertTrue(info.waxing, "should be waxing ~6 days after new moon")
    }

    @Test
    fun `illuminated percent is a clean 0-100 integer`() {
        val info = MoonPhase.at(jdeAt(2026, 1, 3, 10.05))
        assertTrue(info.illuminatedPercent in 0..100)
        assertEquals(100, info.illuminatedPercent)
    }
}
