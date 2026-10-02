package com.solunar.core.solunar

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Engine-level behaviour (spec §4.G) that must hold independent of any external reference:
 * window construction, ordering, widths, determinism, and edge-case handling.
 */
class SolunarEngineTest {

    private val nyc = Observer(40.7128, -74.0060, ZoneId.of("America/New_York"))
    private val date = LocalDate.of(2026, 7, 17)

    @Test
    fun `computeDay is deterministic — same inputs give identical output`() {
        val a = SolunarEngine.computeDay(date, nyc)
        val b = SolunarEngine.computeDay(date, nyc)
        assertEquals(a, b, "engine must be a pure function of (date, observer)")
    }

    @Test
    fun `major windows are ~2h wide and minor windows ~1h wide`() {
        val day = SolunarEngine.computeDay(date, nyc)
        day.majorWindows.forEach {
            assertEquals(120, Duration.between(it.start, it.end).toMinutes(),
                "major window should be 120 min")
        }
        day.minorWindows.forEach {
            assertEquals(60, Duration.between(it.start, it.end).toMinutes(),
                "minor window should be 60 min")
        }
    }

    @Test
    fun `window center sits exactly between start and end`() {
        val day = SolunarEngine.computeDay(date, nyc)
        day.windows.forEach {
            val toStart = Duration.between(it.start, it.center)
            val toEnd = Duration.between(it.center, it.end)
            assertEquals(toStart, toEnd, "center must be the midpoint of the window")
        }
    }

    @Test
    fun `windows are returned sorted by start time`() {
        val day = SolunarEngine.computeDay(date, nyc)
        val starts = day.windows.map { it.start }
        assertEquals(starts.sorted(), starts, "windows must be sorted by start")
    }

    @Test
    fun `there are at most two major and at most two minor windows`() {
        val day = SolunarEngine.computeDay(date, nyc)
        assertTrue(day.majorWindows.size <= 2, "at most two major windows")
        assertTrue(day.minorWindows.size <= 2, "at most two minor windows")
    }

    @Test
    fun `high-latitude summer day does not throw and yields a coherent day`() {
        // Tromsø in June — polar day: sun may never set, moon events may be absent.
        val tromso = Observer(69.6496, 18.9560, ZoneId.of("Europe/Oslo"))
        val day = SolunarEngine.computeDay(LocalDate.of(2026, 6, 21), tromso)
        // Must not crash; rating still computed and within range.
        assertTrue(day.rating.score in 0..100)
        assertTrue(day.rating.stars in 0..4)
    }

    @Test
    fun `equatorial location produces a valid day`() {
        val quito = Observer(-0.1807, -78.4678, ZoneId.of("America/Guayaquil"))
        val day = SolunarEngine.computeDay(date, quito)
        assertTrue(day.rating.score in 0..100)
        // Near the equator the sun both rises and sets every day.
        assertTrue(day.sun.rise != null && day.sun.set != null)
    }

    @Test
    fun `rating score maps to stars via documented thresholds`() {
        val day = SolunarEngine.computeDay(date, nyc)
        val expectedStars = when {
            day.rating.score >= 90 -> 4
            day.rating.score >= 70 -> 3
            day.rating.score >= 50 -> 2
            day.rating.score >= 25 -> 1
            else -> 0
        }
        assertEquals(expectedStars, day.rating.stars)
    }
}
