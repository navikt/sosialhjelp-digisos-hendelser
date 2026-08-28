package no.nav.sosialhjelp.fiks.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Top-level response
// ---------------------------------------------------------------------------

/**
 * Combined response returned by all søknad endpoints.
 * Both the folded aggregate state and the ordered event list are included so consumers can
 * choose either or both without reimplementing the fold themselves.
 */
data class SoknadResponse(
    val soknad: Soknad,
    /** Ordered (ascending) typed domain events emitted during the fold. */
    val hendelser: List<SoknadHendelse>,
)

// ---------------------------------------------------------------------------
// Folded aggregate
// ---------------------------------------------------------------------------

/**
 * Immutable folded state of a søknad.
 *
 * [status] is the raw folded value from the hendelse stream.
 * [avledetStatus] is after the "aktive saker" override: a FERDIGBEHANDLET søknad with active
 * saker (vedtak.isEmpty && saksStatus == UNDER_BEHANDLING) is overridden to UNDER_BEHANDLING.
 * Consumers may use either; both are exposed so neither is lost.
 */
data class Soknad(
    val fiksDigisosId: String,
    val navEksternRefId: String?,
    val kommunenummer: String,
    val fagsystem: Fagsystem?,
    val erPapirsoknad: Boolean,
    val tidspunktSendt: Instant?,
    val sistEndret: Instant,
    val status: SoknadsStatus,
    val avledetStatus: SoknadsStatus,
    val mottaker: NavEnhet?,
    /** Full tildeling-history, including the initial mottaker. */
    val navKontorHistorikk: List<NavKontorTildeling>,
    val saker: List<Sak>,
    /**
     * Flat list of vedtak. [Vedtak.saksReferanse] is nullable — Fiks may emit a VedtakFattet
     * with no matching sak. Consumers group by saksReferanse if needed.
     */
    val vedtak: List<Vedtak>,
    val utbetalinger: List<Utbetaling>,
    /**
     * Unified list of all krav: DokumentasjonEtterspurt, SoknadVedleggKreves,
     * Dokumentasjonkrav and Vilkar. [Krav.kilde] discriminates.
     */
    val krav: List<Krav>,
    val forvaltningsbrev: List<Forvaltningsbrev>,
    val forelopigSvar: ForelopigSvar?,
    /** Reference to the original søknad document in Fiks Dokumentlager. */
    val originalSoknad: DokumentRef?,
)

// ---------------------------------------------------------------------------
// Sub-types used in the aggregate
// ---------------------------------------------------------------------------

data class Fagsystem(
    val systemnavn: String?,
    val systemversjon: String?,
)

data class NavEnhet(
    val enhetsnummer: String,
    val navn: String?,
)

data class NavKontorTildeling(
    val tidspunkt: Instant,
    val enhet: NavEnhet,
    /** true on the first tildeling that differs from the original mottaker. */
    val erForsteTildeling: Boolean,
    /** true when NORG lookup failed for this tildeling. */
    val enhetNavnOppslagFeilet: Boolean,
)

data class Sak(
    val referanse: String,
    val saksStatus: SaksStatus?,
    val tittel: String?,
)

data class Vedtak(
    val referanse: DokumentRef,
    val utfall: UtfallVedtak?,
    val dato: LocalDate?,
    /** Nullable: Fiks allows a vedtak without a matching sak. */
    val saksReferanse: String?,
)

data class Utbetaling(
    val referanse: String,
    val status: UtbetalingsStatus,
    val belop: BigDecimal,
    val beskrivelse: String?,
    val forfallsDato: LocalDate?,
    val utbetalingsDato: LocalDate?,
    val stoppetDato: LocalDate?,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val mottaker: String?,
    /** true when payment goes to someone other than the søker (annen mottaker == null also counts as true). */
    val annenMottaker: Boolean,
    /** Nulled when [annenMottaker] is true — fail-safe default to avoid exposing 3rd-party accounts. */
    val kontonummer: String?,
    val utbetalingsmetode: String?,
    val saksReferanse: String?,
    val datoHendelse: Instant,
    /** Vilkår and dokumentasjonkrav linked to this utbetaling are in [Soknad.krav] filtered on utbetalingsReferanser. */
)

/** Unified "krav" concept covering all four sources. */
sealed interface Krav {
    val referanse: String
    val tittel: String?
    val beskrivelse: String?
    val status: Oppgavestatus
    val frist: LocalDate?
    val saksReferanse: String?
    val utbetalingsReferanser: List<String>

    /**
     * sha256 of the frist date string — used as a grouping key by consumers,
     * preserved from the existing implementation to avoid breaking downstream grouping logic.
     */
    val gruppeId: String?

    /** From a JsonDokumentasjonEtterspurt hendelse. */
    data class DokumentasjonEtterspurt(
        override val referanse: String,
        override val tittel: String?,
        override val beskrivelse: String?,
        override val status: Oppgavestatus,
        override val frist: LocalDate?,
        override val saksReferanse: String? = null,
        override val utbetalingsReferanser: List<String> = emptyList(),
        override val gruppeId: String?,
        val tidspunktForKrav: Instant,
        val forvaltningsbrevRef: DokumentRef?,
    ) : Krav

    /** From the original søknad's VedleggKreves entries (fallback when no DokumentasjonEtterspurt exists). */
    data class SoknadVedleggKreves(
        override val referanse: String,
        override val tittel: String?,
        override val beskrivelse: String?,
        override val status: Oppgavestatus,
        override val frist: LocalDate? = null,
        override val saksReferanse: String? = null,
        override val utbetalingsReferanser: List<String> = emptyList(),
        override val gruppeId: String?,
        val tidspunktForKrav: Instant,
    ) : Krav

    /** From a JsonDokumentasjonkrav hendelse. */
    data class Dokumentasjonkrav(
        override val referanse: String,
        override val tittel: String?,
        override val beskrivelse: String?,
        override val status: Oppgavestatus,
        override val frist: LocalDate?,
        override val saksReferanse: String?,
        override val utbetalingsReferanser: List<String>,
        override val gruppeId: String?,
        val datoLagtTil: Instant,
    ) : Krav

    /** From a JsonVilkar hendelse. */
    data class Vilkar(
        override val referanse: String,
        override val tittel: String?,
        override val beskrivelse: String?,
        override val status: Oppgavestatus,
        override val frist: LocalDate? = null,
        override val saksReferanse: String?,
        override val utbetalingsReferanser: List<String>,
        override val gruppeId: String? = null,
        val datoLagtTil: Instant,
        val datoSistEndret: Instant,
    ) : Krav
}

/** Opaque reference to a document in Fiks — consumers build URLs from their own config. */
sealed interface DokumentRef {
    /** Fiks Dokumentlager (standard download path). */
    data class Dokumentlager(
        val id: String,
    ) : DokumentRef

    /** Fiks SvarUt (letter delivery service). */
    data class SvarUt(
        val id: String,
        val nr: Int,
    ) : DokumentRef
}

data class Forvaltningsbrev(
    val dokumentRef: DokumentRef,
    val tidspunkt: Instant,
)

data class ForelopigSvar(
    val dokumentRef: DokumentRef,
    val tidspunkt: Instant,
)

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class SoknadsStatus { SENDT, MOTTATT, UNDER_BEHANDLING, FERDIGBEHANDLET, BEHANDLES_IKKE }

enum class SaksStatus { UNDER_BEHANDLING, IKKE_INNSYN, FERDIGBEHANDLET, BEHANDLES_IKKE, FEILREGISTRERT }

enum class UtbetalingsStatus { PLANLAGT_UTBETALING, UTBETALT, STOPPET, ANNULLERT }

enum class UtfallVedtak { INNVILGET, DELVIS_INNVILGET, AVSLATT, AVVIST }

enum class Oppgavestatus { RELEVANT, ANNULLERT, OPPFYLT, IKKE_OPPFYLT, LEVERT_TIDLIGERE }
