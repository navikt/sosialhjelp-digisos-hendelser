import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.sosialhjelp"

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.spotless)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_21
    }
}

application {
    mainClass.set("no.nav.sosialhjelp.fiks.ApplicationKt")
}

val githubUser: String? by project
val githubPassword: String? by project

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/navikt/*")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.jackson)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.lettuce.core)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.sosialhjelp.filformat)
    implementation(libs.sosialhjelp.common.api)

    testImplementation(libs.bundles.ktor.test)
    testImplementation(libs.mockk)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events = setOf(TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
}

ktor {
    fatJar {
        archiveFileName.set("app.jar")
    }
}

spotless {
    format("misc") {
        target("*.md", ".gitignore", "Dockerfile")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
