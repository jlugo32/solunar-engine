package com.solunar.core.solunar

import com.solunar.core.solunar.Angles.DEG2RAD
import com.solunar.core.solunar.Angles.RAD2DEG
import com.solunar.core.solunar.Angles.norm360
import kotlin.math.acos
import kotlin.math.cos

/**
 * Moon phase name and illuminated fraction (Meeus ch.48).
 *
 * The illuminated fraction k = (1 + cos i) / 2, where i is the phase angle (Sun–Moon
 * geocentric elongation supplement). We compute the geocentric elongation ψ directly from
 * the Sun and Moon apparent longitudes/latitudes, which is accurate to well under the ±1 %
 * the tests require, and use the phase angle's rate of change (waxing vs waning) from the
 * elongation relative to the Sun.
 */
internal object MoonPhase {

    fun at(jde: Double): MoonPhaseInfo {
        val moon = Moon.position(jde)
        val sunLonDeg = Sun.apparentLongitudeDeg(jde)

        // Geocentric elongation ψ between Moon and Sun (Meeus eq. 48.2), using the Moon's
        // ecliptic latitude. cos ψ = cos(βmoon) · cos(λmoon − λsun).
        val cosPsi = cos(moon.eclipticLatitudeDeg * DEG2RAD) *
            cos((moon.eclipticLongitudeDeg - sunLonDeg) * DEG2RAD)
        val psi = acos(cosPsi.coerceIn(-1.0, 1.0)) * RAD2DEG

        // Phase angle i of the Moon (Sun–Moon–Earth). With Sun essentially at ∞ compared to
        // the Earth–Moon distance, i ≈ 180° − ψ is a good approximation; Meeus eq. 48.3 gives
        // the small correction, negligible at our tolerance.
        val phaseAngle = 180.0 - psi

        // Illuminated fraction k (Meeus eq. 48.1).
        val k = (1.0 + cos(phaseAngle * DEG2RAD)) / 2.0

        // Waxing while the Moon leads the Sun by 0–180° in ecliptic longitude.
        val elongationSigned = norm360(moon.eclipticLongitudeDeg - sunLonDeg)
        val waxing = elongationSigned < 180.0

        return MoonPhaseInfo(
            illuminatedFraction = k.coerceIn(0.0, 1.0),
            phaseAngleDegrees = elongationSigned,
            name = nameFor(elongationSigned),
            waxing = waxing,
        )
    }

    /**
     * Map the Sun→Moon ecliptic elongation (0 = new, 90 = first quarter, 180 = full,
     * 270 = last quarter) to one of the eight canonical phase names. The quarter/new/full
     * names occupy a narrow band around the exact angle; the rest are crescent/gibbous.
     */
    private fun nameFor(elongationDeg: Double): PhaseName {
        val e = norm360(elongationDeg)
        val band = 11.25 // ± half of 22.5° so the four "exact" names get a symmetric window
        return when {
            e < band || e >= 360 - band -> PhaseName.NEW
            e < 90 - band -> PhaseName.WAXING_CRESCENT
            e < 90 + band -> PhaseName.FIRST_QUARTER
            e < 180 - band -> PhaseName.WAXING_GIBBOUS
            e < 180 + band -> PhaseName.FULL
            e < 270 - band -> PhaseName.WANING_GIBBOUS
            e < 270 + band -> PhaseName.LAST_QUARTER
            else -> PhaseName.WANING_CRESCENT
        }
    }
}
