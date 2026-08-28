package no.nav.sosialhjelp.fiks.event

import io.mockk.every
import io.mockk.mockk
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.SoknadHendelse
import no.nav.sosialhjelp.fiks.domain.SoknadResponse
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService

/**
 * Test helper that runs the full EventService.createModel pipeline.
 */
internal object TestEventService {
    fun build(
        innsynService: InnsynService = mockk(),
        vedleggService: VedleggService = mockk(relaxed = true),
        norgClient: NorgClient = mockk(),
    ): EventService = EventService(innsynService, vedleggService, norgClient)
}

/**
 * Helper to build a minimal DigisosSak mock for tests.
 */
internal fun mockDigisosSak(
    fiksDigisosId: String = "test-id",
    kommunenummer: String = "0301",
    sistEndret: Long = System.currentTimeMillis(),
    timestampSendt: Long? = tidspunkt_soknad,
    navEksternRefId: String? = null,
    soknadDokumentId: String? = null,
    ettersendtInfoNAV: no.nav.sosialhjelp.api.fiks.EttersendtInfoNAV? = null,
    digisosSokerMetadata: String? = "meta-id",
    digisosSokerTimestamp: Long? = System.currentTimeMillis(),
): DigisosSak {
    val sak = mockk<DigisosSak>()
    every { sak.fiksDigisosId } returns fiksDigisosId
    every { sak.kommunenummer } returns kommunenummer
    every { sak.sistEndret } returns sistEndret
    every { sak.sokerFnr } returns "12345678901"
    every { sak.ettersendtInfoNAV } returns ettersendtInfoNAV

    if (timestampSendt != null) {
        val originalSoknadNAV = mockk<no.nav.sosialhjelp.api.fiks.OriginalSoknadNAV>()
        every { originalSoknadNAV.timestampSendt } returns timestampSendt
        every { originalSoknadNAV.navEksternRefId } returns navEksternRefId.orEmpty()
        every { originalSoknadNAV.metadata } returns "soknad-meta"
        every { originalSoknadNAV.vedleggMetadata } returns ""
        every { originalSoknadNAV.soknadDokument?.dokumentlagerDokumentId } returns soknadDokumentId
        every { sak.originalSoknadNAV } returns originalSoknadNAV
    } else {
        every { sak.originalSoknadNAV } returns null
    }

    if (digisosSokerMetadata != null && digisosSokerTimestamp != null) {
        val digisosSoker = mockk<no.nav.sosialhjelp.api.fiks.DigisosSoker>()
        every { digisosSoker.metadata } returns digisosSokerMetadata
        every { digisosSoker.timestampSistOppdatert } returns digisosSokerTimestamp
        every { sak.digisosSoker } returns digisosSoker
    } else {
        every { sak.digisosSoker } returns null
    }

    return sak
}

/** Get all krav of a specific type from a SoknadResponse. */
internal inline fun <reified T : Krav> SoknadResponse.kravOf(): List<T> = soknad.krav.filterIsInstance<T>()

/** Get all events of a specific type from a SoknadResponse. */
internal inline fun <reified T : SoknadHendelse> SoknadResponse.eventsOf(): List<T> = hendelser.filterIsInstance<T>()
