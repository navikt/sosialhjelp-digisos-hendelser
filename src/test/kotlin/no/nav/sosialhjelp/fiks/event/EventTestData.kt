package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonAvsender
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonForvaltningsbrev
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonDokumentlagerFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonSvarUtFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumentasjonEtterspurt
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumentasjonkrav
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumenter
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonForelopigSvar
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSaksStatus
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSoknadsStatus
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonTildeltNavKontor
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonUtbetaling
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVedtakFattet
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVedtaksfil
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVilkar
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

const val DOKUMENTLAGERID_1 = "1"
const val DOKUMENTLAGERID_2 = "2"
const val SVARUTID = "42"
const val SVAR_UT_NR = 42

const val NAVKONTOR = "1337"
const val NAVKONTOR2 = "2244"

const val TITTEL_1 = "tittelen din"
const val TITTEL_2 = "tittel2"

const val REFERANSE_1 = "sak1"
const val REFERANSE_2 = "sak2"

const val UTBETALING_REF_1 = "utbetaling 1"

const val VILKAR_REF_1 = "ulike vilkar"

const val DOKUMENTASJONKRAV_REF_1 = "dette må du gjøre for å få pengene"

const val DOKUMENTTYPE = "dokumentasjonstype"
const val TILLEGGSINFO = "ekstra info"

val avsender: JsonAvsender = JsonAvsender().withSystemnavn("test").withSystemversjon("123")

private val now = ZonedDateTime.now()
val tidspunkt_soknad: Long = now.minusHours(11).toEpochSecond() * 1000L
val tidspunkt_soknad_fixed_localDateTime: LocalDateTime = LocalDateTime.of(2020, 10, 10, 10, 10, 10, 0)
var zone: ZoneId = ZoneId.of("Europe/Berlin")
val tidspunkt_soknad_fixed: Long = tidspunkt_soknad_fixed_localDateTime.atZone(zone).toEpochSecond() * 1000L
val tidspunkt_1: String = now.minusHours(10).format(DateTimeFormatter.ISO_DATE_TIME)
val tidspunkt_2: String = now.minusHours(9).format(DateTimeFormatter.ISO_DATE_TIME)
val tidspunkt_3: String = now.minusHours(8).format(DateTimeFormatter.ISO_DATE_TIME)
val tidspunkt_3_1: String = now.minusHours(8).format(DateTimeFormatter.ISO_DATE_TIME)
val tidspunkt_4: String = now.minusHours(7).format(DateTimeFormatter.ISO_DATE_TIME)
val tidspunkt_5: String = now.minusHours(6).format(DateTimeFormatter.ISO_DATE_TIME)
val tidspunkt_6: String = now.minusHours(5).format(DateTimeFormatter.ISO_DATE_TIME)
val innsendelsesfrist: String = now.plusDays(7).format(DateTimeFormatter.ISO_DATE_TIME)

val DOKUMENTLAGER_1: JsonDokumentlagerFilreferanse =
    JsonDokumentlagerFilreferanse()
        .withType(
            JsonFilreferanse.Type.DOKUMENTLAGER,
        ).withId(DOKUMENTLAGERID_1)
val DOKUMENTLAGER_2: JsonDokumentlagerFilreferanse =
    JsonDokumentlagerFilreferanse()
        .withType(
            JsonFilreferanse.Type.DOKUMENTLAGER,
        ).withId(DOKUMENTLAGERID_2)
val SVARUT_1: JsonSvarUtFilreferanse =
    JsonSvarUtFilreferanse()
        .withType(
            JsonFilreferanse.Type.DOKUMENTLAGER,
        ).withId(SVARUTID)
        .withNr(SVAR_UT_NR)

val SOKNADS_STATUS_MOTTATT: JsonSoknadsStatus =
    JsonSoknadsStatus()
        .withType(JsonHendelse.Type.SOKNADS_STATUS)
        .withStatus(JsonSoknadsStatus.Status.MOTTATT)

val SOKNADS_STATUS_UNDERBEHANDLING: JsonSoknadsStatus =
    JsonSoknadsStatus()
        .withType(JsonHendelse.Type.SOKNADS_STATUS)
        .withStatus(JsonSoknadsStatus.Status.UNDER_BEHANDLING)

val SOKNADS_STATUS_FERDIGBEHANDLET: JsonSoknadsStatus =
    JsonSoknadsStatus()
        .withType(JsonHendelse.Type.SOKNADS_STATUS)
        .withStatus(JsonSoknadsStatus.Status.FERDIGBEHANDLET)

val SOKNADS_STATUS_BEHANDLES_IKKE: JsonSoknadsStatus =
    JsonSoknadsStatus()
        .withType(JsonHendelse.Type.SOKNADS_STATUS)
        .withStatus(JsonSoknadsStatus.Status.BEHANDLES_IKKE)

val TILDELT_NAV_KONTOR: JsonTildeltNavKontor =
    JsonTildeltNavKontor()
        .withType(JsonHendelse.Type.TILDELT_NAV_KONTOR)
        .withNavKontor(NAVKONTOR)

val TILDELT_NAV_KONTOR_2: JsonTildeltNavKontor =
    JsonTildeltNavKontor()
        .withType(JsonHendelse.Type.TILDELT_NAV_KONTOR)
        .withNavKontor(NAVKONTOR2)

val SAK1_SAKS_STATUS_UNDERBEHANDLING: JsonSaksStatus =
    JsonSaksStatus()
        .withType(JsonHendelse.Type.SAKS_STATUS)
        .withStatus(JsonSaksStatus.Status.UNDER_BEHANDLING)
        .withTittel(TITTEL_1)
        .withReferanse(REFERANSE_1)

val SAK1_VEDTAK: JsonVedtakFattet =
    JsonVedtakFattet()
        .withType(JsonHendelse.Type.VEDTAK_FATTET)
        .withSaksreferanse(REFERANSE_1)
        .withVedtaksfil(
            JsonVedtaksfil().withReferanse(
                JsonDokumentlagerFilreferanse()
                    .withType(JsonFilreferanse.Type.DOKUMENTLAGER)
                    .withId("0be7aa9c-5de0-450e-b8dc-75720c8053ae"),
            ),
        ).withUtfall(JsonVedtakFattet.Utfall.INNVILGET)

val SAK1_UTEN_SAKS_STATUS_ELLER_TITTEL: JsonSaksStatus =
    JsonSaksStatus()
        .withType(JsonHendelse.Type.SAKS_STATUS)
        .withReferanse(REFERANSE_1)

val SAK1_SAKS_STATUS_IKKEINNSYN: JsonSaksStatus =
    JsonSaksStatus()
        .withType(JsonHendelse.Type.SAKS_STATUS)
        .withStatus(JsonSaksStatus.Status.IKKE_INNSYN)
        .withTittel(TITTEL_1)
        .withReferanse(REFERANSE_1)

val SAK2_SAKS_STATUS_UNDERBEHANDLING: JsonSaksStatus =
    JsonSaksStatus()
        .withType(JsonHendelse.Type.SAKS_STATUS)
        .withStatus(JsonSaksStatus.Status.UNDER_BEHANDLING)
        .withTittel(TITTEL_2)
        .withReferanse(REFERANSE_2)

val SAK1_VEDTAK_FATTET_INNVILGET: JsonVedtakFattet =
    JsonVedtakFattet()
        .withType(JsonHendelse.Type.VEDTAK_FATTET)
        .withSaksreferanse(REFERANSE_1)
        .withVedtaksfil(JsonVedtaksfil().withReferanse(DOKUMENTLAGER_1))
        .withUtfall(JsonVedtakFattet.Utfall.INNVILGET)

val SAK1_VEDTAK_FATTET_UTEN_UTFALL: JsonVedtakFattet =
    JsonVedtakFattet()
        .withType(JsonHendelse.Type.VEDTAK_FATTET)
        .withSaksreferanse(REFERANSE_1)
        .withVedtaksfil(JsonVedtaksfil().withReferanse(DOKUMENTLAGER_1))

val SAK1_VEDTAK_FATTET_AVSLATT: JsonVedtakFattet =
    JsonVedtakFattet()
        .withType(JsonHendelse.Type.VEDTAK_FATTET)
        .withSaksreferanse(REFERANSE_1)
        .withVedtaksfil(JsonVedtaksfil().withReferanse(DOKUMENTLAGER_2))
        .withUtfall(JsonVedtakFattet.Utfall.AVSLATT)

val SAK2_VEDTAK_FATTET: JsonVedtakFattet =
    JsonVedtakFattet()
        .withType(JsonHendelse.Type.VEDTAK_FATTET)
        .withSaksreferanse(REFERANSE_2)
        .withVedtaksfil(JsonVedtaksfil().withReferanse(SVARUT_1))
        .withUtfall(JsonVedtakFattet.Utfall.INNVILGET)

val DOKUMENTASJONETTERSPURT: JsonDokumentasjonEtterspurt =
    JsonDokumentasjonEtterspurt()
        .withType(JsonHendelse.Type.DOKUMENTASJON_ETTERSPURT)
        .withDokumenter(
            mutableListOf(
                JsonDokumenter()
                    .withInnsendelsesfrist(
                        innsendelsesfrist,
                    ).withDokumenttype(DOKUMENTTYPE)
                    .withTilleggsinformasjon(TILLEGGSINFO),
            ),
        ).withForvaltningsbrev(JsonForvaltningsbrev().withReferanse(DOKUMENTLAGER_1))

val DOKUMENTASJONETTERSPURT_TOM_DOKUMENT_LISTE: JsonDokumentasjonEtterspurt =
    JsonDokumentasjonEtterspurt()
        .withType(JsonHendelse.Type.DOKUMENTASJON_ETTERSPURT)
        .withForvaltningsbrev(JsonForvaltningsbrev().withReferanse(DOKUMENTLAGER_1))

val DOKUMENTASJONETTERSPURT_UTEN_FORVALTNINGSBREV: JsonDokumentasjonEtterspurt =
    JsonDokumentasjonEtterspurt()
        .withType(JsonHendelse.Type.DOKUMENTASJON_ETTERSPURT)
        .withDokumenter(
            mutableListOf(
                JsonDokumenter()
                    .withInnsendelsesfrist(
                        innsendelsesfrist,
                    ).withDokumenttype(DOKUMENTTYPE)
                    .withTilleggsinformasjon(TILLEGGSINFO),
            ),
        )

val FORELOPIGSVAR: JsonForelopigSvar =
    JsonForelopigSvar()
        .withType(JsonHendelse.Type.FORELOPIG_SVAR)
        .withForvaltningsbrev(JsonForvaltningsbrev().withReferanse(SVARUT_1))

val UTBETALING: JsonUtbetaling =
    JsonUtbetaling()
        .withType(JsonHendelse.Type.UTBETALING)
        .withUtbetalingsreferanse(UTBETALING_REF_1)
        .withSaksreferanse(REFERANSE_1)
        .withRammevedtaksreferanse(null)
        .withStatus(JsonUtbetaling.Status.UTBETALT)
        .withBelop(1234.56)
        .withBeskrivelse(TITTEL_1)
        .withForfallsdato("2019-12-31")
        .withUtbetalingsdato("2019-12-24")
        .withFom("2019-12-01")
        .withTom("2019-12-31")
        .withAnnenMottaker(false)
        .withMottaker("fnr")
        .withKontonummer(null)
        .withUtbetalingsmetode("pose med krølla femtilapper")

val UTBETALING_BANKOVERFORING: JsonUtbetaling =
    JsonUtbetaling()
        .withType(JsonHendelse.Type.UTBETALING)
        .withUtbetalingsreferanse(UTBETALING_REF_1)
        .withSaksreferanse(REFERANSE_1)
        .withRammevedtaksreferanse(null)
        .withStatus(JsonUtbetaling.Status.UTBETALT)
        .withBelop(1234.56)
        .withBeskrivelse(TITTEL_1)
        .withForfallsdato("2019-12-31")
        .withUtbetalingsdato("2019-12-24")
        .withFom("2019-12-01")
        .withTom("2019-12-31")
        .withAnnenMottaker(false)
        .withMottaker("fnr")
        .withKontonummer("kontonummer")
        .withUtbetalingsmetode("bankoverføring")

val UTBETALING_BANKOVERFORING_ANNEN_MOTTAKER: JsonUtbetaling =
    JsonUtbetaling()
        .withType(JsonHendelse.Type.UTBETALING)
        .withUtbetalingsreferanse(UTBETALING_REF_1)
        .withSaksreferanse(REFERANSE_1)
        .withRammevedtaksreferanse(null)
        .withStatus(JsonUtbetaling.Status.UTBETALT)
        .withBelop(1234.56)
        .withBeskrivelse(TITTEL_1)
        .withForfallsdato("2019-12-31")
        .withUtbetalingsdato("2019-12-24")
        .withFom(null)
        .withTom(null)
        .withAnnenMottaker(true)
        .withMottaker("utleier")
        .withKontonummer("utleierKontonummer")
        .withUtbetalingsmetode("bankoverføring")

val VILKAR_OPPFYLT: JsonVilkar =
    JsonVilkar()
        .withType(JsonHendelse.Type.VILKAR)
        .withVilkarreferanse(VILKAR_REF_1)
        .withUtbetalingsreferanse(listOf(UTBETALING_REF_1))
        .withBeskrivelse("beskrivelse")
        .withStatus(JsonVilkar.Status.OPPFYLT)

val DOKUMENTASJONKRAV_OPPFYLT: JsonDokumentasjonkrav =
    JsonDokumentasjonkrav()
        .withType(JsonHendelse.Type.DOKUMENTASJONKRAV)
        .withDokumentasjonkravreferanse(DOKUMENTASJONKRAV_REF_1)
        .withUtbetalingsreferanse(listOf(UTBETALING_REF_1))
        .withBeskrivelse("beskrivelse")
        .withStatus(JsonDokumentasjonkrav.Status.OPPFYLT)

fun resetHendelser() {
    SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(null)
    SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(null)
    SOKNADS_STATUS_FERDIGBEHANDLET.withHendelsestidspunkt(null)
    SOKNADS_STATUS_BEHANDLES_IKKE.withHendelsestidspunkt(null)
    TILDELT_NAV_KONTOR.withHendelsestidspunkt(null)
    TILDELT_NAV_KONTOR_2.withHendelsestidspunkt(null)
    SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(null)
    SAK1_SAKS_STATUS_IKKEINNSYN.withHendelsestidspunkt(null)
    SAK2_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(null)
    SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(null)
    SAK1_VEDTAK_FATTET_AVSLATT.withHendelsestidspunkt(null)
    SAK2_VEDTAK_FATTET.withHendelsestidspunkt(null)
    DOKUMENTASJONETTERSPURT.withHendelsestidspunkt(null)
    DOKUMENTASJONETTERSPURT_UTEN_FORVALTNINGSBREV.withHendelsestidspunkt(null)
    DOKUMENTASJONETTERSPURT_TOM_DOKUMENT_LISTE.withHendelsestidspunkt(null)
    FORELOPIGSVAR.withHendelsestidspunkt(null)
    UTBETALING.withHendelsestidspunkt(null)
    UTBETALING_BANKOVERFORING.withHendelsestidspunkt(null)
    UTBETALING_BANKOVERFORING_ANNEN_MOTTAKER.withHendelsestidspunkt(null)
    DOKUMENTASJONKRAV_OPPFYLT.withHendelsestidspunkt(null)
    VILKAR_OPPFYLT.withHendelsestidspunkt(null)
}
