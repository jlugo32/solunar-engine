# solunar-engine

A small Kotlin/JVM library that works out solunar tables: the daily feeding and activity
windows that hunters and anglers use. It has no dependencies and no I/O. It does not read the system clock.

It is the astronomy core of [BiteWindow](https://bitewindow.brandistries.com), an Android
fishing and hunting app. It has no Android imports, so the same code runs on the JVM and on Android.

## What it computes

For one date and one observer, `SolunarEngine.computeDay(date, observer)` returns:

- **Major windows**: moon transit (overhead and underfoot) ± 1 h
- **Minor windows**: moonrise and moonset ± 30 min
- Sunrise, sunset, moonrise, moonset and moon transit, as local wall-clock times
- Moon phase and illumination
- A 0–100 day rating

```kotlin
val day = SolunarEngine.computeDay(
    LocalDate.of(2026, 6, 15),
    Observer(latitude = 39.95, longitude = -75.17, zone = ZoneId.of("America/New_York")),
)
day.windows.forEach { println("${it.type} ${it.start} – ${it.end}") }
```

## Accuracy

The algorithms follow Meeus, *Astronomical Algorithms* (2nd ed.): Julian dates and ΔT,
sidereal time, and sun and moon positions. To find rise and set times, the engine samples altitude
across the local day, looks for sign changes, and bisects each crossing to the second. This
also handles events that cross midnight, days with only one event, and days when the moon never rises or never sets.

The tests check moon rise, set and transit against reference values from the
**U.S. Naval Observatory** API to within **±2 minutes**. The test cases deliberately include:

- a Southern Hemisphere location, to catch latitude sign errors
- US daylight-saving spring-forward and fall-back dates, to catch local-time conversion errors

## Build and test

Needs JDK 17.

```bash
./gradlew test      # 43 tests
```

## License

MIT. See [LICENSE](LICENSE).
