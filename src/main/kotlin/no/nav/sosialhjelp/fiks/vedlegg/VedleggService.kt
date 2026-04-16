package no.nav.sosialhjelp.fiks.vedlegg

import no.nav.sbl.soknadsosialhjelp.vedlegg.JsonVedlegg
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.api.fiks.DokumentInfo
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
