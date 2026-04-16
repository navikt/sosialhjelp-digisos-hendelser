package no.nav.sosialhjelp.fiks.domain

import no.nav.sbl.soknadsosialhjelp.vedlegg.JsonVedlegg
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class InternalDigisosSoker(
    var fagsystem: Fagsystem? = null,
    var referanse: String? = null,
    var fiksDigisosId: String? = null,
    var status: SoknadsStatus = SoknadsStatus.SENDT,
    var saker: MutableList<Sak> = mutableListOf(),
    var utbetalinger: MutableList<Utbetaling> = mutableListOf(),
    var forvaltningsbrev: MutableList<Forvaltningsbrev> = mutableListOf(),
    var soknadsmottaker: Soknadsmottaker? = null,
    var tildeltNavKontor: String? = null,
    var oppgaver: MutableList<Oppgave> = mutableListOf(),
    var historikk: MutableList<Hendelse> = mutableListOf(),
    var forelopigSvar: ForelopigSvar = ForelopigSvar(false, null),
    var tidspunktSendt: LocalDateTime? = null,
    var vilkar: MutableList<Vilkar> = mutableListOf(),
    var dokumentasjonkrav: MutableList<Dokumentasjonkrav> = mutableListOf(),
)

data class Fagsystem(
    var systemnavn: String?,
    var systemversjon: String?,
)

data class Forvaltningsbrev(
    val url: String,
    val timestamp: LocalDateTime,
)

data class Soknadsmottaker(
    val navEnhetsnummer: String?,
    val navEnhetsnavn: String?,
)

data class Oppgave(
    var oppgaveId: String,
    var tittel: String,
    var tilleggsinfo: String?,
    val hendelsetype: JsonVedlegg.HendelseType?,
    val hendelsereferanse: String?,
    var innsendelsesfrist: LocalDateTime?,
    var tidspunktForKrav: LocalDateTime,
    var erFraInnsyn: Boolean,
    val forvaltningsbrev: Forvaltningsbrev? = null,
)

data class Sak(
    var referanse: String,
    var saksStatus: SaksStatus?,
    var tittel: String?,
    var vedtak: MutableList<Vedtak>,
    var utbetalinger: MutableList<Utbetaling>,
)

data class Vedtak(
    val id: String,
    var utfall: UtfallVedtak?,
    var vedtaksFilUrl: String,
    var dato: LocalDate?,
)

data class Utbetaling(
    var referanse: String,
    var status: UtbetalingsStatus,
    var belop: BigDecimal,
    var beskrivelse: String?,
    var forfallsDato: LocalDate?,
    var utbetalingsDato: LocalDate?,
    var stoppetDato: LocalDate?,
    var fom: LocalDate?,
    var tom: LocalDate?,
    var mottaker: String?,
    var annenMottaker: Boolean,
    var kontonummer: String?,
    var utbetalingsmetode: String?,
    var vilkar: MutableList<Vilkar>,
    var dokumentasjonkrav: MutableList<Dokumentasjonkrav>,
    var datoHendelse: LocalDateTime,
)

sealed class Oppgavehendelse {
    abstract var referanse: String
    abstract var tittel: String?
    abstract var beskrivelse: String?
    abstract var status: Oppgavestatus
    abstract var utbetalingsReferanse: List<String>?

    fun getTittelOgBeskrivelse(): Pair<String?, String?> {
        if (tittel.isNullOrBlank()) {
            return Pair(beskrivelse, null)
        }
        return Pair(tittel, beskrivelse)
    }

    fun getOppgaveStatus(): Oppgavestatus =
        when (status) {
            Oppgavestatus.OPPFYLT, Oppgavestatus.IKKE_OPPFYLT -> Oppgavestatus.RELEVANT
            else -> status
        }

    fun isEmpty(): Boolean = tittel.isNullOrBlank() && beskrivelse.isNullOrBlank()
}

data class Vilkar(
    override var referanse: String,
    override var tittel: String?,
    override var beskrivelse: String?,
    override var status: Oppgavestatus,
    override var utbetalingsReferanse: List<String>?,
    var datoLagtTil: LocalDateTime,
    var datoSistEndret: LocalDateTime,
    var saksReferanse: String?,
) : Oppgavehendelse()

data class Dokumentasjonkrav(
    val dokumentasjonkravId: String,
    val hendelsetype: JsonVedlegg.HendelseType?,
    override var referanse: String,
    override var tittel: String?,
    override var beskrivelse: String?,
    override var status: Oppgavestatus,
    override var utbetalingsReferanse: List<String>?,
    var datoLagtTil: LocalDateTime,
    var frist: LocalDate?,
    var saksreferanse: String?,
) : Oppgavehendelse()

data class Hendelse(
    val hendelseType: HendelseTekstType,
    val tidspunkt: LocalDateTime,
    val url: UrlResponse? = null,
    val type: HistorikkType? = null,
    val tekstArgument: String? = null,
    val saksReferanse: String? = null,
)

data class UrlResponse(
    val linkTekst: HendelseTekstType,
    val link: String,
)

data class ForelopigSvar(
    val harMottattForelopigSvar: Boolean,
    val link: String?,
    val timestamp: LocalDateTime? = null,
)

enum class SoknadsStatus {
    SENDT,
    MOTTATT,
    UNDER_BEHANDLING,
    FERDIGBEHANDLET,
    BEHANDLES_IKKE,
}

enum class SaksStatus {
    UNDER_BEHANDLING,
    IKKE_INNSYN,
    FERDIGBEHANDLET,
    BEHANDLES_IKKE,
    FEILREGISTRERT,
}

enum class UtbetalingsStatus {
    PLANLAGT_UTBETALING,
    UTBETALT,
    STOPPET,
    ANNULLERT,
}

enum class UtfallVedtak {
    INNVILGET,
    DELVIS_INNVILGET,
    AVSLATT,
    AVVIST,
}

enum class Oppgavestatus {
    RELEVANT,
    ANNULLERT,
    OPPFYLT,
    IKKE_OPPFYLT,
    LEVERT_TIDLIGERE,
}

enum class HistorikkType {
    TILDELT_NAV_KONTOR,
    DOKUMENTASJONSKRAV,
}

enum class HendelseTekstType {
    SOKNAD_SEND_TIL_KONTOR,
    SOKNAD_UNDER_BEHANDLING,
    SOKNAD_MOTTATT_MED_KOMMUNENAVN,
    SOKNAD_MOTTATT_UTEN_KOMMUNENAVN,
    SOKNAD_FERDIGBEHANDLET,
    SOKNAD_BEHANDLES_IKKE,
    SOKNAD_VIDERESENDT_PAPIRSOKNAD_MED_NORG_ENHET,
    SOKNAD_VIDERESENDT_PAPIRSOKNAD_UTEN_NORG_ENHET,
    SOKNAD_VIDERESENDT_MED_NORG_ENHET,
    SOKNAD_VIDERESENDT_UTEN_NORG_ENHET,
    SOKNAD_KAN_IKKE_VISE_STATUS_MED_TITTEL,
    SOKNAD_KAN_IKKE_VISE_STATUS_UTEN_TITTEL,
    SAK_UNDER_BEHANDLING_MED_TITTEL,
    SAK_UNDER_BEHANDLING_UTEN_TITTEL,
    SAK_FERDIGBEHANDLET_MED_TITTEL,
    SAK_FERDIGBEHANDLET_UTEN_TITTEL,
    SAK_KAN_IKKE_VISE_STATUS_MED_TITTEL,
    SAK_KAN_IKKE_VISE_STATUS_UTEN_TITTEL,
    ANTALL_SENDTE_VEDLEGG,
    UTBETALINGER_OPPDATERT,
    BREV_OM_SAKSBEANDLINGSTID,
    ETTERSPOR_MER_DOKUMENTASJON,
    ETTERSPOR_IKKE_MER_DOKUMENTASJON,
    DOKUMENTASJONKRAV,
    SOKNAD_SEND_TIL_KONTOR_LENKETEKST,
    VIS_BREVET_LENKETEKST,
}
