package no.nav.sosialhjelp.digisos.hendelser.event

import no.nav.sosialhjelp.filformat.vedlegg.Vedlegg

/** Minimum interface for fetching vedlegg — implemented by each consumer. */
interface VedleggService {
    suspend fun hentSoknadVedleggMedStatus(status: String): List<Vedlegg>
}

const val VEDLEGG_KREVES_STATUS = "VedleggKreves"
