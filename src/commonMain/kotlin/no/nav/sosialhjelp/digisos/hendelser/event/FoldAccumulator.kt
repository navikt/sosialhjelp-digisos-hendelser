package no.nav.sosialhjelp.digisos.hendelser.event

import kotlinx.datetime.Instant
import no.nav.sosialhjelp.digisos.hendelser.domain.DokumentRef
import no.nav.sosialhjelp.digisos.hendelser.domain.Fagsystem
import no.nav.sosialhjelp.digisos.hendelser.domain.FoldResult
import no.nav.sosialhjelp.digisos.hendelser.domain.ForelopigSvar
import no.nav.sosialhjelp.digisos.hendelser.domain.Forvaltningsbrev
import no.nav.sosialhjelp.digisos.hendelser.domain.Krav
import no.nav.sosialhjelp.digisos.hendelser.domain.NavEnhet
import no.nav.sosialhjelp.digisos.hendelser.domain.NavKontorTildeling
import no.nav.sosialhjelp.digisos.hendelser.domain.Sak
import no.nav.sosialhjelp.digisos.hendelser.domain.SaksStatus
import no.nav.sosialhjelp.digisos.hendelser.domain.Soknad
import no.nav.sosialhjelp.digisos.hendelser.domain.SoknadHendelse
import no.nav.sosialhjelp.digisos.hendelser.domain.SoknadsStatus
import no.nav.sosialhjelp.digisos.hendelser.domain.Utbetaling
import no.nav.sosialhjelp.digisos.hendelser.domain.Vedtak

/**
 * Mutable accumulator used during the fold of a søknad's hendelse stream.
 * After all hendelser have been applied, call [toFoldResult] to produce the immutable output.
 *
 * The typed event list is collected in [hendelser] as a side output during the fold.
 */
internal data class FoldAccumulator(
    var fagsystem: Fagsystem? = null,
    var navEksternRefId: String? = null,
    var fiksDigisosId: String = "",
    var kommunenummer: String = "",
    var erPapirsoknad: Boolean = false,
    var tidspunktSendt: Instant? = null,
    var sistEndret: Instant = Instant.DISTANT_PAST,
    var status: SoknadsStatus = SoknadsStatus.SENDT,
    var mottaker: NavEnhet? = null,
    val navKontorHistorikk: MutableList<NavKontorTildeling> = mutableListOf(),
    /** Track kontor enhetsnummer for idempotency checks in TildeltNavKontor.apply. */
    var tildeltNavKontor: String? = null,
    val saker: MutableList<Sak> = mutableListOf(),
    val vedtak: MutableList<Vedtak> = mutableListOf(),
    val utbetalinger: MutableList<Utbetaling> = mutableListOf(),
    val krav: MutableList<Krav> = mutableListOf(),
    val forvaltningsbrev: MutableList<Forvaltningsbrev> = mutableListOf(),
    var forelopigSvar: ForelopigSvar? = null,
    var originalSoknad: DokumentRef? = null,
    /** Events emitted during the fold, in emission order (sorted by tidspunkt in toFoldResult). */
    val hendelser: MutableList<SoknadHendelse> = mutableListOf(),
) {
    fun toFoldResult(): FoldResult =
        FoldResult(
            soknad =
                Soknad(
                    fiksDigisosId = fiksDigisosId,
                    navEksternRefId = navEksternRefId,
                    kommunenummer = kommunenummer,
                    fagsystem = fagsystem,
                    erPapirsoknad = erPapirsoknad,
                    tidspunktSendt = tidspunktSendt,
                    sistEndret = sistEndret,
                    status = status,
                    avledetStatus = deriveStatus(),
                    mottaker = mottaker,
                    navKontorHistorikk = navKontorHistorikk.toList(),
                    saker = saker.toList(),
                    vedtak = vedtak.toList(),
                    utbetalinger = utbetalinger.toList(),
                    krav = krav.toList(),
                    forvaltningsbrev = forvaltningsbrev.toList(),
                    forelopigSvar = forelopigSvar,
                    originalSoknad = originalSoknad,
                ),
            hendelser = hendelser.sortedBy { it.tidspunkt },
        )

    /**
     * "aktive saker" override: if the folded status is FERDIGBEHANDLET but there are saker
     * with no vedtak and status UNDER_BEHANDLING, override to UNDER_BEHANDLING so new
     * oppgaver from those saker remain visible.
     */
    private fun deriveStatus(): SoknadsStatus {
        if (status == SoknadsStatus.FERDIGBEHANDLET && saker.isNotEmpty()) {
            val hasActiveSaker =
                saker.any { sak ->
                    vedtak.none { it.saksReferanse == sak.referanse } &&
                        sak.saksStatus == SaksStatus.UNDER_BEHANDLING
                }
            if (hasActiveSaker) return SoknadsStatus.UNDER_BEHANDLING
        }
        return status
    }

    fun upsertSak(
        referanse: String,
        saksStatus: SaksStatus?,
        tittel: String?,
    ): Sak {
        val existing = saker.indexOfFirst { it.referanse == referanse }
        val sak = Sak(referanse = referanse, saksStatus = saksStatus, tittel = tittel)
        if (existing >= 0) saker[existing] = sak else saker.add(sak)
        return sak
    }

    fun getSak(referanse: String?): Sak? = saker.firstOrNull { it.referanse == referanse }

    fun upsertUtbetaling(utbetaling: Utbetaling) {
        utbetalinger.removeAll { it.referanse == utbetaling.referanse }
        utbetalinger.add(utbetaling)
    }

    fun getUtbetaling(referanse: String): Utbetaling? = utbetalinger.firstOrNull { it.referanse == referanse }

    fun upsertKrav(krav: Krav) {
        this.krav.removeAll { it.referanse == krav.referanse && it::class == krav::class }
        this.krav.add(krav)
    }

    fun harDokumentasjonEtterspurt(): Boolean = krav.any { it is Krav.DokumentasjonEtterspurt }

    fun clearOppgaverKrav() {
        krav.removeAll { it is Krav.DokumentasjonEtterspurt || it is Krav.SoknadVedleggKreves }
    }
}
