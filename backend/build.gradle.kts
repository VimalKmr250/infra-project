plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.vksiv.personal"
version = providers.gradleProperty("appVersion").getOrElse("0.0.1-SNAPSHOT")

java {
    toolchain {
        // Gradle downloads this JDK automatically (foojay resolver, see settings.gradle.kts).
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    }
}

dependencies {
    // starter-web is deprecated in Spring Boot 4; starter-webmvc is the replacement
    // and already brings in Jackson and Tomcat.
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Boot 4 modularised auto-configuration: LiquibaseAutoConfiguration lives here,
    // NOT in spring-boot-autoconfigure. Depending on org.liquibase:liquibase-core
    // alone compiles fine and then silently never runs a migration.
    implementation("org.springframework.boot:spring-boot-liquibase")

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    runtimeOnly("org.postgresql:postgresql")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Spring Boot 4 split the test starters. This one brings spring-boot-starter-test
    // plus TestRestTemplate (spring-boot-resttestclient) and MockMvc support.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // TestRestTemplate needs RestTemplateBuilder, which Boot 4 moved into this
    // module and no longer pulls in transitively.
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

/**
 * The Angular bundle is consumed as a Gradle artifact from :frontend and packed
 * into the jar under BOOT-INF/classes/static, which Spring Boot serves as static
 * content. META-INF/resources looks like the tidier home for generated assets, but
 * bootJar hoists everything under META-INF to the jar root, which is NOT on the
 * fat jar classpath - the files ship and are then never served. Hand-written
 * static files belong in src/main/resources/public to keep the two sets apart.
 *
 * Pass -PskipFrontend to skip this entirely. Use it for the fast local loop,
 * where `ng serve` renders the UI and Gradle only needs to run the API.
 */
val skipFrontend = providers.gradleProperty("skipFrontend").isPresent

if (!skipFrontend) {
    // Gradle's role-based configurations split these two jobs: dependencies are
    // declared on a dependencyScope configuration, and a resolvable one extending
    // it is what actually gets resolved into files.
    val frontendAssetsDeps = configurations.dependencyScope("frontendAssetsDeps")
    val frontendAssets = configurations.resolvable("frontendAssets") {
        extendsFrom(frontendAssetsDeps.get())
    }.get()

    dependencies {
        add(
            frontendAssetsDeps.name,
            project(mapOf("path" to ":frontend", "configuration" to "angularDist")),
        )
    }

    tasks.processResources {
        from(frontendAssets) {
            into("static")
        }
    }
}

springBoot {
    // Exposes build metadata at /actuator/info, so a running instance can state
    // exactly which version it is - useful when verifying a QA or prod deploy.
    buildInfo()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Local runs default to the `local` profile unless overridden.
    systemProperty("spring.profiles.active", providers.systemProperty("spring.profiles.active").getOrElse("local"))
}
