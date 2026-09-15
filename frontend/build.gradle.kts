import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node.gradle)
}

node {
    // Downloads and uses this exact Node build. The system Node on PATH is ignored,
    // which matters here: Angular 22 rejects odd-numbered Node releases such as 25.x.
    version = libs.versions.node
    download = true
    workDir = layout.buildDirectory.dir("nodejs")
    npmWorkDir = layout.buildDirectory.dir("npm")
    nodeProjectDir = layout.projectDirectory
    // `npm ci` is reproducible but requires a lock file, which does not exist
    // until the first `npm install` has been run.
    npmInstallCommand = if (file("package-lock.json").exists()) "ci" else "install"
}

/** Angular writes to build/angular-dist/browser (see outputPath in angular.json). */
val angularOutput: Provider<Directory> = layout.buildDirectory.dir("angular-dist")
val angularBrowserOutput: Provider<Directory> = angularOutput.map { it.dir("browser") }

val buildAngular = tasks.register<NpmTask>("buildAngular") {
    group = "build"
    description = "Produces the production Angular bundle."
    dependsOn(tasks.named("npmInstall"))

    npmCommand = listOf("run", "build")

    inputs.dir("src").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files("package.json", "package-lock.json", "angular.json", "tsconfig.json")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(angularOutput)
    outputs.cacheIf { true }
}

val lintAngular = tasks.register<NpmTask>("lintAngular") {
    group = "verification"
    description = "Runs the Angular linter."
    dependsOn(tasks.named("npmInstall"))
    npmCommand = listOf("run", "lint")
}

val testAngular = tasks.register<NpmTask>("testAngular") {
    group = "verification"
    description = "Runs Angular unit tests headlessly."
    dependsOn(tasks.named("npmInstall"))
    npmCommand = listOf("run", "test:ci")
}

tasks.named("check") { dependsOn(testAngular, lintAngular) }
tasks.named("assemble") { dependsOn(buildAngular) }

/**
 * Publishes the built bundle so :backend can consume it as a normal Gradle
 * dependency. This is deliberately an artifact rather than a cross-project task
 * reference, so the two modules stay decoupled and the wiring survives
 * parallel execution and project isolation.
 */
val angularDist = configurations.consumable("angularDist")

artifacts {
    add(angularDist.name, angularBrowserOutput) {
        builtBy(buildAngular)
    }
}
