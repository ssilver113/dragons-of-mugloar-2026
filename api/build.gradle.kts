plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.mugloar"
version = "0.0.1-SNAPSHOT"
description = "Dragons of Mugloar — game API adapter, ad scoring and solver"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * Plays N games headlessly through the solver and prints the score distribution. Hits the live
 * Mugloar API, paced — see bench.* in application-bench.yaml.
 *
 *   ./gradlew bench -Pgames=40
 */
tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("bench") {
    group = "verification"
    description = "Plays games headlessly and reports the score distribution."
    mainClass = "com.mugloar.dragons.DragonsApiApplication"
    classpath = sourceSets["main"].runtimeClasspath
    args("--spring.profiles.active=bench")
    if (project.hasProperty("games")) {
        args("--bench.games=${project.property("games")}")
    }
    if (project.hasProperty("interval")) {
        args("--bench.request-interval=${project.property("interval")}")
    }
    // Anything else the run wants to override, e.g. -Pargs="--bench.strategy.target-level-per-turn=0.1"
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" ").filter { it.isNotBlank() })
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Coverage is reported, never gated.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
