package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSaksStatus
import no.nav.sosialhjelp.fiks.domain.SaksStatus
import no.nav.sosialhjelp.fiks.domain.SaksStatusEndret
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class SaksStatusTest {
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
    fun `ny sak UNDER_BEHANDLING med tittel - oppretter sak`() =
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
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].saksStatus).isEqualTo(SaksStatus.UNDER_BEHANDLING)
            assertThat(response.soknad.saker[0].tittel).isEqualTo(TITTEL_1)
            assertThat(response.soknad.saker[0].referanse).isEqualTo(REFERANSE_1)

            val sakHendelser = response.eventsOf<SaksStatusEndret>()
            assertThat(sakHendelser).hasSize(1)
            assertThat(sakHendelser[0].saksReferanse).isEqualTo(REFERANSE_1)
            assertThat(sakHendelser[0].tittel).isEqualTo(TITTEL_1)
            assertThat(sakHendelser[0].erNyeSak).isTrue()
        }

    @Test
    fun `ny sak UNDER_BEHANDLING uten tittel - oppretter sak`() =
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
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].saksStatus).isEqualTo(SaksStatus.UNDER_BEHANDLING)
            assertThat(response.soknad.saker[0].tittel).isNull()

            val sakHendelser = response.eventsOf<SaksStatusEndret>()
            assertThat(sakHendelser).hasSize(1)
            assertThat(sakHendelser[0].tittel).isNull()
            assertThat(sakHendelser[0].erNyeSak).isTrue()
        }

    @Test
    fun `ny sak BEHANDLES_IKKE med tittel - oppretter sak`() =
        runTest(timeout = 5.seconds) {
            val sakBehandlesIkkeMedTittel =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.BEHANDLES_IKKE)
                    .withTittel(TITTEL_1)
                    .withReferanse(REFERANSE_1)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            sakBehandlesIkkeMedTittel.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].saksStatus).isEqualTo(SaksStatus.BEHANDLES_IKKE)
            assertThat(response.soknad.saker[0].tittel).isEqualTo(TITTEL_1)

            val sakHendelse = response.eventsOf<SaksStatusEndret>().first()
            assertThat(sakHendelse.status).isEqualTo(SaksStatus.BEHANDLES_IKKE)
            assertThat(sakHendelse.tittel).isEqualTo(TITTEL_1)
        }

    @Test
    fun `ny sak BEHANDLES_IKKE uten tittel - oppretter sak`() =
        runTest(timeout = 5.seconds) {
            val sakBehandlesIkkeUtenTittel =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.BEHANDLES_IKKE)
                    .withReferanse(REFERANSE_1)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            sakBehandlesIkkeUtenTittel.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].saksStatus).isEqualTo(SaksStatus.BEHANDLES_IKKE)

            val sakHendelse = response.eventsOf<SaksStatusEndret>().first()
            assertThat(sakHendelse.status).isEqualTo(SaksStatus.BEHANDLES_IKKE)
            assertThat(sakHendelse.tittel).isNull()
        }

    @Test
    fun `ny sak IKKE_INNSYN med tittel - oppretter sak`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_IKKEINNSYN.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].saksStatus).isEqualTo(SaksStatus.IKKE_INNSYN)

            val sakHendelse = response.eventsOf<SaksStatusEndret>().first()
            assertThat(sakHendelse.status).isEqualTo(SaksStatus.IKKE_INNSYN)
            assertThat(sakHendelse.tittel).isEqualTo(TITTEL_1)
        }

    @Test
    fun `ny sak uten status og tittel - defaults til UNDER_BEHANDLING`() =
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
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].saksStatus).isEqualTo(SaksStatus.UNDER_BEHANDLING)
            assertThat(response.soknad.saker[0].tittel).isNull()
        }

    @Test
    fun `oppdater saksStatus fra UNDER_BEHANDLING til BEHANDLES_IKKE med tittel`() =
        runTest(timeout = 5.seconds) {
            val sakBehandlesIkke =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.BEHANDLES_IKKE)
                    .withTittel(TITTEL_1)
                    .withReferanse(REFERANSE_1)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            sakBehandlesIkke.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].saksStatus).isEqualTo(SaksStatus.BEHANDLES_IKKE)

            val sakHendelser = response.eventsOf<SaksStatusEndret>().filter { it.saksReferanse == REFERANSE_1 }
            assertThat(sakHendelser).hasSize(2)
            assertThat(sakHendelser[0].erNyeSak).isTrue()
            assertThat(sakHendelser[1].status).isEqualTo(SaksStatus.BEHANDLES_IKKE)
            assertThat(sakHendelser[1].tittel).isEqualTo(TITTEL_1)
        }

    @Test
    fun `oppdater saksStatus fra UNDER_BEHANDLING til BEHANDLES_IKKE uten tittel`() =
        runTest(timeout = 5.seconds) {
            val sakBehandlesIkkeUtenTittel =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.BEHANDLES_IKKE)
                    .withReferanse(REFERANSE_1)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            sakBehandlesIkkeUtenTittel.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            val sakHendelser = response.eventsOf<SaksStatusEndret>().filter { it.saksReferanse == REFERANSE_1 }
            assertThat(sakHendelser).hasSize(2)
            assertThat(sakHendelser[1].status).isEqualTo(SaksStatus.BEHANDLES_IKKE)
            assertThat(sakHendelser[1].tittel).isNull()
        }

    @Test
    fun `oppdater saksStatus fra IKKE_INNSYN til UNDER_BEHANDLING - ingen ny SaksStatusEndret-hendelse`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_IKKEINNSYN.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].saksStatus).isEqualTo(SaksStatus.UNDER_BEHANDLING)

            // Only one SaksStatusEndret emitted (the initial IKKE_INNSYN → no new event for transition back)
            val sakHendelser = response.eventsOf<SaksStatusEndret>().filter { it.saksReferanse == REFERANSE_1 }
            assertThat(sakHendelser).hasSize(1)
            assertThat(sakHendelser[0].status).isEqualTo(SaksStatus.IKKE_INNSYN)
        }

    @Test
    fun `oppdater tittel - tittel endres`() =
        runTest(timeout = 5.seconds) {
            val sakMedOppdatertTittel =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.UNDER_BEHANDLING)
                    .withTittel(TITTEL_2)
                    .withReferanse(REFERANSE_1)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            sakMedOppdatertTittel.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(1)
            assertThat(response.soknad.saker[0].tittel).isEqualTo(TITTEL_2)
        }

    @Test
    fun `to ulike saker opprettes for to ulike referanser`() =
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
                            SAK2_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.saker).hasSize(2)
            assertThat(response.soknad.saker[0].referanse).isEqualTo(REFERANSE_1)
            assertThat(response.soknad.saker[1].referanse).isEqualTo(REFERANSE_2)
        }
}
