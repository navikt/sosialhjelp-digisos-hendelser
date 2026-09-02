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
// Kotlin/JS has no first-class npm publishing support, so this is handled directly in
// the release GitHub Actions workflow (.github/workflows/publish_release.yml) via
// `npm pkg set` + `npm publish` against the `jsNodeProductionLibraryDistribution` output,
// using the same version passed to this build (`-Pversion=`) to stay in sync with Maven.

// Kotlin/JS exports nothing unless it is annotated `@JsExport`, and dropping those annotations
// fails silently: the build still succeeds and simply publishes an npm package with an empty
// `.d.ts` and a bundle with no exports. That shipped once already. Kotlin tests in `jsTest` do not
// catch it, since Kotlin can call non-exported declarations perfectly well — so assert on the
// generated TypeScript declarations instead.
val verifyJsExports by tasks.registering {
    val dts =
        layout.buildDirectory
            .file("dist/js/productionLibrary/${rootProject.name}.d.ts")
    dependsOn(tasks.named("jsNodeProductionLibraryDistribution"))
    inputs.file(dts)

    doLast {
        val file = dts.get().asFile
        val text = file.readText()
        val required =
            listOf(
                "function foldJson(",
                "class SoknadMetadata",
                "static create(",
                "class Soknad {",
                "class FoldResult",
                "interface SoknadHendelse",
                "abstract class SoknadsStatus",
            )
        val missing = required.filterNot { it in text }
        check(missing.isEmpty()) {
            "Generated TypeScript declarations are missing: $missing\n" +
                "Most likely an @JsExport annotation was dropped. See ${file.absolutePath}"
        }
    }
}

tasks.named("check") { dependsOn(verifyJsExports) }

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
