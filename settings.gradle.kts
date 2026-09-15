plugins {
    // Lets Gradle download the Java 25 toolchain automatically when it is not installed.
    // The version is literal, not a catalog alias: the catalog is declared by this
    // very file, so `libs` does not exist yet at this point.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "vksiv-apps"

include(":backend", ":frontend")

dependencyResolutionManagement {
    // Deliberately NOT PREFER_SETTINGS: the Node plugin registers its own
    // repository to download the pinned Node distribution, and PREFER_SETTINGS
    // silently ignores it, which breaks the frontend build.
    repositories {
        mavenCentral()
    }
}
