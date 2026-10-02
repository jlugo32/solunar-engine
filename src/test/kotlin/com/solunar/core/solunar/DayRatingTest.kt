package com.solunar.core.solunar

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Day-rating formula (spec §4.I). Verifies the documented curve directly, using the public
 * [DayRating] contract so the exact numbers in the KDoc are enforced by tests.
 */
class DayRatingTest {

    private val noSun: LocalDateTime? = null

    @Test
    fun `new moon with no overlap scores the full phase weight`() {
        // elongation 0° → phaseFactor 1.0 → 70 points, no windows → 0 overlap.
        val r = DayRatingCalculator.compute(0.0, emptyList(), noSun, noSun)
        assertEquals(70, r.score)
        assertEquals(3, r.stars) // 70 → 3 stars
    }

    @Test
    fun `full moon with no overlap also scores the full phase weight`() {
        // elongation 180° → cos(360°)=1 → phaseFactor 1.0 → 70 points.
        val r = DayRatingCalculator.compute(180.0, emptyList(), noSun, noSun)
        assertEquals(70, r.score)
    }

    @Test
    fun `quarter moon scores near zero phase points`() {
        // elongation 90° → cos(180°) = −1 → phaseFactor 0 → 0 points.
        val r = DayRatingCalculator.compute(90.0, emptyList(), noSun, noSun)
        assertEquals(0, r.score)
        assertEquals(0, r.stars)
    }

    @Test
    fun `full moon with all windows dead-on sun events hits 100`() {
        // Phase 70 + full overlap 30 = 100.
        val sunrise = LocalDate.of(2026, 7, 17).atTime(6, 0)
        val sunset = LocalDate.of(2026, 7, 17).atTime(20, 0)
        val windows = listOf(
            window(WindowType.MAJOR, sunrise),
            window(WindowType.MAJOR, sunset),
            window(WindowType.MINOR, sunrise),
            window(WindowType.MINOR, sunset),
        )
        val r = DayRatingCalculator.compute(180.0, windows, sunrise, sunset)
        assertEquals(100, r.score)
        assertEquals(4, r.stars)
    }

    @Test
    fun `overlap decays to zero beyond the falloff window`() {
        val sunrise = LocalDate.of(2026, 7, 17).atTime(6, 0)
        val sunset = LocalDate.of(2026, 7, 17).atTime(20, 0)
        // Windows centered far (3+ h) from any sun event → no overlap bonus.
        val farCenter = LocalDate.of(2026, 7, 17).atTime(12, 0)
        val windows = listOf(window(WindowType.MAJOR, farCenter))
        val r = DayRatingCalculator.compute(180.0, windows, sunrise, sunset)
        assertEquals(70, r.score) // phase only, no overlap
    }

    @Test
    fun `score is always clamped to 0-100 and stars to 0-4`() {
        for (elong in 0..350 step 10) {
            val r = DayRatingCalculator.compute(elong.toDouble(), emptyList(), noSun, noSun)
            assertTrue(r.score in 0..100)
            assertTrue(r.stars in 0..4)
        }
    }

    private fun window(type: WindowType, center: LocalDateTime) =
        SolunarWindow(type, center.minusMinutes(60), center, center.plusMinutes(60))
}
