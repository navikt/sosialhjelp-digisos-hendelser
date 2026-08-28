package no.nav.sosialhjelp.fiks.event

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonTildeltNavKontor
import no.nav.sosialhjelp.fiks.domain.NavEnhet
import no.nav.sosialhjelp.fiks.domain.NavKontorTildeling
import no.nav.sosialhjelp.fiks.domain.TildeltNavKontor
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.utils.toInstant
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

private val log = LoggerFactory.getLogger("no.nav.sosialhjelp.fiks.event.TildeltNavKontor")

internal suspend fun FoldAccumulator.apply(
    hendelse: JsonTildeltNavKontor,
    norgClient: NorgClient,
) {
    // Idempotent: ignore if same kontor was already set
    if (hendelse.navKontor == tildeltNavKontor) return

    // First tildeling that matches the original mottaker — just record the tracking var, no event
    if (hendelse.navKontor == mottaker?.enhetsnummer) {
        tildeltNavKontor = hendelse.navKontor
        return
    }

    val fraEnhet = mottaker
    val erForsteTildeling = navKontorHistorikk.isEmpty()
    tildeltNavKontor = hendelse.navKontor

    val norgNavn: String? =
        try {
            norgClient.hentNavEnhet(hendelse.navKontor).navn
        } catch (e: Exception) {
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            log.warn("NORG-oppslag for enhet ${hendelse.navKontor} feilet: ${e.message}")
            null
        }
    val enhetNavnOppslagFeilet = norgNavn == null
    val tilEnhet = NavEnhet(hendelse.navKontor, norgNavn ?: "et annet Nav-kontor")

    mottaker = tilEnhet
    navKontorHistorikk.add(
        NavKontorTildeling(
            tidspunkt = hendelse.hendelsestidspunkt.toInstant(),
            enhet = tilEnhet,
            erForsteTildeling = erForsteTildeling,
            enhetNavnOppslagFeilet = enhetNavnOppslagFeilet,
        ),
    )

    hendelser.add(
        TildeltNavKontor(
            tidspunkt = hendelse.hendelsestidspunkt.toInstant(),
            fraEnhet = fraEnhet,
            tilEnhet = tilEnhet,
            erForsteTildeling = erForsteTildeling,
            enhetNavnOppslagFeilet = enhetNavnOppslagFeilet,
        ),
    )
}
