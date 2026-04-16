package no.nav.sosialhjelp.fiks.event

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonTildeltNavKontor
import no.nav.sosialhjelp.fiks.domain.Hendelse
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.HistorikkType
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.Soknadsmottaker
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

private val log = LoggerFactory.getLogger(JsonTildeltNavKontor::class.java.name)

suspend fun InternalDigisosSoker.apply(
    hendelse: JsonTildeltNavKontor,
    norgClient: NorgClient,
    isPapirSoknad: Boolean,
) {
    if (hendelse.navKontor == tildeltNavKontor) {
        return
    }

    if (hendelse.navKontor == soknadsmottaker?.navEnhetsnummer) {
        tildeltNavKontor = hendelse.navKontor
        return
    }

    tildeltNavKontor = hendelse.navKontor

    val destinasjon =
        try {
            norgClient.hentNavEnhet(hendelse.navKontor).navn
        } catch (e: Exception) {
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            null
        }

    soknadsmottaker = Soknadsmottaker(hendelse.navKontor, destinasjon ?: "et annet Nav-kontor")

    val isFirstTimeTildeltNavKontor = historikk.none { it.type == HistorikkType.TILDELT_NAV_KONTOR }
    val hendelseTekstType =
        if (isPapirSoknad && isFirstTimeTildeltNavKontor) {
            if (destinasjon != null) {
                HendelseTekstType.SOKNAD_VIDERESENDT_PAPIRSOKNAD_MED_NORG_ENHET
            } else {
                HendelseTekstType.SOKNAD_VIDERESENDT_PAPIRSOKNAD_UTEN_NORG_ENHET
            }
        } else {
            if (destinasjon != null) {
                HendelseTekstType.SOKNAD_VIDERESENDT_MED_NORG_ENHET
            } else {
                HendelseTekstType.SOKNAD_VIDERESENDT_UTEN_NORG_ENHET
            }
        }

    log.info(
        "Hendelse: " +
            "Tidspunkt: ${hendelse.hendelsestidspunkt} " +
            "Tildelt Navkontor. " +
            "Beskrivelse: ${hendelseTekstType.name} " +
            "NavEnhetsnavn: ${soknadsmottaker?.navEnhetsnavn}}",
    )
    historikk.add(
        Hendelse(
            hendelseTekstType,
            hendelse.hendelsestidspunkt.toLocalDateTime(),
            type = HistorikkType.TILDELT_NAV_KONTOR,
            tekstArgument = destinasjon,
        ),
    )
}
