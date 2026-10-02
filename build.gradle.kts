// Pure Kotlin/JVM. NO Android Gradle Plugin, NO android{} block, NO Android deps.
// Kotlin 2.2.20 + Gradle 9.6.1.
plugins {
    kotlin("jvm") version "2.2.20"
}

// java.time.* used throughout (JDK 17). No third-party runtime dependencies at all.
dependencies {
    testImplementation(kotlin("test"))
}

java {
    // minSdk 26 (Android 8.0) desugars java.time; on plain JVM we simply target 17.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
