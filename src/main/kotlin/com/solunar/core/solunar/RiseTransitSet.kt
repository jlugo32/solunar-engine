package com.solunar.core.solunar

import com.solunar.core.solunar.Angles.norm180
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Rise, set and transit solver by altitude scanning + bisection.
 *
 * Rather than Meeus ch.15's tabular three-point interpolation — which is awkward for the
 * fast-moving moon and for days with a single rise or set — we sample the body's true
 * altitude (recomputing the full ephemeris at each sample, so the moon's motion is captured
 * exactly) at a fine step across a local day, detect sign changes of (altitude − h0), and
 * bisect each crossing to the second. This is numerically robust and naturally handles
 * midnight-crossing, single-event, and always-up/never-up days (spec §4.E–G).
 */
internal object RiseTransitSet {

    /** Which body to solve for; supplies the ephemeris and the rise/set standard altitude. */
    internal enum class Body { SUN, MOON }

    private const val SAMPLE_STEP_MINUTES = 5.0      // scan resolution before bisection
    private const val BISECTION_SECONDS = 1.0        // refine crossings to ~1 s

    private data class Sample(val jdUt: Double, val instant: Instant, val altMinusH0: Double, val hourAngle: Double)

    /**
     * Solve rise/set/upper-transit/lower-transit for [body] on the local calendar [date]
     * at [observer]. Returns times in the observer's zone, or null per event when it does
     * not occur that local day.
     */
    fun solve(body: Body, date: LocalDate, observer: Observer): RiseSetTimes {
        // Scan the whole local day plus a small margin so events near local midnight are seen.
        val zone = observer.zone
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

        // Build the altitude samples across [dayStart, dayEnd].
        val samples = ArrayList<Sample>()
        var tInstant = dayStart
        val stepSeconds = (SAMPLE_STEP_MINUTES * 60).toLong()
        while (!tInstant.isAfter(dayEnd)) {
            samples.add(sampleAt(body, tInstant, observer))
            tInstant = tInstant.plusSeconds(stepSeconds)
        }

        var rise: LocalDateTime? = null
        var set: LocalDateTime? = null

        // Rise/set = sign changes of (altitude − h0).
        for (i in 0 until samples.size - 1) {
            val a = samples[i]
            val b = samples[i + 1]
            if (a.altMinusH0 == 0.0) continue
            if (a.altMinusH0 < 0 && b.altMinusH0 > 0 && rise == null) {
                rise = bisectAltitude(body, a, b, observer).atZone(zone).toLocalDateTime()
            } else if (a.altMinusH0 > 0 && b.altMinusH0 < 0 && set == null) {
                set = bisectAltitude(body, a, b, observer).atZone(zone).toLocalDateTime()
            }
        }

        // Transits = the hour angle passing through 0h (upper) / 12h (lower). We track the
        // signed hour angle folded to (−180, 180]: upper transit is where it crosses 0, and
        // the extreme of altitude confirms which is upper vs lower.
        val (upper, lower) = solveTransits(body, samples, observer)

        return RiseSetTimes(rise, set, upper, lower)
    }

    private fun sampleAt(body: Body, instant: Instant, observer: Observer): Sample {
        val ldt = instant.atOffset(ZoneOffset.UTC)
        val jdUt = JulianDate.fromUtc(instant)
        val jde = JulianDate.toJde(jdUt, ldt.year, ldt.monthValue)

        val (eq, h0) = when (body) {
            Body.SUN -> Sun.position(jde) to Sun.RISE_SET_ALTITUDE_DEG
            Body.MOON -> {
                val p = Moon.position(jde)
                p.equatorial to Moon.riseSetAltitudeDeg(p.parallaxDeg)
            }
        }
        val gmst = Coordinates.greenwichMeanSiderealDeg(jdUt)
        val ha = Coordinates.hourAngleDeg(gmst, observer.longitude, eq.rightAscensionDeg)
        val alt = Coordinates.altitudeDeg(ha, eq.declinationDeg, observer.latitude)
        // Fold hour angle to (−180, 180] so 0 = upper transit and ±180 = lower transit.
        return Sample(jdUt, instant, alt - h0, norm180(ha))
    }

    /** Bisect a rise/set crossing between two bracketing samples to ~1-second precision. */
    private fun bisectAltitude(body: Body, lo: Sample, hi: Sample, observer: Observer): Instant {
        var a = lo
        var b = hi
        while (b.instant.epochSecond - a.instant.epochSecond > BISECTION_SECONDS) {
            val midInstant = Instant.ofEpochSecond((a.instant.epochSecond + b.instant.epochSecond) / 2)
            val mid = sampleAt(body, midInstant, observer)
            if ((a.altMinusH0 < 0) == (mid.altMinusH0 < 0)) a = mid else b = mid
        }
        return a.instant
    }

    /**
     * Locate upper (HA = 0h) and lower (HA = ±12h) transits by finding where the folded
     * hour angle crosses 0 and ±180 respectively, then bisecting. Returns (upper, lower);
     * either may be null if it doesn't fall inside the local day.
     */
    private fun solveTransits(
        body: Body,
        samples: List<Sample>,
        observer: Observer,
    ): Pair<LocalDateTime?, LocalDateTime?> {
        val zone = observer.zone
        var upper: LocalDateTime? = null
        var lower: LocalDateTime? = null
        for (i in 0 until samples.size - 1) {
            val a = samples[i]
            val b = samples[i + 1]
            // Upper transit: folded HA goes from negative to positive through 0.
            if (upper == null && a.hourAngle < 0 && b.hourAngle > 0 && abs(a.hourAngle) < 90 && abs(b.hourAngle) < 90) {
                upper = bisectHourAngle(body, a, b, observer, target = 0.0).atZone(zone).toLocalDateTime()
            }
            // Lower transit: folded HA wraps across ±180 (from +side to −side jumping the seam).
            if (lower == null && a.hourAngle > 90 && b.hourAngle < -90) {
                lower = bisectHourAngleSeam(body, a, b, observer).atZone(zone).toLocalDateTime()
            }
        }
        return upper to lower
    }

    private fun bisectHourAngle(body: Body, lo: Sample, hi: Sample, observer: Observer, target: Double): Instant {
        var a = lo
        var b = hi
        while (b.instant.epochSecond - a.instant.epochSecond > BISECTION_SECONDS) {
            val midInstant = Instant.ofEpochSecond((a.instant.epochSecond + b.instant.epochSecond) / 2)
            val mid = sampleAt(body, midInstant, observer)
            if ((a.hourAngle - target < 0) == (mid.hourAngle - target < 0)) a = mid else b = mid
        }
        return a.instant
    }

    /** Bisect the lower transit where the folded hour angle crosses the ±180 seam. */
    private fun bisectHourAngleSeam(body: Body, lo: Sample, hi: Sample, observer: Observer): Instant {
        var a = lo
        var b = hi
        // Map HA to distance-from-180 so the seam becomes an ordinary zero crossing.
        fun distFrom180(ha: Double): Double = if (ha >= 0) 180.0 - ha else -(180.0 + ha)
        while (b.instant.epochSecond - a.instant.epochSecond > BISECTION_SECONDS) {
            val midInstant = Instant.ofEpochSecond((a.instant.epochSecond + b.instant.epochSecond) / 2)
            val mid = sampleAt(body, midInstant, observer)
            if ((distFrom180(a.hourAngle) < 0) == (distFrom180(mid.hourAngle) < 0)) a = mid else b = mid
        }
        return a.instant
    }
}
