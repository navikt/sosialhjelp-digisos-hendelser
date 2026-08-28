package no.nav.sosialhjelp.fiks.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import kotlin.reflect.full.companionObject

/**
 * Parse an ISO-8601 date-time string to [Instant].
 * Used in the fold engine — comparing as Instant avoids the raw-string-comparison footgun
 * that existed when timestamps were sorted lexicographically as strings.
 */
fun String.toInstant(): Instant =
    ZonedDateTime
        .parse(this, ISO_DATE_TIME)
        .toInstant()

/**
 * Parse a date string to [LocalDate].
 * Accepts both ISO date format ("YYYY-MM-DD") and ISO date-time format
 * ("YYYY-MM-DDThh:mm:ss+offset[zone]"), extracting only the date part.
 */
fun String.toLocalDate(): LocalDate =
    if (this.length > 10) {
        // ISO date-time — parse full and extract local date in Europe/Oslo
        ZonedDateTime
            .parse(this, ISO_DATE_TIME)
            .withZoneSameInstant(ZoneId.of("Europe/Oslo"))
            .toLocalDate()
    } else {
        LocalDate.parse(this, ISO_LOCAL_DATE)
    }

fun unixToInstant(tidspunkt: Long): Instant = Instant.ofEpochMilli(tidspunkt)

/** @deprecated Use [toInstant] instead to avoid timezone loss. */
fun String.toLocalDateTime(): java.time.LocalDateTime =
    ZonedDateTime
        .parse(this, ISO_DATE_TIME)
        .withZoneSameInstant(ZoneId.of("Europe/Oslo"))
        .toLocalDateTime()

/** @deprecated Use [unixToInstant] instead to avoid timezone loss. */
fun unixToLocalDateTime(tidspunkt: Long): java.time.LocalDateTime =
    java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(tidspunkt), ZoneId.of("Europe/Oslo"))

fun <R : Any> R.logger(): Lazy<Logger> = lazy { LoggerFactory.getLogger(unwrapCompanionClass(this.javaClass).name) }

fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> =
    ofClass.enclosingClass?.takeIf {
        ofClass.enclosingClass.kotlin.companionObject
            ?.java == ofClass
    } ?: ofClass

/** Mask 11-digit Norwegian national IDs in error messages before logging. */
val String.maskerFnr: String get() = this.replace(Regex("""\b[0-9]{11}\b"""), "[FNR]")
