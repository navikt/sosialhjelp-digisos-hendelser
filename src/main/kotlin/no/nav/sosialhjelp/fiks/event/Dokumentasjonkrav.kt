package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumentasjonkrav
import no.nav.sbl.soknadsosialhjelp.vedlegg.JsonVedlegg
import no.nav.sosialhjelp.fiks.domain.Dokumentasjonkrav
import no.nav.sosialhjelp.fiks.domain.Hendelse
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.HistorikkType
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.utils.sha256
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(JsonDokumentasjonkrav::class.java.name)

fun InternalDigisosSoker.apply(hendelse: JsonDokumentasjonkrav) {
    val dokumentasjonkrav =
        Dokumentasjonkrav(
            dokumentasjonkravId =
                sha256(
                    hendelse.frist
                        ?.toLocalDateTime()
                        ?.toLocalDate()
                        .toString(),
                ),
            hendelsetype = JsonVedlegg.HendelseType.DOKUMENTASJONKRAV,
            referanse = hendelse.dokumentasjonkravreferanse,
            tittel = hendelse.tittel,
            beskrivelse = hendelse.beskrivelse,
            status = Oppgavestatus.valueOf(hendelse.status.value()),
            datoLagtTil = hendelse.hendelsestidspunkt.toLocalDateTime(),
            frist = hendelse.frist?.toLocalDateTime()?.toLocalDate(),
            utbetalingsReferanse = hendelse.utbetalingsreferanse,
            saksreferanse = hendelse.saksreferanse,
        )

    this.dokumentasjonkrav.oppdaterEllerLeggTilDokumentasjonkrav(dokumentasjonkrav)

    val utbetalingerMedSakKnytning = saker.flatMap { it.utbetalinger }.filter { it.referanse in hendelse.utbetalingsreferanse }
    val utbetalingerUtenSakKnytning = utbetalinger.filter { it.referanse in hendelse.utbetalingsreferanse }

    if (utbetalingerMedSakKnytning.isEmpty() && utbetalingerUtenSakKnytning.isEmpty()) {
        log.debug("Fant ingen utbetalinger å knytte dokumentasjonkrav til. Utbetalingsreferanser: {}", hendelse.utbetalingsreferanse)
        return
    }

    val union = utbetalingerMedSakKnytning.union(utbetalingerUtenSakKnytning)
    union.forEach { it.dokumentasjonkrav.oppdaterEllerLeggTilDokumentasjonkrav(dokumentasjonkrav) }

    log.info(
        "Hendelse: Tidspunkt: ${hendelse.hendelsestidspunkt} Dokumentasjonskrav." +
            " Beskrivelse: Dine oppgaver er oppdatert, les mer i vedtaket.",
    )
    historikk.add(
        Hendelse(
            HendelseTekstType.DOKUMENTASJONKRAV,
            hendelse.hendelsestidspunkt.toLocalDateTime(),
            type = HistorikkType.DOKUMENTASJONSKRAV,
        ),
    )
}

private fun MutableList<Dokumentasjonkrav>.oppdaterEllerLeggTilDokumentasjonkrav(dokumentasjonkrav: Dokumentasjonkrav) {
    if (any { it.referanse == dokumentasjonkrav.referanse }) {
        filter { it.referanse == dokumentasjonkrav.referanse }
            .forEach { it.oppdaterFelter(dokumentasjonkrav) }
    } else {
        this.add(dokumentasjonkrav)
    }
}

private fun Dokumentasjonkrav.oppdaterFelter(dokumentasjonkrav: Dokumentasjonkrav) {
    this.tittel = dokumentasjonkrav.tittel
    this.beskrivelse = dokumentasjonkrav.beskrivelse
    this.status = dokumentasjonkrav.status
    this.utbetalingsReferanse = dokumentasjonkrav.utbetalingsReferanse
    this.frist = dokumentasjonkrav.frist
    this.saksreferanse = dokumentasjonkrav.saksreferanse
}
