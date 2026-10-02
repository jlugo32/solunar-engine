package com.solunar.core.solunar

import com.solunar.core.solunar.Angles.DEG2RAD
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.cos

/**
 * Day "bite" rating (spec §4.I) — a 0–100 score and 0–4 stars.
 *
 * ## Formula (auditable, tunable — change the constants, not the shape)
 *
 * The score is a weighted sum of two independent, well-understood solunar effects,
 * clamped to [0, 100]:
 *
 * 1. **Lunar phase base** (`PHASE_WEIGHT`, default 70 points).
 *    Feeding activity is empirically strongest near the new and full moon (strong tides,
 *    the moon transiting near midnight/noon), weakest at the quarters. We model this with a
 *    raised cosine of *twice* the Sun→Moon elongation θ, so both θ = 0° (new) and θ = 180°
 *    (full) map to the maximum and the quarters (90°, 270°) map to the minimum:
 *
 *        phaseFactor = (1 + cos(2θ)) / 2          // 1.0 at new/full, 0.0 at quarters
 *        phasePoints = PHASE_WEIGHT * phaseFactor
 *
 * 2. **Sun/Moon overlap bonus** (`OVERLAP_WEIGHT`, default 30 points).
 *    A major or minor period that coincides with sunrise or sunset is prime time. For each
 *    of the (up to 4) windows, we find the smallest gap between the window's centre and
 *    either sunrise or sunset; a gap of 0 scores the full per-window bonus and it decays
 *    linearly to 0 at `OVERLAP_FALLOFF_MIN` minutes. Majors count double minors. The bonus
 *    is normalised so the theoretical maximum (all windows dead-on a sun event) is exactly
 *    OVERLAP_WEIGHT:
 *
 *        perWindow = weight * max(0, 1 − gapMinutes / OVERLAP_FALLOFF_MIN)
 *        overlapPoints = OVERLAP_WEIGHT * (Σ perWindow / Σ maxWeight)
 *
 *        score = round(phasePoints + overlapPoints)   // clamped to [0, 100]
 *
 * Stars are fixed thresholds on the score:
 *
 *        90–100 → 4 ★   70–89 → 3 ★   50–69 → 2 ★   25–49 → 1 ★   0–24 → 0 ★
 *
 * All weights/thresholds live as named constants below so the curve can be retuned without
 * touching logic, and the KDoc above stays the single source of truth for what the number
 * means.
 */
internal object DayRatingCalculator {

    private const val PHASE_WEIGHT = 70.0
    private const val OVERLAP_WEIGHT = 30.0
    private const val OVERLAP_FALLOFF_MIN = 90.0
    private const val MAJOR_WINDOW_WEIGHT = 2.0
    private const val MINOR_WINDOW_WEIGHT = 1.0

    fun compute(
        sunToMoonElongationDeg: Double,
        windows: List<SolunarWindow>,
        sunrise: LocalDateTime?,
        sunset: LocalDateTime?,
    ): DayRating {
        // 1. Phase base: raised cosine of 2θ → peaks at new (0°) and full (180°).
        val phaseFactor = (1.0 + cos(2.0 * sunToMoonElongationDeg * DEG2RAD)) / 2.0
        val phasePoints = PHASE_WEIGHT * phaseFactor

        // 2. Overlap bonus.
        var weightedSum = 0.0
        var maxPossible = 0.0
        for (w in windows) {
            val weight = if (w.type == WindowType.MAJOR) MAJOR_WINDOW_WEIGHT else MINOR_WINDOW_WEIGHT
            maxPossible += weight
            val gap = smallestGapMinutes(w.center, sunrise, sunset)
            if (gap != null) {
                val proximity = (1.0 - gap / OVERLAP_FALLOFF_MIN).coerceAtLeast(0.0)
                weightedSum += weight * proximity
            }
        }
        val overlapPoints = if (maxPossible > 0) OVERLAP_WEIGHT * (weightedSum / maxPossible) else 0.0

        val score = Math.round(phasePoints + overlapPoints).toInt().coerceIn(0, 100)
        return DayRating(score = score, stars = starsFor(score))
    }

    private fun smallestGapMinutes(center: LocalDateTime, sunrise: LocalDateTime?, sunset: LocalDateTime?): Double? {
        val gaps = listOfNotNull(
            sunrise?.let { abs(Duration.between(center, it).toMinutes()).toDouble() },
            sunset?.let { abs(Duration.between(center, it).toMinutes()).toDouble() },
        )
        return gaps.minOrNull()
    }

    private fun abs(v: Long): Long = if (v < 0) -v else v

    private fun starsFor(score: Int): Int = when {
        score >= 90 -> 4
        score >= 70 -> 3
        score >= 50 -> 2
        score >= 25 -> 1
        else -> 0
    }
}
