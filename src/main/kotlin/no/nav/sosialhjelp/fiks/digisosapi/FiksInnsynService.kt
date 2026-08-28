package no.nav.sosialhjelp.fiks.digisosapi

import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.event.EventService
import no.nav.sosialhjelp.fiks.event.InnsynService
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.utils.logger
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService

/**
 * InnsynService implementation that fetches documents from Fiks.
 * Created per-request so the caller's token flows into document fetching.
 */
class FiksInnsynService(
    private val fiksService: FiksService,
    private val token: String,
) : InnsynService {
    private val log by logger()

    override suspend fun hentJsonDigisosSoker(digisosSak: DigisosSak): JsonDigisosSoker? {
        val metadataId = digisosSak.digisosSoker?.metadata
        val sistOppdatert = digisosSak.digisosSoker?.timestampSistOppdatert

        return when {
            metadataId != null && sistOppdatert != null -> {
                // We don't have a Caller here, so we pass the raw token.
                // FiksService uses a synthetic Citizen caller for document fetching.
                fiksService.getDocumentWithToken(
                    digisosId = digisosSak.fiksDigisosId,
                    dokumentlagerId = metadataId,
                    requestedClass = JsonDigisosSoker::class.java,
                    cacheKey = "${metadataId}_$sistOppdatert",
                    token = token,
                )
            }
            else ->
                null.also {
                    log.debug("Mangler metadataId eller sistOppdatert for DigisosSak ${digisosSak.fiksDigisosId}")
                }
        }
    }

    override suspend fun hentOriginalSoknad(digisosSak: DigisosSak): JsonSoknad? {
        val originalMetadataId = digisosSak.originalSoknadNAV?.metadata ?: return null
        return fiksService.getDocumentWithToken(
            digisosId = digisosSak.fiksDigisosId,
            dokumentlagerId = originalMetadataId,
            requestedClass = JsonSoknad::class.java,
            token = token,
        )
    }
}

/**
 * Factory for creating request-scoped EventService instances.
 * Each request gets its own InnsynService carrying the correct token.
 */
class EventServiceFactory(
    private val fiksService: FiksService,
    private val norgClient: NorgClient,
) {
    fun createForToken(
        token: String,
        vedleggService: VedleggService,
    ): EventService =
        EventService(
            innsynService = FiksInnsynService(fiksService, token),
            vedleggService = vedleggService,
            norgClient = norgClient,
        )
}
