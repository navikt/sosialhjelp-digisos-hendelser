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
import no.nav.sosialhjelp.fiks.app.exceptions.NorgException
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.navenhet.NavEnhet
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class TildeltNavKontorTest {
    private val clientProperties: ClientProperties = mockk(relaxed = true)
    private val innsynService: InnsynService = mockk()
    private val vedleggService: VedleggService = mockk()
    private val norgClient: NorgClient = mockk()

    private val service = EventService(clientProperties, innsynService, vedleggService, norgClient)

    private val mockDigisosSak: DigisosSak = mockk()
    private val mockJsonSoknad: JsonSoknad = mockk()

    private val soknadsmottaker = "The Office"
    private val enhetsnr = "2317"

    private val mockNavEnhet: NavEnhet = mockk()
    private val enhetNavn = "Nav Holmenkollen"

    private val mockNavEnhet2: NavEnhet = mockk()
    private val enhetNavn2 = "Nav Longyearbyen"

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
    fun `tildeltNavKontor skal hente navenhets navn fra Norg`() =
        runTest(timeout = 5.seconds) {
            coEvery { norgClient.hentNavEnhet(NAVKONTOR) } returns mockNavEnhet
            every { mockNavEnhet.navn } returns enhetNavn
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            TILDELT_NAV_KONTOR.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(model.saker).hasSize(0)
            assertThat(model.historikk).hasSize(3)

            assertThat(model.historikk.last().hendelseType).isEqualTo(HendelseTekstType.SOKNAD_VIDERESENDT_MED_NORG_ENHET)
            assertThat(model.historikk.last().tekstArgument).isEqualTo(enhetNavn)
        }

    @Test
    fun `tildeltNavKontor skal gi generell melding hvis NorgClient kaster NorgException`() =
        runTest(timeout = 5.seconds) {
            coEvery { norgClient.hentNavEnhet(NAVKONTOR) } throws NorgException("noe feilet", null)
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            TILDELT_NAV_KONTOR.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(model.saker).hasSize(0)
            assertThat(model.historikk).hasSize(3)

            assertThat(model.historikk.last().hendelseType).isEqualTo(HendelseTekstType.SOKNAD_VIDERESENDT_UTEN_NORG_ENHET)
            assertThat(model.historikk.last().tekstArgument).isNull()
        }

    @Test
    fun `tildeltNavKontor til samme navKontor som soknad ble sendt til - gir ingen hendelse`() =
        runTest(timeout = 5.seconds) {
            every { mockJsonSoknad.mottaker.enhetsnummer } returns NAVKONTOR
            coEvery { norgClient.hentNavEnhet(NAVKONTOR) } returns mockNavEnhet
            every { mockNavEnhet.navn } returns enhetNavn
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            TILDELT_NAV_KONTOR.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(model.saker).hasSize(0)
            assertThat(model.historikk).hasSize(2)

            assertThat(model.historikk.last().hendelseType).isEqualTo(HendelseTekstType.SOKNAD_MOTTATT_MED_KOMMUNENAVN)
        }

    @Test
    fun `flere identiske tildeltNavKontor-hendelser skal kun gi en hendelse i historikk`() =
        runTest(timeout = 5.seconds) {
            coEvery { norgClient.hentNavEnhet(NAVKONTOR) } returns mockNavEnhet
            every { mockNavEnhet.navn } returns enhetNavn
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            TILDELT_NAV_KONTOR.withHendelsestidspunkt(tidspunkt_2),
                            TILDELT_NAV_KONTOR.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(model.saker).hasSize(0)
            assertThat(model.historikk).hasSize(3)

            assertThat(model.historikk.last().tekstArgument).isEqualTo(enhetNavn)
        }

    @Test
    fun `tildeltNavKontor til ulike kontor gir like mange hendelser`() =
        runTest(timeout = 5.seconds) {
            coEvery { norgClient.hentNavEnhet(NAVKONTOR) } returns mockNavEnhet
            coEvery { norgClient.hentNavEnhet(NAVKONTOR2) } returns mockNavEnhet2
            every { mockNavEnhet.navn } returns enhetNavn
            every { mockNavEnhet2.navn } returns enhetNavn2
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            TILDELT_NAV_KONTOR.withHendelsestidspunkt(tidspunkt_2),
                            TILDELT_NAV_KONTOR_2.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(model.saker).hasSize(0)
            assertThat(model.historikk).hasSize(4)

            assertThat(model.historikk[2].tekstArgument).isEqualTo(enhetNavn)
            assertThat(model.historikk[3].tekstArgument).isEqualTo(enhetNavn2)
        }

    @Test
    fun `forste gang en papirSoknad faar tildeltNavKontor - hendelseType er SOKNAD_VIDERESENDT_PAPIRSOKNAD_MED_NORG_ENHET`() =
        runTest(timeout = 5.seconds) {
            coEvery { norgClient.hentNavEnhet(NAVKONTOR) } returns mockNavEnhet
            every { mockNavEnhet.navn } returns enhetNavn
            every { mockDigisosSak.originalSoknadNAV } returns null
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            TILDELT_NAV_KONTOR.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(model.saker).hasSize(0)
            assertThat(model.historikk).hasSize(2)

            assertThat(model.historikk[0].hendelseType).isEqualTo(HendelseTekstType.SOKNAD_MOTTATT_UTEN_KOMMUNENAVN)
            assertThat(model.historikk[1].hendelseType).isEqualTo(HendelseTekstType.SOKNAD_VIDERESENDT_PAPIRSOKNAD_MED_NORG_ENHET)
            assertThat(model.historikk[1].tekstArgument).isEqualTo(enhetNavn)
        }

    @Test
    fun `andre gang en papirSoknad faar tildeltNavKontor skal hendelsen vise videresendt`() =
        runTest(timeout = 5.seconds) {
            coEvery { norgClient.hentNavEnhet(NAVKONTOR) } returns mockNavEnhet
            coEvery { norgClient.hentNavEnhet(NAVKONTOR2) } returns mockNavEnhet2
            every { mockNavEnhet.navn } returns enhetNavn
            every { mockNavEnhet2.navn } returns enhetNavn2
            every { mockDigisosSak.originalSoknadNAV } returns null
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            TILDELT_NAV_KONTOR.withHendelsestidspunkt(tidspunkt_2),
                            TILDELT_NAV_KONTOR_2.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(model.saker).hasSize(0)
            assertThat(model.historikk).hasSize(3)

            assertThat(model.historikk[0].hendelseType).isEqualTo(HendelseTekstType.SOKNAD_MOTTATT_UTEN_KOMMUNENAVN)
            assertThat(model.historikk[2].hendelseType).isEqualTo(HendelseTekstType.SOKNAD_VIDERESENDT_MED_NORG_ENHET)
            assertThat(model.historikk[2].tekstArgument).isEqualTo(enhetNavn2)
        }

    @Test
    fun `forste gang papirSoknad - NorgException - hendelseType er SOKNAD_VIDERESENDT_PAPIRSOKNAD_UTEN_NORG_ENHET`() =
        runTest(timeout = 5.seconds) {
            coEvery { norgClient.hentNavEnhet(NAVKONTOR) } throws NorgException("noe feilet", null)
            every { mockDigisosSak.originalSoknadNAV } returns null
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            TILDELT_NAV_KONTOR.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(model.saker).hasSize(0)
            assertThat(model.historikk).hasSize(2)

            assertThat(model.historikk[0].hendelseType).isEqualTo(HendelseTekstType.SOKNAD_MOTTATT_UTEN_KOMMUNENAVN)
            assertThat(model.historikk[1].hendelseType).isEqualTo(HendelseTekstType.SOKNAD_VIDERESENDT_PAPIRSOKNAD_UTEN_NORG_ENHET)
            assertThat(model.historikk[1].tekstArgument).isNull()
        }
}
