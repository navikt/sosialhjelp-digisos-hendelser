import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

group = "no.nav.sosialhjelp"
// Overridable via `-Pversion=X.Y.Z` (e.g. from a CI release workflow) so that both the
// Maven (JVM) and npm (JS) publications always share the exact same version.
version = providers.gradleProperty("version").orNull ?: "1.0-SNAPSHOT"

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

kotlin {
    jvm()
    js {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.sosialhjelp.filformat.kmp)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.mockk)
                implementation(libs.assertj.core)
            }
        }

        jsMain {
            dependencies {
                // Required for kotlinx-datetime's TimeZone.of("Europe/Oslo") to resolve on
                // Kotlin/JS + Node: @js-joda/core (used internally by kotlinx-datetime on JS)
                // ships with no IANA timezone database by default.
                implementation(npm("@js-joda/timezone", libs.versions.js.joda.timezone.get()))
            }

        }
    }
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

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/navikt/sosialhjelp-digisos-hendelser")
            credentials {
                username = githubUser
                password = githubPassword
            }
        }
    }
}

// --- npm (GitHub Packages) publishing ---
// GitHub Packages requires npm package names to be scoped to the org, while the JS
// distribution produced by `binaries.library()` uses the unscoped `rootProject.name`.
// These tasks stage the distribution output under a scoped name + the project's version
// (kept in sync with the Maven publications above) and publish it with `npm publish`.
val npmPackageScope = "@navikt"
val npmPublishStagingDir = layout.buildDirectory.dir("npmPublish")

val preparePublishableNpmPackage by tasks.registering(Copy::class) {
    dependsOn("jsNodeProductionLibraryDistribution")
    from(layout.buildDirectory.dir("dist/js/productionLibrary"))
    into(npmPublishStagingDir)
    doLast {
        val packageJsonFile = npmPublishStagingDir.get().file("package.json").asFile
        val json = groovy.json.JsonSlurper().parse(packageJsonFile) as MutableMap<String, Any?>
        json["name"] = "$npmPackageScope/${rootProject.name}"
        json["version"] = project.version.toString()
        packageJsonFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)))
    }
}

val publishNpmToGitHubPackages by tasks.registering(Exec::class) {
    dependsOn(preparePublishableNpmPackage)
    workingDir(npmPublishStagingDir)
    commandLine("npm", "publish", "--registry=https://npm.pkg.github.com")
}

tasks.named("publish") {
    dependsOn(publishNpmToGitHubPackages)
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
