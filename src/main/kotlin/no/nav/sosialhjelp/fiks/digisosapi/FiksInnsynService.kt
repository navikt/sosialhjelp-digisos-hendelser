package no.nav.sosialhjelp.fiks.digisosapi

import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.event.EventService
import no.nav.sosialhjelp.fiks.event.InnsynService
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.utils.logger
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService

/**
 * InnsynService-implementasjon som henter dokumenter fra Fiks.
 * Opprettes per request slik at tokenet er tilgjengelig.
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
            metadataId != null && sistOppdatert != null ->
                fiksService.getDocument(
                    digisosId = digisosSak.fiksDigisosId,
                    dokumentlagerId = metadataId,
                    requestedClass = JsonDigisosSoker::class.java,
                    cacheKey = "${metadataId}_$sistOppdatert",
                    token = token,
                )

            else -> null.also { log.debug("Mangler metadataId eller sistOppdatert for DigisosSak ${digisosSak.fiksDigisosId}") }
        }
    }

    override suspend fun hentOriginalSoknad(digisosSak: DigisosSak): JsonSoknad? {
        val originalMetadataId = digisosSak.originalSoknadNAV?.metadata ?: return null

        return fiksService.getDocument(
            digisosId = digisosSak.fiksDigisosId,
            dokumentlagerId = originalMetadataId,
            requestedClass = JsonSoknad::class.java,
            token = token,
        )
    }
}

/**
 * Fabrikk for å opprette request-scopede EventService-instanser.
 * Sørger for at hvert request får sin egen InnsynService med riktig token.
 */
class EventServiceFactory(
    private val clientProperties: ClientProperties,
    private val fiksService: FiksService,
    private val vedleggService: VedleggService,
    private val norgClient: NorgClient,
) {
    fun createForToken(token: String): EventService =
        EventService(
            clientProperties = clientProperties,
            innsynService = FiksInnsynService(fiksService, token),
            vedleggService = vedleggService,
            norgClient = norgClient,
        )
}
