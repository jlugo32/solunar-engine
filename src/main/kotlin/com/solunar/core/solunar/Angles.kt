package com.solunar.core.solunar

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor

/**
 * Angle and interpolation helpers, following Meeus, *Astronomical Algorithms* (2nd ed.).
 *
 * All astronomy code in this package uses these instead of scattering conversions and
 * range-normalisation inline, so the numeric behaviour is defined in exactly one place
 * and can be audited. Pure functions, no state, no framework imports.
 */
internal object Angles {

    const val DEG2RAD = PI / 180.0
    const val RAD2DEG = 180.0 / PI

    /** Degrees -> radians. */
    fun rad(deg: Double): Double = deg * DEG2RAD

    /** Radians -> degrees. */
    fun deg(rad: Double): Double = rad * RAD2DEG

    /** Reduce an angle in degrees to the range [0, 360). */
    fun norm360(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** Reduce an angle in degrees to the range (-180, 180]. */
    fun norm180(deg: Double): Double {
        var r = norm360(deg)
        if (r > 180.0) r -= 360.0
        return r
    }

    /** Reduce an hour value to the range [0, 24). */
    fun norm24(hours: Double): Double {
        val r = hours % 24.0
        return if (r < 0) r + 24.0 else r
    }

    /**
     * Three-point interpolation (Meeus ch.3, eq. 3.3).
     *
     * @param y1 value at the first tabular argument
     * @param y2 value at the central tabular argument
     * @param y3 value at the third tabular argument
     * @param n  interpolating factor in [-1, 1] (fraction of the interval from the centre)
     */
    fun interpolate3(y1: Double, y2: Double, y3: Double, n: Double): Double {
        val a = y2 - y1
        val b = y3 - y2
        val c = b - a
        return y2 + (n / 2.0) * (a + b + n * c)
    }

    /**
     * Find the interpolating factor n0 (Meeus ch.3, eq. 3.7) at which a quantity crosses
     * zero, given three tabular values around the crossing. Returns null if the three
     * values do not bracket a zero (the classic curvature-corrected root, iterated).
     */
    fun zeroCrossingFactor(y1: Double, y2: Double, y3: Double): Double? {
        val a = y2 - y1
        val b = y3 - y2
        val c = b - a
        if (c == 0.0 && a + b == 0.0) return null
        var n0 = 0.0
        // Meeus: n0 = -2*y2 / (a + b + c*n0), iterated to convergence.
        repeat(20) {
            val denom = a + b + c * n0
            if (denom == 0.0) return null
            val next = -2.0 * y2 / denom
            if (abs(next - n0) < 1e-9) return next
            n0 = next
        }
        return n0
    }

    /** Fractional part of x, always in [0, 1). */
    fun frac(x: Double): Double {
        val f = x - floor(x)
        return if (f < 0) f + 1.0 else f
    }
}
