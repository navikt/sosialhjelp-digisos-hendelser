package no.nav.sosialhjelp.fiks.digisosapi

import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.domain.SoknadResponse
import no.nav.sosialhjelp.fiks.event.EventService
import no.nav.sosialhjelp.fiks.event.InnsynService
import no.nav.sosialhjelp.fiks.utils.logger
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService

/**
 * InnsynService implementation backed by a pre-fetched innsynsfil (for bulk mode).
 * Returns the pre-fetched JsonDigisosSoker and fetches the original søknad from the cache.
 */
class PreFetchedInnsynService(
    private val jsonDigisosSoker: JsonDigisosSoker?,
    private val fiksService: FiksService,
    private val token: String,
) : InnsynService {
    override suspend fun hentJsonDigisosSoker(digisosSak: DigisosSak): JsonDigisosSoker? = jsonDigisosSoker

    override suspend fun hentOriginalSoknad(digisosSak: DigisosSak): no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad? {
        val originalMetadataId = digisosSak.originalSoknadNAV?.metadata ?: return null
        return fiksService.getDocumentWithToken(
            digisosId = digisosSak.fiksDigisosId,
            dokumentlagerId = originalMetadataId,
            requestedClass = no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad::class.java,
            token = token,
        )
    }
}

/**
 * Builds [SoknadResponse] objects for all saker in bulk.
 * Used by the oversikt endpoints to avoid N sequential EventService.createModel calls.
 */
class BulkOversiktService(
    private val fiksService: FiksService,
    private val eventService: EventService,
    private val vedleggService: VedleggService,
) {
    private val log by logger()

    suspend fun createOversikt(
        sakerMedInnsynsfiler: List<Pair<DigisosSak, JsonDigisosSoker?>>,
        token: String,
    ): List<SoknadResponse> =
        sakerMedInnsynsfiler.mapNotNull { (sak, innsynsfil) ->
            runCatching {
                val innsynService = PreFetchedInnsynService(innsynsfil, fiksService, token)
                val eventService =
                    EventService(
                        innsynService = innsynService,
                        vedleggService = vedleggService,
                        norgClient = this.eventService.norgClient,
                    )
                eventService.createModel(sak)
            }.onFailure {
                log.warn("Feil ved fold av sak ${sak.fiksDigisosId}: ${it.message}")
            }.getOrNull()
        }
}
