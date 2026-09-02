package no.nav.sosialhjelp.digisos.hendelser.fold

import kotlinx.datetime.Instant
import no.nav.sosialhjelp.digisos.hendelser.domain.DokumentRef
import no.nav.sosialhjelp.digisos.hendelser.domain.Fagsystem
import no.nav.sosialhjelp.digisos.hendelser.domain.NavEnhet
import no.nav.sosialhjelp.digisos.hendelser.domain.Soknad
import no.nav.sosialhjelp.digisos.hendelser.domain.hendelse.SoknadHendelse
import no.nav.sosialhjelp.digisos.hendelser.domain.hendelse.SoknadSendt
import no.nav.sosialhjelp.digisos.hendelser.domain.SoknadsStatus as DomainSoknadsStatus
import no.nav.sosialhjelp.digisos.hendelser.domain.toInstant
import no.nav.sosialhjelp.digisos.hendelser.domain.unixToInstant
import no.nav.sosialhjelp.digisos.hendelser.event.FoldAccumulator
import no.nav.sosialhjelp.digisos.hendelser.event.VedleggService
import no.nav.sosialhjelp.digisos.hendelser.event.apply
import no.nav.sosialhjelp.digisos.hendelser.event.applySoknadKrav
import no.nav.sosialhjelp.filformat.digisos.soker.DigisosSoker
import no.nav.sosialhjelp.filformat.digisos.soker.Dokumentasjonkrav
import no.nav.sosialhjelp.filformat.digisos.soker.DokumentasjonEtterspurt
import no.nav.sosialhjelp.filformat.digisos.soker.ForelopigSvar
import no.nav.sosialhjelp.filformat.digisos.soker.Rammevedtak
import no.nav.sosialhjelp.filformat.digisos.soker.SaksStatus
import no.nav.sosialhjelp.filformat.digisos.soker.SoknadsStatus
import no.nav.sosialhjelp.filformat.digisos.soker.TildeltNavKontor
import no.nav.sosialhjelp.filformat.digisos.soker.UkjentHendelse
import no.nav.sosialhjelp.filformat.digisos.soker.Utbetaling
import no.nav.sosialhjelp.filformat.digisos.soker.VedtakFattet
import no.nav.sosialhjelp.filformat.digisos.soker.Vilkar

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

/**
 * Metadata about the søknad that is not part of the hendelse stream.
 * Sourced from the consumer's Fiks API response (DigisosSak / OriginalSoknadNAV).
 */
data class SoknadMetadata(
    val fiksDigisosId: String,
    val kommunenummer: String,
    val erPapirsoknad: Boolean,
    val sistEndret: Instant,
    /** Epoch-millisecond timestamp from OriginalSoknadNAV.timestampSendt; null for paper søknader. */
    val timestampSendt: Long?,
    val navEksternRefId: String?,
    /** Dokumentlager id of the original søknad document; null if unavailable. */
    val originalSoknadDokumentlagerId: String?,
    /** Dokumentlager id of the vedlegg metadata document; used by [VedleggService]. */
    val vedleggMetadataDokumentlagerId: String?,
    val fagsystemNavn: String?,
    val fagsystemVersjon: String?,
    /** The initial mottaker enhetsnummer from søknad.mottaker. */
    val mottakerEnhetsnummer: String?,
    /** The initial mottaker enhetsnavn (consumer resolves via NORG if desired). */
    val mottakerEnhetsnavn: String?,
)

/**
 * Fold a [DigisosSoker] hendelse stream into a [FoldResult].
 *
 * The fold is **pure**: no IO, no NORG lookups, no text.
 * The [vedleggService] is only invoked as a fallback when no DokumentasjonEtterspurt hendelse
 * exists and the søknad is < 30 days old. Pass a no-op implementation to skip it.
 */
suspend fun fold(
    digisosSoker: DigisosSoker?,
    metadata: SoknadMetadata,
    vedleggService: VedleggService,
): FoldResult {
    val acc =
        FoldAccumulator(
            fiksDigisosId = metadata.fiksDigisosId,
            kommunenummer = metadata.kommunenummer,
            erPapirsoknad = metadata.erPapirsoknad,
            sistEndret = metadata.sistEndret,
            fagsystem =
                if (metadata.fagsystemNavn != null || metadata.fagsystemVersjon != null) {
                    Fagsystem(metadata.fagsystemNavn, metadata.fagsystemVersjon)
                } else if (digisosSoker?.avsender != null) {
                    Fagsystem(digisosSoker.avsender.systemnavn, digisosSoker.avsender.systemversjon)
                } else {
                    null
                },
        )

    val timestampSendt = metadata.timestampSendt
    if (timestampSendt != null && timestampSendt != 0L) {
        acc.tidspunktSendt = unixToInstant(timestampSendt)
        acc.navEksternRefId = metadata.navEksternRefId
        acc.status = DomainSoknadsStatus.SENDT

        metadata.originalSoknadDokumentlagerId?.let {
            acc.originalSoknad = DokumentRef.Dokumentlager(it)
        }

        if (metadata.mottakerEnhetsnummer != null) {
            val enhet = NavEnhet(metadata.mottakerEnhetsnummer, metadata.mottakerEnhetsnavn)
            acc.mottaker = enhet
            acc.hendelser.add(
                SoknadSendt(
                    tidspunkt = unixToInstant(timestampSendt),
                    mottaker = enhet,
                    soknadDokumentRef = acc.originalSoknad,
                ),
            )
        }
    }

    digisosSoker
        ?.hendelser
        ?.sortedWith(hendelseComparator)
        ?.forEach { hendelse ->
            when (hendelse) {
                is SoknadsStatus -> acc.apply(hendelse)
                is TildeltNavKontor -> acc.apply(hendelse)
                is SaksStatus -> acc.apply(hendelse)
                is VedtakFattet -> acc.apply(hendelse)
                is DokumentasjonEtterspurt -> acc.apply(hendelse)
                is ForelopigSvar -> acc.apply(hendelse)
                is Utbetaling -> acc.apply(hendelse)
                is Vilkar -> acc.apply(hendelse)
                is Dokumentasjonkrav -> acc.apply(hendelse)
                is Rammevedtak -> acc.apply(hendelse)
                is UkjentHendelse -> { /* forward-compat: new hendelse types are silently ignored */ }
            }
        }

    if (timestampSendt != null &&
        !acc.harDokumentasjonEtterspurt() &&
        soknadSendtForMindreEnn30DagerSiden(timestampSendt)
    ) {
        acc.applySoknadKrav(vedleggService, timestampSendt)
    }

    return acc.toFoldResult()
}

/** No-op VedleggService — use when the søknadskrav fallback is not needed. */
object NoopVedleggService : VedleggService {
    override suspend fun hentSoknadVedleggMedStatus(status: String) = emptyList<no.nav.sosialhjelp.filformat.vedlegg.Vedlegg>()
}

// ---------------------------------------------------------------------------
// Hendelse sort order — matches innsyn-api and modia-api
// ---------------------------------------------------------------------------

private val hendelseComparator: Comparator<no.nav.sosialhjelp.filformat.digisos.soker.Hendelse> =
    compareBy<no.nav.sosialhjelp.filformat.digisos.soker.Hendelse> {
        it.hendelsestidspunkt.toInstant()
    }.thenComparator { a, b -> compareByType(a, b) }
        .thenComparator { a, b ->
            if (a is SoknadsStatus && b is SoknadsStatus) mottattBeforeUnderBehandling(a, b) else 0
        }

private fun compareByType(
    a: no.nav.sosialhjelp.filformat.digisos.soker.Hendelse,
    b: no.nav.sosialhjelp.filformat.digisos.soker.Hendelse,
): Int =
    when {
        a is Utbetaling && (b is Vilkar || b is Dokumentasjonkrav) -> -1
        b is Utbetaling && (a is Vilkar || a is Dokumentasjonkrav) -> 1
        else -> 0
    }

private fun mottattBeforeUnderBehandling(
    a: SoknadsStatus,
    b: SoknadsStatus,
): Int =
    when {
        a.status == SoknadsStatus.Status.MOTTATT && b.status == SoknadsStatus.Status.UNDER_BEHANDLING -> -1
        b.status == SoknadsStatus.Status.MOTTATT && a.status == SoknadsStatus.Status.UNDER_BEHANDLING -> 1
        else -> 0
    }

private fun soknadSendtForMindreEnn30DagerSiden(timestampSendt: Long): Boolean {
    val sendt = unixToInstant(timestampSendt)
    val now = kotlinx.datetime.Clock.System.now()
    val thirtyDays = 30L * 24 * 60 * 60 * 1000
    return (now.toEpochMilliseconds() - sendt.toEpochMilliseconds()) < thirtyDays
}
