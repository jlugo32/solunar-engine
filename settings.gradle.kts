// Standalone Kotlin/JVM module for the solunar engine.
// This is the auditable, dependency-free, framework-free astronomy core.
// It has zero Android imports, so the same package tree (com.solunar.core.solunar)
// drops into an Android app unchanged.
//
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "solunar-engine"
