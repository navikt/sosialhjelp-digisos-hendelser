package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sosialhjelp.fiks.domain.DokumentRef
import no.nav.sosialhjelp.fiks.domain.UtfallVedtak
import no.nav.sosialhjelp.fiks.domain.VedtakFattet
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class VedtakFattetTest {
    private val innsynService: InnsynService = mockk()
    private val vedleggService: VedleggService = mockk()
    private val norgClient: NorgClient = mockk()

    private val service = TestEventService.build(innsynService, vedleggService, norgClient)

    @BeforeEach
    fun init() {
        clearAllMocks()
        resetHendelser()
    }

    @Test
    fun `vedtakFattet ETTER saksStatus - sak ferdigbehandlet med tittel`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_3),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_4),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].tittel).isEqualTo(TITTEL_1)

            val vedtakForSak = response.soknad.vedtak.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakForSak).hasSize(1)

            val vedtakHendelse = response.eventsOf<VedtakFattet>().first { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakHendelse.saksTittel).isEqualTo(TITTEL_1)
            assertThat(vedtakHendelse.saksReferanse).isEqualTo(REFERANSE_1)
        }

    @Test
    fun `vedtakFattet UTEN saksStatus - oppretter sak med null tittel`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].referanse).isEqualTo(REFERANSE_1)
            val vedtakForSak = response.soknad.vedtak.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakForSak).hasSize(1)
        }

    @Test
    fun `vedtakFattet ETTER saksStatus uten tittel - VedtakFattet hendelse har null saksTittel`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_UTEN_SAKS_STATUS_ELLER_TITTEL.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].tittel).isNull()

            val vedtakHendelse = response.eventsOf<VedtakFattet>().first { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakHendelse.saksTittel).isNull()
        }

    @Test
    fun `vedtakFattet med INNVILGET utfall - utfall settes korrekt`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            val vedtakForSak = response.soknad.vedtak.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakForSak).hasSize(1)
            assertThat(vedtakForSak[0].utfall).isEqualTo(UtfallVedtak.INNVILGET)
            val ref = vedtakForSak[0].referanse
            assertThat(ref).isInstanceOf(DokumentRef.Dokumentlager::class.java)
            assertThat((ref as DokumentRef.Dokumentlager).id).isEqualTo(DOKUMENTLAGERID_1)
        }

    @Test
    fun `vedtakFattet med AVSLATT utfall - utfall settes korrekt`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_AVSLATT.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            val vedtakForSak = response.soknad.vedtak.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakForSak[0].utfall).isEqualTo(UtfallVedtak.AVSLATT)
            val ref = vedtakForSak[0].referanse
            assertThat(ref).isInstanceOf(DokumentRef.Dokumentlager::class.java)
            assertThat((ref as DokumentRef.Dokumentlager).id).isEqualTo(DOKUMENTLAGERID_2)
        }

    @Test
    fun `vedtakFattet uten utfall - utfall er null`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_UTEN_UTFALL.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            val vedtakForSak = response.soknad.vedtak.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakForSak[0].utfall).isNull()
        }

    @Test
    fun `vedtakFattet med SvarUt referanse - referanse er SvarUt`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK2_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK2_VEDTAK_FATTET.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            val vedtakForSak = response.soknad.vedtak.filter { it.saksReferanse == REFERANSE_2 }
            assertThat(vedtakForSak).hasSize(1)
            val ref = vedtakForSak[0].referanse
            assertThat(ref).isInstanceOf(DokumentRef.SvarUt::class.java)
            assertThat((ref as DokumentRef.SvarUt).id).isEqualTo(SVARUTID)
        }

    @Test
    fun `vedtakFattet VedtakFattet-hendelse har dokumentRef`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            val vedtakHendelse = response.eventsOf<VedtakFattet>().first { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakHendelse.vedtakRef).isInstanceOf(DokumentRef.Dokumentlager::class.java)
            assertThat((vedtakHendelse.vedtakRef as DokumentRef.Dokumentlager).id).isEqualTo(DOKUMENTLAGERID_1)
        }

    @Test
    fun `to vedtakFattet for same sak - begge vedtak legges til`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            SAK1_VEDTAK_FATTET_AVSLATT.withHendelsestidspunkt(tidspunkt_4),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            val vedtakForSak = response.soknad.vedtak.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakForSak).hasSize(2)
            assertThat(vedtakForSak[0].utfall).isEqualTo(UtfallVedtak.INNVILGET)
            assertThat(vedtakForSak[1].utfall).isEqualTo(UtfallVedtak.AVSLATT)

            val vedtakHendelser = response.eventsOf<VedtakFattet>().filter { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakHendelser).hasSize(2)
        }
}
