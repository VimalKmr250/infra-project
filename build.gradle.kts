// Root build: declares plugins for subprojects without applying them here.
plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.node.gradle) apply false
}

tasks.register("printVersions") {
    group = "help"
    description = "Prints the pinned toolchain versions."
    val versions = mapOf(
        "Java" to libs.versions.java.get(),
        "Node" to libs.versions.node.get(),
        "Spring Boot" to libs.versions.springBoot.get(),
        "Gradle" to gradle.gradleVersion,
    )
    doLast { versions.forEach { (k, v) -> println("%-12s %s".format(k, v)) } }
}
