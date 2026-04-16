package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSaksStatus
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.SaksStatus
import no.nav.sosialhjelp.fiks.navenhet.NavEnhet
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class SaksStatusTest {
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
    fun `ny sak UNDER_BEHANDLING med tittel - oppretter sak og legger til SAK_UNDER_BEHANDLING_MED_TITTEL`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].saksStatus).isEqualTo(SaksStatus.UNDER_BEHANDLING)
            assertThat(model.saker[0].tittel).isEqualTo(TITTEL_1)
            assertThat(model.saker[0].referanse).isEqualTo(REFERANSE_1)

            val sakHendelse = model.historikk.last()
            assertThat(sakHendelse.hendelseType).isEqualTo(HendelseTekstType.SAK_UNDER_BEHANDLING_MED_TITTEL)
            assertThat(sakHendelse.tekstArgument).isEqualTo(TITTEL_1)
            assertThat(sakHendelse.saksReferanse).isEqualTo(REFERANSE_1)
        }

    @Test
    fun `ny sak UNDER_BEHANDLING uten tittel - oppretter sak og legger til SAK_UNDER_BEHANDLING_UTEN_TITTEL`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].saksStatus).isEqualTo(SaksStatus.UNDER_BEHANDLING)
            assertThat(model.saker[0].tittel).isNull()

            val sakHendelse = model.historikk.last()
            assertThat(sakHendelse.hendelseType).isEqualTo(HendelseTekstType.SAK_UNDER_BEHANDLING_UTEN_TITTEL)
            assertThat(sakHendelse.tekstArgument).isNull()
        }

    @Test
    fun `ny sak BEHANDLES_IKKE med tittel - oppretter sak og legger til SAK_KAN_IKKE_VISE_STATUS_MED_TITTEL`() =
        runTest(timeout = 5.seconds) {
            val sakBehandlesIkkeMedTittel =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.BEHANDLES_IKKE)
                    .withTittel(TITTEL_1)
                    .withReferanse(REFERANSE_1)

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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].saksStatus).isEqualTo(SaksStatus.BEHANDLES_IKKE)
            assertThat(model.saker[0].tittel).isEqualTo(TITTEL_1)

            val sakHendelse = model.historikk.last()
            assertThat(sakHendelse.hendelseType).isEqualTo(HendelseTekstType.SAK_KAN_IKKE_VISE_STATUS_MED_TITTEL)
            assertThat(sakHendelse.tekstArgument).isEqualTo(TITTEL_1)
        }

    @Test
    fun `ny sak BEHANDLES_IKKE uten tittel - oppretter sak og legger til SAK_KAN_IKKE_VISE_STATUS_UTEN_TITTEL`() =
        runTest(timeout = 5.seconds) {
            val sakBehandlesIkkeUtenTittel =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.BEHANDLES_IKKE)
                    .withReferanse(REFERANSE_1)

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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].saksStatus).isEqualTo(SaksStatus.BEHANDLES_IKKE)

            val sakHendelse = model.historikk.last()
            assertThat(sakHendelse.hendelseType).isEqualTo(HendelseTekstType.SAK_KAN_IKKE_VISE_STATUS_UTEN_TITTEL)
            assertThat(sakHendelse.tekstArgument).isNull()
        }

    @Test
    fun `ny sak IKKE_INNSYN med tittel - oppretter sak og legger til SAK_KAN_IKKE_VISE_STATUS_MED_TITTEL`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].saksStatus).isEqualTo(SaksStatus.IKKE_INNSYN)

            val sakHendelse = model.historikk.last()
            assertThat(sakHendelse.hendelseType).isEqualTo(HendelseTekstType.SAK_KAN_IKKE_VISE_STATUS_MED_TITTEL)
            assertThat(sakHendelse.tekstArgument).isEqualTo(TITTEL_1)
        }

    @Test
    fun `ny sak uten status og tittel - defaults til UNDER_BEHANDLING og SAK_UNDER_BEHANDLING_UTEN_TITTEL`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].saksStatus).isEqualTo(SaksStatus.UNDER_BEHANDLING)
            assertThat(model.saker[0].tittel).isNull()

            assertThat(model.historikk.last().hendelseType).isEqualTo(HendelseTekstType.SAK_UNDER_BEHANDLING_UTEN_TITTEL)
        }

    @Test
    fun `oppdater saksStatus fra UNDER_BEHANDLING til BEHANDLES_IKKE med tittel - legger til SOKNAD_KAN_IKKE_VISE_STATUS_MED_TITTEL`() =
        runTest(timeout = 5.seconds) {
            val sakBehandlesIkke =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.BEHANDLES_IKKE)
                    .withTittel(TITTEL_1)
                    .withReferanse(REFERANSE_1)

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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].saksStatus).isEqualTo(SaksStatus.BEHANDLES_IKKE)

            // To sakshendelser: SAK_UNDER_BEHANDLING_MED_TITTEL + SOKNAD_KAN_IKKE_VISE_STATUS_MED_TITTEL
            val sakHendelser = model.historikk.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(sakHendelser).hasSize(2)
            assertThat(sakHendelser[0].hendelseType).isEqualTo(HendelseTekstType.SAK_UNDER_BEHANDLING_MED_TITTEL)
            assertThat(sakHendelser[1].hendelseType).isEqualTo(HendelseTekstType.SOKNAD_KAN_IKKE_VISE_STATUS_MED_TITTEL)
            assertThat(sakHendelser[1].tekstArgument).isEqualTo(TITTEL_1)
        }

    @Test
    fun `oppdater saksStatus fra UNDER_BEHANDLING til BEHANDLES_IKKE uten tittel - legger til SOKNAD_KAN_IKKE_VISE_STATUS_UTEN_TITTEL`() =
        runTest(timeout = 5.seconds) {
            val sakBehandlesIkkeUtenTittel =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.BEHANDLES_IKKE)
                    .withReferanse(REFERANSE_1)

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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            val sakHendelser = model.historikk.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(sakHendelser).hasSize(2)
            assertThat(sakHendelser[1].hendelseType).isEqualTo(HendelseTekstType.SOKNAD_KAN_IKKE_VISE_STATUS_UTEN_TITTEL)
            assertThat(sakHendelser[1].tekstArgument).isNull()
        }

    @Test
    fun `oppdater saksStatus fra IKKE_INNSYN til UNDER_BEHANDLING - ingen ny hendelse i historikk`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].saksStatus).isEqualTo(SaksStatus.UNDER_BEHANDLING)

            // Kun én sakshendelse fra oppretting av saken (IKKE_INNSYN) - ingen ekstra hendelse for overgangen tilbake
            val sakHendelser = model.historikk.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(sakHendelser).hasSize(1)
            assertThat(sakHendelser[0].hendelseType).isEqualTo(HendelseTekstType.SAK_KAN_IKKE_VISE_STATUS_MED_TITTEL)
        }

    @Test
    fun `oppdater tittel - tittel endres uten ny historikk-hendelse`() =
        runTest(timeout = 5.seconds) {
            val sakMedOppdatertTittel =
                JsonSaksStatus()
                    .withType(JsonHendelse.Type.SAKS_STATUS)
                    .withStatus(JsonSaksStatus.Status.UNDER_BEHANDLING)
                    .withTittel(TITTEL_2)
                    .withReferanse(REFERANSE_1)

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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(1)
            assertThat(model.saker[0].tittel).isEqualTo(TITTEL_2)

            // Ingen ny historikk-hendelse siden status ikke endret seg
            val sakHendelser = model.historikk.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(sakHendelser).hasSize(1)
        }

    @Test
    fun `to ulike saker opprettes for to ulike referanser`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.saker).hasSize(2)
            assertThat(model.saker[0].referanse).isEqualTo(REFERANSE_1)
            assertThat(model.saker[1].referanse).isEqualTo(REFERANSE_2)
        }
}
