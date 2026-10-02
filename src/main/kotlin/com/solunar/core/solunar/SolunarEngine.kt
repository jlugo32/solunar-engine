package com.solunar.core.solunar

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Public entry point to the solunar core (spec §4.G).
 *
 * `computeDay(date, observer)` returns a fully-populated, immutable [SolunarDay]:
 * major windows (moon transit ± 1 h), minor windows (moonrise/moonset ± 0.5 h), sun and
 * moon rise/set, moon phase, and the day rating. Deterministic and framework-free — the
 * only inputs are the date and the observer; nothing reads the system clock or performs I/O.
 *
 * This is the sole class the Android layer needs to call. Everything under it (Sun, Moon,
 * RiseTransitSet, MoonPhase, DayRating) is `internal`.
 */
object SolunarEngine {

    private val MAJOR_HALF_WIDTH = Duration.ofHours(1)        // major = transit ± 1 h (~2 h)
    private val MINOR_HALF_WIDTH = Duration.ofMinutes(30)     // minor = rise/set ± 0.5 h (~1 h)

    fun computeDay(date: LocalDate, observer: Observer): SolunarDay {
        val sun = RiseTransitSet.solve(RiseTransitSet.Body.SUN, date, observer)
        val moon = RiseTransitSet.solve(RiseTransitSet.Body.MOON, date, observer)

        val windows = buildWindows(moon).sortedBy { it.start }

        // Phase + rating are evaluated at local noon, a stable representative instant for the day.
        val noon = date.atTime(12, 0)
        val jdUtNoon = JulianDate.fromUtc(noon.atZone(observer.zone).toInstant())
        val jdeNoon = JulianDate.toJde(jdUtNoon, date.year, date.monthValue)
        val phase = MoonPhase.at(jdeNoon)

        val rating = DayRatingCalculator.compute(
            sunToMoonElongationDeg = phase.phaseAngleDegrees,
            windows = windows,
            sunrise = sun.rise,
            sunset = sun.set,
        )

        return SolunarDay(
            date = date,
            observer = observer,
            windows = windows,
            sun = sun,
            moon = moon,
            moonPhase = phase,
            rating = rating,
        )
    }

    /** Build major windows from the two lunar transits and minor windows from moonrise/moonset. */
    private fun buildWindows(moon: RiseSetTimes): List<SolunarWindow> {
        val out = ArrayList<SolunarWindow>(4)
        moon.upperTransit?.let { out.add(major(it)) }
        moon.lowerTransit?.let { out.add(major(it)) }
        moon.rise?.let { out.add(minor(it)) }
        moon.set?.let { out.add(minor(it)) }
        return out
    }

    private fun major(center: LocalDateTime) = SolunarWindow(
        type = WindowType.MAJOR,
        start = center.minus(MAJOR_HALF_WIDTH),
        center = center,
        end = center.plus(MAJOR_HALF_WIDTH),
    )

    private fun minor(center: LocalDateTime) = SolunarWindow(
        type = WindowType.MINOR,
        start = center.minus(MINOR_HALF_WIDTH),
        center = center,
        end = center.plus(MINOR_HALF_WIDTH),
    )
}
