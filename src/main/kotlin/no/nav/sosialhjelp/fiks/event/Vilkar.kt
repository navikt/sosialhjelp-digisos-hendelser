package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVilkar
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.domain.Utbetaling
import no.nav.sosialhjelp.fiks.domain.Vilkar
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(JsonVilkar::class.java.name)

fun InternalDigisosSoker.apply(hendelse: JsonVilkar) {
    log.info("Hendelse: Tidspunkt: ${hendelse.hendelsestidspunkt} Vilkar. Status: ${hendelse.status?.name ?: "null"}")
    val vilkar =
        Vilkar(
            referanse = hendelse.vilkarreferanse,
            tittel = hendelse.tittel,
            beskrivelse = hendelse.beskrivelse,
            status = Oppgavestatus.valueOf(hendelse.status.value()),
            datoLagtTil = hendelse.hendelsestidspunkt.toLocalDateTime(),
            datoSistEndret = hendelse.hendelsestidspunkt.toLocalDateTime(),
            utbetalingsReferanse = hendelse.utbetalingsreferanse,
            saksReferanse = hendelse.saksreferanse,
        )

    this.vilkar.oppdaterEllerLeggTilVilkar(hendelse, vilkar)

    val utbetalinger = finnAlleUtbetalingerSomVilkarRefererTil(hendelse)

    fjernFraUtbetalingerSomIkkeLengreErReferertTilIVilkaret(hendelse)

    if (utbetalinger.isEmpty()) {
        log.debug("Fant ingen utbetalinger å knytte vilkår til. Utbetalingsreferanser: {}", hendelse.utbetalingsreferanse)
        return
    }
    utbetalinger.forEach { it.vilkar.oppdaterEllerLeggTilVilkar(hendelse, vilkar) }
}

private fun InternalDigisosSoker.finnAlleUtbetalingerSomVilkarRefererTil(hendelse: JsonVilkar): MutableList<Utbetaling> {
    val utbetalinger = mutableListOf<Utbetaling>()
    for (utbetalingsreferanse in hendelse.utbetalingsreferanse) {
        for (sak in saker) {
            for (utbetaling in sak.utbetalinger) {
                if (utbetaling.referanse == utbetalingsreferanse) {
                    utbetalinger.add(utbetaling)
                }
            }
        }
    }
    return utbetalinger
}

private fun InternalDigisosSoker.fjernFraUtbetalingerSomIkkeLengreErReferertTilIVilkaret(hendelse: JsonVilkar) {
    for (sak in saker) {
        for (utbetaling in sak.utbetalinger) {
            utbetaling.vilkar.removeAll {
                it.referanse == hendelse.vilkarreferanse &&
                    !hendelse.utbetalingsreferanse.contains(utbetaling.referanse)
            }
        }
    }
}

private fun MutableList<Vilkar>.oppdaterEllerLeggTilVilkar(
    hendelse: JsonVilkar,
    vilkar: Vilkar,
) {
    if (any { it.referanse == hendelse.vilkarreferanse }) {
        filter { it.referanse == hendelse.vilkarreferanse }
            .forEach { it.oppdaterFelter(hendelse) }
    } else {
        this.add(vilkar)
    }
}

private fun Vilkar.oppdaterFelter(hendelse: JsonVilkar) {
    datoSistEndret = hendelse.hendelsestidspunkt.toLocalDateTime()
    beskrivelse = hendelse.beskrivelse
    tittel = hendelse.tittel
    status = Oppgavestatus.valueOf(hendelse.status.value())
    utbetalingsReferanse = hendelse.utbetalingsreferanse
    saksReferanse = hendelse.saksreferanse
}
