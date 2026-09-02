package no.nav.sosialhjelp.digisos.hendelser.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Immutable folded state of a søknad.
 *
 * [status] is the raw folded value from the hendelse stream. [avledetStatus] applies the
 * "aktive saker" override on top of it.
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
    val mottaker: NavEnhet?,
    /** Full tildeling history — each entry is one TildeltNavKontor hendelse. */
    val navKontorHistorikk: List<NavKontorTildeling>,
    val saker: List<Sak>,
    /**
     * Vedtak from a VedtakFattet with a blank saksreferanse.
     */
    val vedtakUtenSak: List<Vedtak>,
    /** Utbetalinger whose saksreferanse is blank or matches no known sak. */
    val utbetalingerUtenSak: List<Utbetaling>,
    /**
     * Dokumentasjon etterspurt på søknadsnivå. From JsonDokumentasjonEtterspurt or the original
     * søknad's VedleggKreves fallback.
     */
    val dokumentasjonEtterspurt: List<DokumentasjonEtterspurt>,
    val forvaltningsbrev: List<DatertDokument>,
    val forelopigSvar: DatertDokument?,
    /** Reference to the original søknad document in Fiks Dokumentlager. */
    val originalSoknad: DokumentRef?,
) {
    /**
     * "aktive saker" override: a FERDIGBEHANDLET søknad with a sak that has no vedtak and is
     * still UNDER_BEHANDLING is reported as UNDER_BEHANDLING, so oppgaver from that sak stay
     * visible.
     */
    val avledetStatus: SoknadsStatus
        get() =
            if (status == SoknadsStatus.FERDIGBEHANDLET &&
                saker.any { it.vedtak.isEmpty() && it.saksStatus == SaksStatus.UNDER_BEHANDLING }
            ) {
                SoknadsStatus.UNDER_BEHANDLING
            } else {
                status
            }
}

data class Fagsystem(
    val systemnavn: String?,
    val systemversjon: String?,
)

/**
 * A Nav enhet identified by its enhetsnummer.
 * [navn] is resolved by the consumer via NORG.
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
    /** null when the sak was synthesized from a hendelse referencing an unknown sak */
    val saksStatus: SaksStatus?,
    /** null for a synthesized sak */
    val tittel: String?,
    val vedtak: List<Vedtak>,
    val utbetalinger: List<Utbetaling>,
    val dokumentasjonkrav: List<Dokumentasjonkrav>,
    val vilkar: List<Vilkar>,
)

data class Vedtak(
    val dokument: DokumentRef,
    val utfall: UtfallVedtak?,
    val dato: LocalDate?,
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
    /** true when payment goes to someone other than søker */
    val annenMottaker: Boolean,
    /** Null when [annenMottaker] is true */
    val kontonummer: String?,
    val utbetalingsmetode: String?,
    val sistEndret: Instant,
) {
    val belopAsDouble: Double get() = belopString.toDoubleOrNull() ?: 0.0
}

/** From a JsonDokumentasjonEtterspurt hendelse, or the original søknad's VedleggKreves entries. */
data class DokumentasjonEtterspurt(
    val referanse: String,
    val tittel: String?,
    val beskrivelse: String?,
    val status: Oppgavestatus,
    /** null for [Kilde.SOKNAD_VEDLEGG_KREVES]. */
    val frist: LocalDate?,
    /** [gruppeIdForFrist] of [frist]. Stable grouping key */
    val gruppeId: String,
    val tidspunktForKrav: Instant,
    /** null for [Kilde.SOKNAD_VEDLEGG_KREVES]. */
    val forvaltningsbrevRef: DokumentRef?,
    val kilde: Kilde,
) {
    enum class Kilde { DOKUMENTASJON_ETTERSPURT, SOKNAD_VEDLEGG_KREVES }
}

/** From a JsonDokumentasjonkrav hendelse. Always belongs to a [Sak]. */
data class Dokumentasjonkrav(
    val referanse: String,
    val tittel: String?,
    val beskrivelse: String?,
    val status: Oppgavestatus,
    val frist: LocalDate?,
    val gruppeId: String,
    /** References into the parent [Sak.utbetalinger]. Many-to-many */
    val utbetalingsReferanser: List<String>,
    val datoLagtTil: Instant,
)

/** From a JsonVilkar hendelse. Always belongs to a [Sak]. */
data class Vilkar(
    val referanse: String,
    val tittel: String?,
    val beskrivelse: String?,
    val status: Oppgavestatus,
    /** References into the parent [Sak.utbetalinger]. Many-to-many */
    val utbetalingsReferanser: List<String>,
    val datoLagtTil: Instant,
    val datoSistEndret: Instant,
)

/**
 * Stable grouping key for krav sharing a frist. sha256 of the frist, or of the literal string
 * "null" when absent.
 */
fun gruppeIdForFrist(frist: LocalDate?): String = sha256(frist.toString())

/** Opaque reference to a document in Fiks */
sealed interface DokumentRef {
    data class Dokumentlager(val id: String) : DokumentRef
    data class SvarUt(val id: String, val nr: Int) : DokumentRef
}

data class DatertDokument(
    val dokumentRef: DokumentRef,
    val tidspunkt: Instant,
)

enum class SoknadsStatus { SENDT, MOTTATT, UNDER_BEHANDLING, FERDIGBEHANDLET, BEHANDLES_IKKE }

enum class SaksStatus { UNDER_BEHANDLING, IKKE_INNSYN, FERDIGBEHANDLET, BEHANDLES_IKKE, FEILREGISTRERT }

enum class UtbetalingsStatus { PLANLAGT_UTBETALING, UTBETALT, STOPPET, ANNULLERT }

enum class UtfallVedtak { INNVILGET, DELVIS_INNVILGET, AVSLATT, AVVIST }

enum class Oppgavestatus { RELEVANT, ANNULLERT, OPPFYLT, IKKE_OPPFYLT, LEVERT_TIDLIGERE }
