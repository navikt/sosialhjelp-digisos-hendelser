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
 * Used in the fold engine — comparing as Instant avoids the raw-string-comparison footgun.
 */
fun String.toInstant(): Instant =
    ZonedDateTime
        .parse(this, ISO_DATE_TIME)
        .toInstant()

/**
 * Parse a date string to [LocalDate].
 * Accepts both ISO date format ("YYYY-MM-DD") and ISO date-time format
 * ("YYYY-MM-DDThh:mm:ss+offset[zone]"), extracting the local date in Europe/Oslo.
 */
fun String.toLocalDate(): LocalDate =
    if (this.length > 10) {
        ZonedDateTime.parse(this, ISO_DATE_TIME)
            .withZoneSameInstant(ZoneId.of("Europe/Oslo"))
            .toLocalDate()
    } else {
        LocalDate.parse(this, ISO_LOCAL_DATE)
    }

fun unixToInstant(tidspunkt: Long): Instant = Instant.ofEpochMilli(tidspunkt)

/** Mask 11-digit Norwegian national IDs in error messages before logging or wrapping. */
val String.maskerFnr: String get() = this.replace(Regex("""\b[0-9]{11}\b"""), "[FNR]")

fun <R : Any> R.logger(): Lazy<Logger> = lazy { LoggerFactory.getLogger(unwrapCompanionClass(this.javaClass).name) }

fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> =
    ofClass.enclosingClass?.takeIf {
        ofClass.enclosingClass.kotlin.companionObject?.java == ofClass
    } ?: ofClass
