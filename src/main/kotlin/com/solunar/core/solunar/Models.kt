package com.solunar.core.solunar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Public data model returned by the solunar engine. Framework-free: only java.time and
 * primitives, so this compiles unchanged in both the JVM test module and the Android app.
 */

/** An observer's position and clock zone. Coarse lat/lon is sufficient for solunar (spec §3). */
data class Observer(
    /** Latitude in degrees, north positive. */
    val latitude: Double,
    /** Longitude in degrees, EAST positive (Meeus convention is west-positive; we convert internally). */
    val longitude: Double,
    /** Zone used to render all returned instants as local wall-clock times. */
    val zone: ZoneId,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}

/** Kind of feeding/activity window. */
enum class WindowType {
    /** Centred on a lunar transit (moon overhead / underfoot). ~2 h wide. */
    MAJOR,

    /** Centred on moonrise or moonset. ~1 h wide. */
    MINOR,
}

/**
 * A feeding/activity window in local wall-clock time.
 *
 * [start] and [end] are the boundaries; [center] is the transit/rise/set instant the
 * window is built around. All three are in the observer's zone. A window may legitimately
 * start on the previous local day or end on the next (midnight crossing) — the engine
 * clamps the *reported* window to the requested day where the spec calls for it, but the
 * raw instants are preserved here so callers can render "started 11:40 PM yesterday".
 */
data class SolunarWindow(
    val type: WindowType,
    val start: LocalDateTime,
    val center: LocalDateTime,
    val end: LocalDateTime,
)

/** A rise/set/transit event time, or null when the event does not occur on the given day. */
data class RiseSetTimes(
    val rise: LocalDateTime?,
    val set: LocalDateTime?,
    /** Upper transit (object due south/north, highest). Center of a MAJOR window for the moon. */
    val upperTransit: LocalDateTime?,
    /** Lower transit / anti-transit (object due north/south, lowest). Center of the other MAJOR. */
    val lowerTransit: LocalDateTime?,
)

/** Moon phase description. */
data class MoonPhaseInfo(
    /** Illuminated fraction of the disk, 0.0–1.0. */
    val illuminatedFraction: Double,
    /** 0–360°: 0 = new, 90 = first quarter, 180 = full, 270 = last quarter. */
    val phaseAngleDegrees: Double,
    val name: PhaseName,
    /** true while the fraction is increasing (new -> full). */
    val waxing: Boolean,
) {
    /** Illuminated percentage, rounded, for display. */
    val illuminatedPercent: Int get() = Math.round(illuminatedFraction * 100.0).toInt()
}

enum class PhaseName {
    NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
    FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT,
}

/** Day activity rating (spec §4.I). */
data class DayRating(
    /** 0–100 continuous score. */
    val score: Int,
    /** 0–4 stars derived from [score]. */
    val stars: Int,
)

/**
 * The complete solunar picture for one local day at one location — the single immutable
 * object the Android layer renders. Everything here is derived deterministically from
 * (date, observer); no clock reads, no I/O.
 */
data class SolunarDay(
    val date: LocalDate,
    val observer: Observer,
    /** Two MAJOR + up-to-two MINOR windows, ordered by start time within the local day. */
    val windows: List<SolunarWindow>,
    val sun: RiseSetTimes,
    val moon: RiseSetTimes,
    val moonPhase: MoonPhaseInfo,
    val rating: DayRating,
) {
    val majorWindows: List<SolunarWindow> get() = windows.filter { it.type == WindowType.MAJOR }
    val minorWindows: List<SolunarWindow> get() = windows.filter { it.type == WindowType.MINOR }
}
