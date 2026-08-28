package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumentasjonEtterspurt
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumentasjonkrav
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonForelopigSvar
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonRammevedtak
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSaksStatus
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSoknadsStatus
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonTildeltNavKontor
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonUtbetaling
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVedtakFattet
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVilkar
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.api.fiks.OriginalSoknadNAV
import no.nav.sosialhjelp.fiks.domain.DokumentRef
import no.nav.sosialhjelp.fiks.domain.Fagsystem
import no.nav.sosialhjelp.fiks.domain.NavEnhet
import no.nav.sosialhjelp.fiks.domain.SoknadResponse
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.utils.logger
import no.nav.sosialhjelp.fiks.utils.unixToInstant
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class EventService(
    private val innsynService: InnsynService,
    private val vedleggService: VedleggService,
    val norgClient: NorgClient,
) {
    private val log by logger()

    /**
     * Create the full søknad model and event list.
     * This is the primary entry point used by both citizen and saksbehandler paths.
     */
    suspend fun createModel(digisosSak: DigisosSak): SoknadResponse {
        val jsonDigisosSoker: JsonDigisosSoker? = innsynService.hentJsonDigisosSoker(digisosSak)
        val jsonSoknad: JsonSoknad? = innsynService.hentOriginalSoknad(digisosSak)
        val originalSoknadNAV: OriginalSoknadNAV? = digisosSak.originalSoknadNAV

        val acc =
            FoldAccumulator(
                fiksDigisosId = digisosSak.fiksDigisosId,
                kommunenummer = digisosSak.kommunenummer,
                erPapirsoknad = originalSoknadNAV == null,
                sistEndret = unixToInstant(digisosSak.sistEndret),
            )

        if (jsonDigisosSoker?.avsender != null) {
            acc.fagsystem = Fagsystem(jsonDigisosSoker.avsender.systemnavn, jsonDigisosSoker.avsender.systemversjon)
        }

        // Seed from originalSoknadNAV (non-papir søknad)
        if (originalSoknadNAV?.timestampSendt != null && originalSoknadNAV.timestampSendt != 0L) {
            acc.tidspunktSendt = unixToInstant(originalSoknadNAV.timestampSendt)
            acc.navEksternRefId = originalSoknadNAV.navEksternRefId
            acc.status = SoknadsStatus.SENDT

            // Original søknad document reference
            originalSoknadNAV.soknadDokument?.dokumentlagerDokumentId?.let {
                acc.originalSoknad = DokumentRef.Dokumentlager(it)
            }

            // Initial mottaker from søknad.json
            if (jsonSoknad?.mottaker != null) {
                val enhetsnummer = jsonSoknad.mottaker.enhetsnummer
                val navn = jsonSoknad.mottaker.navEnhetsnavn
                val enhet = NavEnhet(enhetsnummer, navn)
                acc.mottaker = enhet
                acc.hendelser.add(
                    no.nav.sosialhjelp.fiks.domain.SoknadSendt(
                        tidspunkt = unixToInstant(originalSoknadNAV.timestampSendt),
                        mottaker = enhet,
                        soknadDokumentRef = acc.originalSoknad,
                    ),
                )
            }
        } else if (originalSoknadNAV?.timestampSendt == 0L) {
            log.error("Søknadens timestampSendt er 0 for fiksDigisosId=${digisosSak.fiksDigisosId}")
        }

        applyHendelserOgSoknadKrav(jsonDigisosSoker, acc, digisosSak, originalSoknadNAV)

        return SoknadResponse(
            soknad = acc.toSoknad(),
            hendelser = acc.hendelser.sortedBy { it.tidspunkt },
        )
    }

    private suspend fun applyHendelserOgSoknadKrav(
        jsonDigisosSoker: JsonDigisosSoker?,
        acc: FoldAccumulator,
        digisosSak: DigisosSak,
        originalSoknadNAV: OriginalSoknadNAV?,
    ) {
        jsonDigisosSoker
            ?.hendelser
            ?.sortedWith(hendelseComparator)
            ?.forEach { acc.applyHendelse(it, norgClient) }

        // Fallback: if no DokumentasjonEtterspurt exists and søknad < 30 days old
        if (originalSoknadNAV != null &&
            !acc.harDokumentasjonEtterspurt() &&
            soknadSendtForMindreEnn30DagerSiden(originalSoknadNAV.timestampSendt)
        ) {
            acc.applySoknadKrav(digisosSak, vedleggService, originalSoknadNAV.timestampSendt)
        }
    }

    companion object {
        private val log by logger()

        /**
         * Sort order for hendelser (identical to both innsyn-api and modia-api today):
         * 1. By hendelsestidspunkt (parsed as Instant for correctness — no longer raw string)
         * 2. UTBETALING before VILKAR/DOKUMENTASJONKRAV so krav can attach to the utbetaling
         * 3. MOTTATT before UNDER_BEHANDLING when timestamps are equal
         */
        val hendelseComparator: Comparator<JsonHendelse> =
            compareBy<JsonHendelse> { parseHendelsestidspunkt(it.hendelsestidspunkt) }
                .thenComparator { a, b -> compareHendelseByType(a.type, b.type) }
                .thenComparator { a, b ->
                    if (a is JsonSoknadsStatus && b is JsonSoknadsStatus) {
                        mottattBeforeUnderBehandling(a, b)
                    } else {
                        0
                    }
                }

        private fun parseHendelsestidspunkt(ts: String?): java.time.Instant =
            if (ts == null) {
                java.time.Instant.EPOCH
            } else {
                runCatching { ZonedDateTime.parse(ts, DateTimeFormatter.ISO_DATE_TIME).toInstant() }
                    .getOrElse { java.time.Instant.EPOCH }
            }

        private fun mottattBeforeUnderBehandling(
            a: JsonSoknadsStatus,
            b: JsonSoknadsStatus,
        ): Int =
            when {
                a.status == JsonSoknadsStatus.Status.MOTTATT && b.status == JsonSoknadsStatus.Status.UNDER_BEHANDLING -> -1
                b.status == JsonSoknadsStatus.Status.MOTTATT && a.status == JsonSoknadsStatus.Status.UNDER_BEHANDLING -> 1
                else -> 0
            }

        private fun compareHendelseByType(
            a: JsonHendelse.Type,
            b: JsonHendelse.Type,
        ): Int =
            when {
                a == JsonHendelse.Type.UTBETALING &&
                    (b == JsonHendelse.Type.VILKAR || b == JsonHendelse.Type.DOKUMENTASJONKRAV) -> -1
                b == JsonHendelse.Type.UTBETALING &&
                    (a == JsonHendelse.Type.VILKAR || a == JsonHendelse.Type.DOKUMENTASJONKRAV) -> 1
                else -> 0
            }

        fun soknadSendtForMindreEnn30DagerSiden(timestampSendt: Long): Boolean =
            unixToInstant(timestampSendt)
                .atZone(java.time.ZoneId.of("Europe/Oslo"))
                .toLocalDate()
                .isAfter(LocalDate.now().minusDays(30))

        fun stripEnhetsnavnForKommune(navn: String?): String? = navn?.replace(" kommune", "")
    }
}

private suspend fun FoldAccumulator.applyHendelse(
    hendelse: JsonHendelse,
    norgClient: NorgClient,
) {
    when (hendelse) {
        is JsonSoknadsStatus -> apply(hendelse)
        is JsonTildeltNavKontor -> apply(hendelse, norgClient)
        is JsonSaksStatus -> apply(hendelse)
        is JsonVedtakFattet -> apply(hendelse)
        is JsonDokumentasjonEtterspurt -> apply(hendelse)
        is JsonForelopigSvar -> apply(hendelse)
        is JsonUtbetaling -> apply(hendelse)
        is JsonVilkar -> apply(hendelse)
        is JsonDokumentasjonkrav -> apply(hendelse)
        is JsonRammevedtak -> { /* Gjør ingenting — rammevedtak vises ikke til bruker */ }
        else -> error("Hendelsetype ${hendelse.type.value()} mangler mapping")
    }
}

