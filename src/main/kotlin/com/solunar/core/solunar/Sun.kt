package com.solunar.core.solunar

import com.solunar.core.solunar.Angles.DEG2RAD
import com.solunar.core.solunar.Angles.norm360
import kotlin.math.sin

/**
 * Apparent solar position (Meeus ch.25, low-precision "the sun" routine).
 *
 * Accurate to about 0.01° in longitude over the centuries around J2000 — far better than
 * needed for sunrise/sunset at ±1 min. The engine uses this both for the sun's own rise/set
 * (h0 = −0.833°) and for the sunrise/sunset overlap bonus in the day rating (spec §4.I).
 */
internal object Sun {

    /** Standard geometric altitude of the sun's centre at rise/set: −0.8333° (refraction + semidiameter). */
    const val RISE_SET_ALTITUDE_DEG = -0.8333

    /** Apparent equatorial position of the sun at the given JDE. */
    fun position(jde: Double): Coordinates.Equatorial {
        val t = JulianDate.centuriesSinceJ2000(jde)

        // Geometric mean longitude of the sun (eq. 25.2).
        val l0 = norm360(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        // Mean anomaly of the sun (eq. 25.3).
        val m = norm360(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val mRad = m * DEG2RAD
        // Eccentricity of Earth's orbit (eq. 25.4).
        val e = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t

        // Equation of the centre C (eq. 25.4 text).
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
            (0.019993 - 0.000101 * t) * sin(2 * mRad) +
            0.000289 * sin(3 * mRad)

        // True geometric longitude ⊙ and true anomaly ν.
        val trueLon = l0 + c

        // Apparent longitude λ (nutation + aberration, eq. 25.8-ish): correct with Ω.
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLon - 0.00569 - 0.00478 * sin(omega * DEG2RAD)

        // Obliquity, corrected by the same nutation term for consistency with apparent λ.
        val eps = Coordinates.meanObliquityDeg(jde) + 0.00256 * kotlin.math.cos(omega * DEG2RAD)

        // Sun's ecliptic latitude is ~0.
        return Coordinates.eclipticToEquatorial(lambda, 0.0, eps)
    }

    /** Apparent geometric ecliptic longitude of the sun in degrees (used by MoonPhase for elongation). */
    fun apparentLongitudeDeg(jde: Double): Double {
        val t = JulianDate.centuriesSinceJ2000(jde)
        val l0 = norm360(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val m = norm360(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val mRad = m * DEG2RAD
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
            (0.019993 - 0.000101 * t) * sin(2 * mRad) +
            0.000289 * sin(3 * mRad)
        val trueLon = l0 + c
        val omega = 125.04 - 1934.136 * t
        return norm360(trueLon - 0.00569 - 0.00478 * sin(omega * DEG2RAD))
    }
}
