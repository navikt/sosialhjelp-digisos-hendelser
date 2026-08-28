package no.nav.sosialhjelp.fiks.event

import no.nav.sosialhjelp.fiks.domain.DokumentRef
import no.nav.sosialhjelp.fiks.domain.Fagsystem
import no.nav.sosialhjelp.fiks.domain.ForelopigSvar
import no.nav.sosialhjelp.fiks.domain.Forvaltningsbrev
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.NavEnhet
import no.nav.sosialhjelp.fiks.domain.NavKontorTildeling
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.domain.Sak
import no.nav.sosialhjelp.fiks.domain.Soknad
import no.nav.sosialhjelp.fiks.domain.SoknadHendelse
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.domain.Utbetaling
import no.nav.sosialhjelp.fiks.domain.Vedtak
import java.time.Instant

/**
 * Mutable accumulator used during the fold of a søknad's hendelse stream.
 * After all hendelser have been applied, call [toSoknad] to produce the immutable [Soknad].
 *
 * The hendelse list is emitted as a side output during the fold and collected in [hendelser].
 */
internal data class FoldAccumulator(
    var fagsystem: Fagsystem? = null,
    var navEksternRefId: String? = null,
    var fiksDigisosId: String = "",
    var kommunenummer: String = "",
    var erPapirsoknad: Boolean = false,
    var tidspunktSendt: Instant? = null,
    var sistEndret: Instant = Instant.EPOCH,
    var status: SoknadsStatus = SoknadsStatus.SENDT,
    var mottaker: NavEnhet? = null,
    val navKontorHistorikk: MutableList<NavKontorTildeling> = mutableListOf(),
    /** Track kontor for idempotency checks. */
    var tildeltNavKontor: String? = null,
    val saker: MutableList<Sak> = mutableListOf(),
    val vedtak: MutableList<Vedtak> = mutableListOf(),
    val utbetalinger: MutableList<Utbetaling> = mutableListOf(),
    val krav: MutableList<Krav> = mutableListOf(),
    val forvaltningsbrev: MutableList<Forvaltningsbrev> = mutableListOf(),
    var forelopigSvar: ForelopigSvar? = null,
    var originalSoknad: DokumentRef? = null,
    /** Events emitted during the fold, in emission order (will be sorted by tidspunkt later). */
    val hendelser: MutableList<SoknadHendelse> = mutableListOf(),
) {
    fun toSoknad(): Soknad {
        val avledetStatus = deriveStatus()
        return Soknad(
            fiksDigisosId = fiksDigisosId,
            navEksternRefId = navEksternRefId,
            kommunenummer = kommunenummer,
            fagsystem = fagsystem,
            erPapirsoknad = erPapirsoknad,
            tidspunktSendt = tidspunktSendt,
            sistEndret = sistEndret,
            status = status,
            avledetStatus = avledetStatus,
            mottaker = mottaker,
            navKontorHistorikk = navKontorHistorikk.toList(),
            saker = saker.toList(),
            vedtak = vedtak.toList(),
            utbetalinger = utbetalinger.toList(),
            krav = krav.toList(),
            forvaltningsbrev = forvaltningsbrev.toList(),
            forelopigSvar = forelopigSvar,
            originalSoknad = originalSoknad,
        )
    }

    /**
     * "aktive saker" override: if the folded status is FERDIGBEHANDLET but there are saker
     * with no vedtak and status UNDER_BEHANDLING, override to UNDER_BEHANDLING so that new
     * oppgaver from those saker remain visible.
     */
    private fun deriveStatus(): SoknadsStatus {
        if (status == SoknadsStatus.FERDIGBEHANDLET && saker.isNotEmpty()) {
            val hasActiveSaker =
                saker.any { sak ->
                    vedtak.none { it.saksReferanse == sak.referanse } &&
                        sak.saksStatus == no.nav.sosialhjelp.fiks.domain.SaksStatus.UNDER_BEHANDLING
                }
            if (hasActiveSaker) return SoknadsStatus.UNDER_BEHANDLING
        }
        return status
    }

    fun upsertSak(
        referanse: String,
        saksStatus: no.nav.sosialhjelp.fiks.domain.SaksStatus?,
        tittel: String?,
    ): Sak {
        val existing = saker.indexOfFirst { it.referanse == referanse }
        val sak = Sak(referanse = referanse, saksStatus = saksStatus, tittel = tittel)
        if (existing >= 0) {
            saker[existing] = sak
        } else {
            saker.add(sak)
        }
        return sak
    }

    fun getSak(referanse: String?): Sak? = saker.firstOrNull { it.referanse == referanse }

    // --- Utbetaling helpers ---

    fun upsertUtbetaling(utbetaling: Utbetaling) {
        utbetalinger.removeAll { it.referanse == utbetaling.referanse }
        utbetalinger.add(utbetaling)
    }

    fun getUtbetaling(referanse: String): Utbetaling? = utbetalinger.firstOrNull { it.referanse == referanse }

    // --- Krav helpers ---

    fun upsertKrav(krav: Krav) {
        this.krav.removeAll { it.referanse == krav.referanse && it::class == krav::class }
        this.krav.add(krav)
    }

    fun getKrav(referanse: String): Krav? = krav.firstOrNull { it.referanse == referanse }

    fun getKravOfType(
        referanse: String,
        type: kotlin.reflect.KClass<out Krav>,
    ): Krav? = krav.firstOrNull { it.referanse == referanse && it::class == type }

    /** Check whether any DokumentasjonEtterspurt krav exist (determines soknadKrav fallback). */
    fun harDokumentasjonEtterspurt(): Boolean = krav.any { it is Krav.DokumentasjonEtterspurt }

    /** Remove all DokumentasjonEtterspurt and SoknadVedleggKreves krav (wholesale replacement). */
    fun clearOppgaverKrav() {
        krav.removeAll { it is Krav.DokumentasjonEtterspurt || it is Krav.SoknadVedleggKreves }
    }

    /**
     * Update vilkår utbetalingsReferanser: remove vilkår from utbetalinger no longer referenced,
     * and ensure it is linked to those currently referenced.
     * The actual utbetaling objects in [utbetalinger] carry references only by referanse strings;
     * the krav list is the source of truth for vilkår-to-utbetaling linkage.
     */
    fun reconcileVilkarUtbetalingsReferanser(
        vilkarReferanse: String,
        nyeUtbetalingsReferanser: List<String>,
    ) {
        // Replace the vilkår in the krav list with updated references
        val existing = krav.filterIsInstance<Krav.Vilkar>().firstOrNull { it.referanse == vilkarReferanse }
        if (existing != null) {
            val updated = existing.copy(utbetalingsReferanser = nyeUtbetalingsReferanser)
            krav.removeAll { it is Krav.Vilkar && it.referanse == vilkarReferanse }
            krav.add(updated)
        }
    }

    fun reconcileDokumentasjonkravUtbetalingsReferanser(
        dokkravReferanse: String,
        nyeUtbetalingsReferanser: List<String>,
    ) {
        val existing = krav.filterIsInstance<Krav.Dokumentasjonkrav>().firstOrNull { it.referanse == dokkravReferanse }
        if (existing != null) {
            val updated = existing.copy(utbetalingsReferanser = nyeUtbetalingsReferanser)
            krav.removeAll { it is Krav.Dokumentasjonkrav && it.referanse == dokkravReferanse }
            krav.add(updated)
        }
    }

    /**
     * Oppgavestatus helper matching the two-app pattern:
     * OPPFYLT/IKKE_OPPFYLT → RELEVANT (deprecated statuses — both apps collapse them).
     */
    companion object {
        fun normalizeOppgavestatus(status: Oppgavestatus): Oppgavestatus =
            when (status) {
                Oppgavestatus.OPPFYLT, Oppgavestatus.IKKE_OPPFYLT -> Oppgavestatus.RELEVANT
                else -> status
            }
    }
}
