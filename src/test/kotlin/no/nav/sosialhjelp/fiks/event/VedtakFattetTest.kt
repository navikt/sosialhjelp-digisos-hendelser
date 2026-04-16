package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.digisossak.saksstatus.DEFAULT_SAK_TITTEL
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.UtfallVedtak
import no.nav.sosialhjelp.fiks.navenhet.NavEnhet
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class VedtakFattetTest {
    private val clientProperties: ClientProperties = mockk(relaxed = true)
    private val innsynService: InnsynService = mockk()
    private val vedleggService: VedleggService = mockk()
    private val norgClient: NorgClient = mockk()

    private val service = EventService(clientProperties, innsynService, vedleggService, norgClient)

    private val mockDigisosSak: DigisosSak = mockk()
    private val mockJsonSoknad: JsonSoknad = mockk()
    private val mockNavEnhet: NavEnhet = mockk()

    private val soknadsmottaker = "The Office"
    private val enhetsnr = "2317"

    @BeforeEach
    fun init() {
        clearAllMocks()
        every { mockDigisosSak.fiksDigisosId } returns "123"
        every { mockDigisosSak.digisosSoker?.metadata } returns "some id"
        every { mockDigisosSak.originalSoknadNAV?.metadata } returns "some other id"
        every { mockDigisosSak.originalSoknadNAV?.timestampSendt } returns tidspunkt_soknad
        every { mockDigisosSak.originalSoknadNAV?.navEksternRefId } returns null
        every { mockDigisosSak.originalSoknadNAV?.soknadDokument?.dokumentlagerDokumentId } returns null
        every { mockJsonSoknad.mottaker.navEnhetsnavn } returns soknadsmottaker
        every { mockJsonSoknad.mottaker.enhetsnummer } returns enhetsnr
        every { mockDigisosSak.ettersendtInfoNAV } returns null
        coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
        coEvery { norgClient.hentNavEnhet(enhetsnr) } returns mockNavEnhet

        resetHendelser()
    }

    @Test
    fun `vedtakFattet ETTER saksStatus - sak ferdigbehandlet med tittel`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].tittel).isEqualTo(TITTEL_1)
            assertThat(model.saker[0].vedtak).hasSize(1)

            val vedtakHendelse =
                model.historikk.last { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakHendelse.hendelseType).isEqualTo(HendelseTekstType.SAK_FERDIGBEHANDLET_MED_TITTEL)
            assertThat(vedtakHendelse.tekstArgument).isEqualTo(TITTEL_1)
            assertThat(vedtakHendelse.saksReferanse).isEqualTo(REFERANSE_1)
        }

    @Test
    fun `vedtakFattet UTEN saksStatus - oppretter sak med DEFAULT_SAK_TITTEL`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].referanse).isEqualTo(REFERANSE_1)
            assertThat(model.saker[0].tittel).isEqualTo(DEFAULT_SAK_TITTEL)
            assertThat(model.saker[0].vedtak).hasSize(1)
        }

    @Test
    fun `vedtakFattet ETTER saksStatus uten tittel - historikk SAK_FERDIGBEHANDLET_UTEN_TITTEL`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].tittel).isNull()

            val vedtakHendelse = model.historikk.last { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakHendelse.hendelseType).isEqualTo(HendelseTekstType.SAK_FERDIGBEHANDLET_UTEN_TITTEL)
            assertThat(vedtakHendelse.tekstArgument).isNull()
        }

    @Test
    fun `vedtakFattet med INNVILGET utfall - utfall settes korrekt`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker[0].vedtak).hasSize(1)
            assertThat(model.saker[0].vedtak[0].utfall).isEqualTo(UtfallVedtak.INNVILGET)
            assertThat(model.saker[0].vedtak[0].id).isEqualTo(DOKUMENTLAGERID_1)
        }

    @Test
    fun `vedtakFattet med AVSLATT utfall - utfall settes korrekt`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker[0].vedtak[0].utfall).isEqualTo(UtfallVedtak.AVSLATT)
            assertThat(model.saker[0].vedtak[0].id).isEqualTo(DOKUMENTLAGERID_2)
        }

    @Test
    fun `vedtakFattet uten utfall - utfall er null`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker[0].vedtak[0].utfall).isNull()
        }

    @Test
    fun `vedtakFattet med SvarUt referanse - id er SVARUTID`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].vedtak).hasSize(1)
            assertThat(model.saker[0].vedtak[0].id).isEqualTo(SVARUTID)
        }

    @Test
    fun `vedtakFattet historikk inneholder url med VIS_BREVET_LENKETEKST`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            val vedtakHendelse = model.historikk.last { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakHendelse.url).isNotNull()
            assertThat(vedtakHendelse.url!!.linkTekst).isEqualTo(HendelseTekstType.VIS_BREVET_LENKETEKST)
            assertThat(vedtakHendelse.url!!.link).isNotNull()
        }

    @Test
    fun `to vedtakFattet for same sak - begge vedtak legges til`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].vedtak).hasSize(2)
            assertThat(model.saker[0].vedtak[0].utfall).isEqualTo(UtfallVedtak.INNVILGET)
            assertThat(model.saker[0].vedtak[1].utfall).isEqualTo(UtfallVedtak.AVSLATT)

            val vedtakHendelser = model.historikk.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(vedtakHendelser).hasSize(3) // SAK_UNDER_BEHANDLING_MED_TITTEL + to SAK_FERDIGBEHANDLET
        }

    @Test
    fun `vedtakFattet legger til vedtaksfilUrl pa vedtaket`() =
        runTest(timeout = 5.seconds) {
            every { clientProperties.fiksDokumentlagerEndpointUrl } returns "https://fiks.no"

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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            val vedtak = model.saker[0].vedtak[0]
            assertThat(vedtak.vedtaksFilUrl).contains(DOKUMENTLAGERID_1)
        }
}
