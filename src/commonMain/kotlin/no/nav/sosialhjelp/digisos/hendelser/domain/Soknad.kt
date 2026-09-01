package no.nav.sosialhjelp.digisos.hendelser.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

// ---------------------------------------------------------------------------
// Top-level fold output
// ---------------------------------------------------------------------------

/**
 * Combined output of the fold.
 * Both the folded aggregate state and the ordered typed event list are included
 * so consumers can use either or both without reimplementing the fold themselves.
 */
data class FoldResult(
    val soknad: Soknad,
    /** Ordered (ascending by tidspunkt) typed domain events emitted during the fold. */
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
 * saker (no vedtak and saksStatus == UNDER_BEHANDLING) is overridden to UNDER_BEHANDLING.
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
    /** Full tildeling history — each entry is one TildeltNavKontor hendelse. */
    val navKontorHistorikk: List<NavKontorTildeling>,
    val saker: List<Sak>,
    /**
     * Flat list of vedtak. [Vedtak.saksReferanse] is nullable — Fiks may emit a VedtakFattet
     * without a matching sak. Consumers group by saksReferanse if needed.
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
// Sub-types
// ---------------------------------------------------------------------------

data class Fagsystem(
    val systemnavn: String?,
    val systemversjon: String?,
)

/**
 * A Nav enhet identified by its enhetsnummer.
 * [navn] is resolved by the consumer via NORG post-fold — the fold never calls NORG.
 */
data class NavEnhet(
    val enhetsnummer: String,
    val navn: String?,
)

data class NavKontorTildeling(
    val tidspunkt: Instant,
    val enhetsnummer: String,
    /** true on the first tildeling that differs from the original mottaker. */
    val erForsteTildeling: Boolean,
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
    /**
     * Stored as String to remain platform-neutral.
     * JVM consumers: call [belopAsBigDecimal] (jvmMain extension).
     * JS consumers: use [belopAsDouble].
     */
    val belopString: String,
    val beskrivelse: String?,
    val forfallsDato: LocalDate?,
    val utbetalingsDato: LocalDate?,
    val stoppetDato: LocalDate?,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val mottaker: String?,
    /** true when payment goes to someone other than søker (annenMottaker == null also counts as true). */
    val annenMottaker: Boolean,
    /** Nulled when [annenMottaker] is true — fail-safe to avoid exposing third-party account numbers. */
    val kontonummer: String?,
    val utbetalingsmetode: String?,
    val saksReferanse: String?,
    val datoHendelse: Instant,
) {
    /** Double representation — use for JS consumers or quick comparisons. */
    val belopAsDouble: Double get() = belopString.toDoubleOrNull() ?: 0.0
}

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
    data class Dokumentlager(val id: String) : DokumentRef
    data class SvarUt(val id: String, val nr: Int) : DokumentRef
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
