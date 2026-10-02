package com.solunar.core.solunar

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Formal **moon rise/set/transit vs USNO, ±2 min** suite (spec §4 acceptance).
 *
 * Every reference value below was fetched from the U.S. Naval Observatory Astronomical
 * Applications API — https://aa.usno.navy.mil/api/rstt/oneday — for the exact latitude,
 * longitude and IANA time zone stated per case (NOT a city-name lookup). USNO reports civil
 * clock times rounded to the whole minute, so the tolerance is ±2 min.
 *
 * The suite deliberately includes the two failure modes that bite solunar apps:
 *   • a Southern-Hemisphere point (exact NZ lat/lon) to catch any latitude-sign error;
 *   • US DST spring-forward AND fall-back dates to catch local-time conversion errors — the
 *     category leader shipped a "Fixed DST correction problem," confirming this is a real
 *     regression risk. We assert against the DST-correct wall-clock times java.time produces
 *     from the IANA zone (MDT on 2026-03-08, MST on 2026-11-01).
 *
 * The spring-forward case is also a natural single-event-day test: on 2026-03-08 at Boulder
 * the moon does not rise during the local day (USNO lists only Transit + Set), so the engine
 * must return a null moonrise, not a spurious one.
 */
class MoonRiseSetUsnoTest {

    private val toleranceMinutes = 2L

    private data class MoonRef(
        val label: String,
        val lat: Double,
        val lon: Double,
        val zone: String,
        val date: LocalDate,
        val rise: String?,        // "HH:mm" local, or null if no rise that local day
        val upperTransit: String?,
        val set: String?,
    )

    // --- USNO reference points (values verbatim from aa.usno.navy.mil/api/rstt/oneday) ---
    private val references = listOf(
        // 1. Northern mid-latitude baseline. New York City exact coords, EDT.
        MoonRef("NYC 40.7128,-74.0060", 40.7128, -74.0060, "America/New_York",
            LocalDate.of(2026, 7, 17), rise = "09:24", upperTransit = "16:04", set = "22:31"),

        // 2. SOUTHERN HEMISPHERE — Wellington, NZ exact coords, NZST (winter, no DST).
        //    Catches latitude-sign bugs: a sign error here throws rise/set off by hours.
        MoonRef("Wellington -41.2865,174.7762", -41.2865, 174.7762, "Pacific/Auckland",
            LocalDate.of(2026, 7, 17), rise = "09:29", upperTransit = "14:56", set = "20:33"),

        // 3. DST SPRING-FORWARD — Boulder, CO on 2026-03-08 (clocks 02:00→03:00 MDT).
        //    Single-event day: moon has NO rise locally; USNO lists only Transit + Set (DT).
        MoonRef("Boulder spring-forward 40.0150,-105.2705", 40.0150, -105.2705, "America/Denver",
            LocalDate.of(2026, 3, 8), rise = null, upperTransit = "04:43", set = "09:32"),

        // 4. DST FALL-BACK — Boulder, CO on 2026-11-01 (clocks 02:00→01:00 MST).
        //    USNO returns Standard-Time (ST) values; the engine must convert to MST, not MDT.
        MoonRef("Boulder fall-back 40.0150,-105.2705", 40.0150, -105.2705, "America/Denver",
            LocalDate.of(2026, 11, 1), rise = "23:27", upperTransit = "05:49", set = "13:13"),
    )

    @Test
    fun `moon rise set and transit match USNO within 2 minutes at every reference point`() {
        val failures = StringBuilder()
        for (ref in references) {
            val observer = Observer(ref.lat, ref.lon, ZoneId.of(ref.zone))
            val day = SolunarEngine.computeDay(ref.date, observer)

            checkEvent(ref, "moonrise", ref.rise, day.moon.rise, failures)
            checkEvent(ref, "moon upper transit", ref.upperTransit, day.moon.upperTransit, failures)
            checkEvent(ref, "moonset", ref.set, day.moon.set, failures)
        }
        assertTrue(failures.isEmpty(), "USNO moon rise/set mismatches:\n$failures")
    }

    @Test
    fun `southern hemisphere moonrise is not wildly off — explicit latitude-sign guard`() {
        // A latitude-sign bug typically shifts events by many hours or drops them entirely.
        // This asserts the Wellington moonrise is present and within 2 min of USNO 09:29.
        val wlg = references.first { it.label.startsWith("Wellington") }
        val day = SolunarEngine.computeDay(wlg.date, Observer(wlg.lat, wlg.lon, ZoneId.of(wlg.zone)))
        val rise = day.moon.rise
        assertNotNull(rise, "Southern-Hemisphere moonrise must exist (latitude-sign guard)")
        assertWithin(wlg.date, "09:29", rise, "Wellington moonrise")
    }

    @Test
    fun `spring-forward day has no moonrise — single-event day handled correctly`() {
        val spring = references.first { it.label.startsWith("Boulder spring") }
        val day = SolunarEngine.computeDay(spring.date, Observer(spring.lat, spring.lon, ZoneId.of(spring.zone)))
        assertNull(day.moon.rise, "moon should not rise locally on 2026-03-08 at Boulder (USNO: none)")
        assertNotNull(day.moon.set, "moonset should still be present that day")
    }

    @Test
    fun `fall-back day resolves to standard time not daylight time`() {
        // If the engine mishandled the DST fall-back it would report MDT (an hour early).
        // USNO moonset that day is 13:13 MST; a naive-offset bug would land near 14:13.
        val fall = references.first { it.label.startsWith("Boulder fall") }
        val day = SolunarEngine.computeDay(fall.date, Observer(fall.lat, fall.lon, ZoneId.of(fall.zone)))
        val set = day.moon.set
        assertNotNull(set)
        assertWithin(fall.date, "13:13", set, "Boulder fall-back moonset (must be MST)")
    }

    // --- helpers ---

    private fun checkEvent(
        ref: MoonRef,
        name: String,
        expected: String?,
        actual: LocalDateTime?,
        failures: StringBuilder,
    ) {
        if (expected == null) {
            if (actual != null) failures.append("  ${ref.label}: $name expected NONE but got $actual\n")
            return
        }
        if (actual == null) {
            failures.append("  ${ref.label}: $name expected $expected but got NONE\n")
            return
        }
        val exp = parse(ref.date, expected)
        val diff = Duration.between(exp, actual).abs().toMinutes()
        if (diff > toleranceMinutes) {
            failures.append("  ${ref.label}: $name expected $expected got ${actual.toLocalTime()} (Δ$diff min)\n")
        }
    }

    private fun assertWithin(date: LocalDate, expectedHhmm: String, actual: LocalDateTime, label: String) {
        val exp = parse(date, expectedHhmm)
        val diff = Duration.between(exp, actual).abs().toMinutes()
        assertTrue(diff <= toleranceMinutes, "$label expected $expectedHhmm got ${actual.toLocalTime()} (Δ$diff min)")
    }

    private fun parse(date: LocalDate, hhmm: String): LocalDateTime {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return date.atTime(h, m)
    }
}
