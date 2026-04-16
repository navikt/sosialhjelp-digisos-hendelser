package no.nav.sosialhjelp.fiks.utils

import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonDokumentlagerFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonSvarUtFilreferanse
import no.nav.sosialhjelp.fiks.app.ClientProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import kotlin.reflect.full.companionObject

fun hentUrlFraFilreferanse(
    clientProperties: ClientProperties,
    filreferanse: JsonFilreferanse,
): String =
    when (filreferanse) {
        is JsonDokumentlagerFilreferanse ->
            clientProperties.fiksDokumentlagerEndpointUrl +
                "/dokumentlager/nedlasting/niva4/${filreferanse.id}?inline=true"

        is JsonSvarUtFilreferanse ->
            clientProperties.fiksSvarUtEndpointUrl +
                "/forsendelse/${filreferanse.id}/${filreferanse.nr}?inline=true"

        else -> throw RuntimeException(
            "Noe uventet feilet. JsonFilreferanse på annet format enn JsonDokumentlagerFilreferanse og JsonSvarUtFilreferanse",
        )
    }

fun hentDokumentlagerUrl(
    clientProperties: ClientProperties,
    dokumentlagerId: String,
): String = clientProperties.fiksDokumentlagerEndpointUrl + "/dokumentlager/nedlasting/niva4/$dokumentlagerId?inline=true"

fun String.toLocalDateTime(): LocalDateTime =
    ZonedDateTime
        .parse(this, ISO_DATE_TIME)
        .withZoneSameInstant(ZoneId.of("Europe/Oslo"))
        .toLocalDateTime()

fun String.toLocalDate(): LocalDate = LocalDate.parse(this, ISO_LOCAL_DATE)

fun unixToLocalDateTime(tidspunkt: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(tidspunkt), ZoneId.of("Europe/Oslo"))

fun <R : Any> R.logger(): Lazy<Logger> = lazy { LoggerFactory.getLogger(unwrapCompanionClass(this.javaClass).name) }

fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> =
    ofClass.enclosingClass?.takeIf {
        ofClass.enclosingClass.kotlin.companionObject
            ?.java == ofClass
    } ?: ofClass
