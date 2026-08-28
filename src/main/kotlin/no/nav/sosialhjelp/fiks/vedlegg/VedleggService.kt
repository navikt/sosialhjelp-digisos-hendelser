package no.nav.sosialhjelp.fiks.vedlegg

import no.nav.sbl.soknadsosialhjelp.vedlegg.JsonVedlegg
import no.nav.sbl.soknadsosialhjelp.vedlegg.JsonVedleggSpesifikasjon
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.api.fiks.DokumentInfo
import no.nav.sosialhjelp.fiks.app.auth.Caller
import no.nav.sosialhjelp.fiks.digisosapi.FiksService
import no.nav.sosialhjelp.fiks.utils.logger
import java.time.LocalDateTime

const val VEDLEGG_KREVES_STATUS = "VedleggKreves"

interface VedleggService {
    suspend fun hentSoknadVedleggMedStatus(
        status: String,
        digisosSak: DigisosSak,
    ): List<InternalVedlegg>
}

data class InternalVedlegg(
    val type: String,
    val tilleggsinfo: String?,
    val hendelseType: JsonVedlegg.HendelseType?,
    val hendelseReferanse: String?,
    val dokumentInfoList: MutableList<DokumentInfo>,
    val tidspunktLastetOpp: LocalDateTime,
    val innsendelsesfrist: LocalDateTime?,
)

/**
 * Fetches the original søknad's VedleggSpesifikasjon from Fiks and filters by [status].
 * Used for the applySoknadKrav fallback when no DokumentasjonEtterspurt exists.
 */
class FiksVedleggService(
    private val fiksService: FiksService,
    private val caller: Caller,
) : VedleggService {
    private val log by logger()

    override suspend fun hentSoknadVedleggMedStatus(
        status: String,
        digisosSak: DigisosSak,
    ): List<InternalVedlegg> {
        val vedleggMetadataId = digisosSak.originalSoknadNAV?.vedleggMetadata ?: return emptyList()

        return runCatching {
            val spec =
                fiksService.getDocument(
                    digisosId = digisosSak.fiksDigisosId,
                    dokumentlagerId = vedleggMetadataId,
                    requestedClass = JsonVedleggSpesifikasjon::class.java,
                    caller = caller,
                )
            spec.vedlegg
                .filter { it.status == status }
                .flatMap { vedlegg ->
                    vedlegg.filer.map { fil ->
                        InternalVedlegg(
                            type = vedlegg.type ?: "",
                            tilleggsinfo = vedlegg.tilleggsinfo,
                            hendelseType = JsonVedlegg.HendelseType.SOKNAD,
                            hendelseReferanse = null,
                            dokumentInfoList =
                                mutableListOf(
                                    DokumentInfo(
                                        filnavn = fil.filnavn ?: "",
                                        dokumentlagerDokumentId = "",
                                        storrelse = 0L,
                                    ),
                                ),
                            tidspunktLastetOpp = LocalDateTime.now(),
                            innsendelsesfrist = null,
                        )
                    }
                }
        }.onFailure { log.error("Henting av vedleggspesifikasjon feilet: ${it.message}") }
            .getOrElse { emptyList() }
    }
}
