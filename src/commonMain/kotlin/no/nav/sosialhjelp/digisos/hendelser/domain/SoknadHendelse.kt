package no.nav.sosialhjelp.digisos.hendelser.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Sealed hierarchy of typed domain events emitted during the fold of a søknad's hendelse stream.
 *
 * Design principle: pure facts only — NO display text, NO i18n keys, NO NORG lookups.
 * Consumers map events to their own representation:
 *  - innsyn-api: maps to HendelseTekstType / i18n keys for the frontend
 *  - modia-api: maps to Norwegian strings in Titler.kt
 *  - adminpanel: whatever the debug view needs
 *
 * Key load-bearing facts preserved for consumers:
 *  - [SoknadsStatusEndret.mottakerNavn] lets innsyn reconstruct SOKNAD_MOTTATT_MED/UTEN_KOMMUNENAVN
 *  - [TildeltNavKontor.erForsteTildeling] and [Soknad.erPapirsoknad] reconstruct the four
 *    SOKNAD_VIDERESENDT_* variants
 *  - [NavKontorTildeling] in [Soknad.navKontorHistorikk] gives modia the full routing history
 */
sealed interface SoknadHendelse {
    val tidspunkt: Instant
}

data class SoknadSendt(
    override val tidspunkt: Instant,
    val mottaker: NavEnhet?,
    val soknadDokumentRef: DokumentRef?,
) : SoknadHendelse

data class SoknadsStatusEndret(
    override val tidspunkt: Instant,
    val status: SoknadsStatus,
    /**
     * The name of the Nav-enhet that received the application at the time the MOTTATT
     * hendelse was processed, stripped of " kommune" suffix.
     * null means the name was not available.
     * Used by innsyn to choose SOKNAD_MOTTATT_MED_KOMMUNENAVN vs SOKNAD_MOTTATT_UTEN_KOMMUNENAVN.
     */
    val mottakerNavn: String?,
) : SoknadHendelse

data class TildeltNavKontor(
    override val tidspunkt: Instant,
    val fraEnhetsnummer: String?,
    val tilEnhetsnummer: String,
    /**
     * true = the first tildeling that differs from the original mottaker.
     * Used by innsyn to choose SOKNAD_VIDERESENDT_MED_NAVKONTOR vs SOKNAD_VIDERESENDT_UTEN_NAVKONTOR.
     */
    val erForsteTildeling: Boolean,
) : SoknadHendelse

data class SaksStatusEndret(
    override val tidspunkt: Instant,
    val saksReferanse: String,
    val tittel: String?,
    val status: SaksStatus,
    val erNyeSak: Boolean,
) : SoknadHendelse

data class VedtakFattet(
    override val tidspunkt: Instant,
    val saksReferanse: String?,
    val saksTittel: String?,
    val utfall: UtfallVedtak?,
    val vedtakRef: DokumentRef,
) : SoknadHendelse

data class DokumentasjonEtterspurt(
    override val tidspunkt: Instant,
    val harDokumenter: Boolean,
    val forvaltningsbrevRef: DokumentRef?,
) : SoknadHendelse

data class ForelopigSvarMottatt(
    override val tidspunkt: Instant,
    val brevRef: DokumentRef,
) : SoknadHendelse

data class UtbetalingEndret(
    override val tidspunkt: Instant,
    val utbetalingsReferanse: String,
    val status: UtbetalingsStatus,
) : SoknadHendelse

data class KravEndret(
    override val tidspunkt: Instant,
    val kravReferanse: String,
    val kravType: KravType,
) : SoknadHendelse

enum class KravType { DOKUMENTASJONKRAV, VILKAR }

data class SoknadKravLagtTil(
    override val tidspunkt: Instant,
    val antallKrav: Int,
) : SoknadHendelse

/** Fired when a DokumentasjonEtterspurt empties the oppgave-list and status != BEHANDLES_IKKE. */
data class OppgaverTrukket(
    override val tidspunkt: Instant,
) : SoknadHendelse

/**
 * Returns a stable gruppeId string for grouping krav by frist.
 * Preserved from the existing sha256(frist.toString()) pattern both apps share.
 */
fun gruppeIdForFrist(frist: LocalDate): String = sha256(frist.toString())
