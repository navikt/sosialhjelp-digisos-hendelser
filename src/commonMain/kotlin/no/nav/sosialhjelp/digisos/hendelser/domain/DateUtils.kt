package no.nav.sosialhjelp.digisos.hendelser.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val OSLO = TimeZone.of("Europe/Oslo")

/**
 * Parse an ISO-8601 timestamp string (e.g. "2024-03-15T13:37:00.134Z") to [Instant].
 * Returns [Instant.DISTANT_PAST] on parse failure to ensure safe sort ordering.
 */
fun String.toInstant(): Instant =
    runCatching { Instant.parse(this) }.getOrElse { Instant.DISTANT_PAST }

/**
 * Parse a date string to [LocalDate] in Europe/Oslo timezone.
 * Accepts both "YYYY-MM-DD" and full ISO-8601 date-time strings.
 */
fun String.toLocalDate(): LocalDate =
    if (this.length > 10) {
        Instant.parse(this).toLocalDateTime(OSLO).date
    } else {
        LocalDate.parse(this)
    }

/** Convert a Unix epoch-millisecond timestamp to [Instant]. */
fun unixToInstant(epochMillis: Long): Instant = Instant.fromEpochMilliseconds(epochMillis)

/** Convert an [Instant] to [LocalDate] in Europe/Oslo. */
fun Instant.toLocalDateOslo(): LocalDate = toLocalDateTime(OSLO).date

/** Strip " kommune" suffix from Nav-enhet names for display. */
fun stripEnhetsnavnForKommune(navn: String?): String? = navn?.replace(" kommune", "")
