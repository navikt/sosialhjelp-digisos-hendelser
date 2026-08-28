package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sosialhjelp.fiks.app.exceptions.NorgException
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.domain.TildeltNavKontor
import no.nav.sosialhjelp.fiks.navenhet.NavEnhet
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class TildeltNavKontorTest {
    private val innsynService: InnsynService = mockk()
    private val vedleggService: VedleggService = mockk()
    private val norgClient: NorgClient = mockk()

    private val service = TestEventService.build(innsynService, vedleggService, norgClient)

    private val mockNavEnhet: NavEnhet = mockk()
    private val enhetNavn = "Nav Holmenkollen"

    private val mockNavEnhet2: NavEnhet = mockk()
    private val enhetNavn2 = "Nav Longyearbyen"

    @BeforeEach
    fun init() {
        clearAllMocks()
        resetHendelser()
    }

    @Test
    fun `tildeltNavKontor skal hente navenhets navn fra Norg`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
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
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(response.soknad.saker).hasSize(0)

            val tildeltHendelser = response.eventsOf<TildeltNavKontor>()
            assertThat(tildeltHendelser).hasSize(1)
            assertThat(tildeltHendelser[0].tilEnhet.navn).isEqualTo(enhetNavn)
            assertThat(tildeltHendelser[0].enhetNavnOppslagFeilet).isFalse()
        }

    @Test
    fun `tildeltNavKontor skal gi feilet oppslagflagg hvis NorgClient kaster NorgException`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
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
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(response.soknad.saker).hasSize(0)

            val tildeltHendelser = response.eventsOf<TildeltNavKontor>()
            assertThat(tildeltHendelser).hasSize(1)
            assertThat(tildeltHendelser[0].enhetNavnOppslagFeilet).isTrue()
        }

    @Test
    fun `tildeltNavKontor til samme navKontor som soknad ble sendt til - ingen TildeltNavKontor-hendelse`() =
        runTest(timeout = 5.seconds) {
            // mockDigisosSak has enhetsnr "2317" as mottaker via originalSoknad mottaker
            // NAVKONTOR is "1337" — but we need to simulate same enhet as mottaker
            // Use mockDigisosSak and ensure mottaker enhetsnummer == NAVKONTOR
            val digisosSak = mockDigisosSak()
            coEvery { norgClient.hentNavEnhet(NAVKONTOR) } returns mockNavEnhet
            every { mockNavEnhet.navn } returns enhetNavn

            // Provide a JsonSoknad where mottaker.enhetsnummer == NAVKONTOR so tildeling is ignored
            val mockJsonSoknadSameEnhet = mockk<no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad>()
            every { mockJsonSoknadSameEnhet.mottaker.navEnhetsnavn } returns enhetNavn
            every { mockJsonSoknadSameEnhet.mottaker.enhetsnummer } returns NAVKONTOR
            coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknadSameEnhet

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

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(response.soknad.saker).hasSize(0)

            // No TildeltNavKontor event since it's the same as original mottaker
            val tildeltHendelser = response.eventsOf<TildeltNavKontor>()
            assertThat(tildeltHendelser).hasSize(0)
        }

    @Test
    fun `flere identiske tildeltNavKontor-hendelser skal kun gi en TildeltNavKontor-hendelse`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
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
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(response.soknad.saker).hasSize(0)

            val tildeltHendelser = response.eventsOf<TildeltNavKontor>()
            assertThat(tildeltHendelser).hasSize(1)
            assertThat(tildeltHendelser[0].tilEnhet.navn).isEqualTo(enhetNavn)
        }

    @Test
    fun `tildeltNavKontor til ulike kontor gir like mange hendelser`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
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
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(response.soknad.saker).hasSize(0)

            val tildeltHendelser = response.eventsOf<TildeltNavKontor>()
            assertThat(tildeltHendelser).hasSize(2)
            assertThat(tildeltHendelser[0].tilEnhet.navn).isEqualTo(enhetNavn)
            assertThat(tildeltHendelser[1].tilEnhet.navn).isEqualTo(enhetNavn2)
        }

    @Test
    fun `forste gang en papirSoknad faar tildeltNavKontor - erForsteTildeling er true`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak(timestampSendt = null) // papirSoknad = no originalSoknadNAV
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
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(response.soknad.erPapirsoknad).isTrue()

            val tildeltHendelser = response.eventsOf<TildeltNavKontor>()
            assertThat(tildeltHendelser).hasSize(1)
            assertThat(tildeltHendelser[0].erForsteTildeling).isTrue()
            assertThat(tildeltHendelser[0].tilEnhet.navn).isEqualTo(enhetNavn)
        }

    @Test
    fun `andre gang en papirSoknad faar tildeltNavKontor - erForsteTildeling er false`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak(timestampSendt = null) // papirSoknad
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
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.erPapirsoknad).isTrue()

            val tildeltHendelser = response.eventsOf<TildeltNavKontor>()
            assertThat(tildeltHendelser).hasSize(2)
            assertThat(tildeltHendelser[0].erForsteTildeling).isTrue()
            assertThat(tildeltHendelser[1].erForsteTildeling).isFalse()
            assertThat(tildeltHendelser[1].tilEnhet.navn).isEqualTo(enhetNavn2)
        }

    @Test
    fun `forste gang papirSoknad - NorgException - enhetNavnOppslagFeilet er true`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak(timestampSendt = null) // papirSoknad
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
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.erPapirsoknad).isTrue()

            val tildeltHendelser = response.eventsOf<TildeltNavKontor>()
            assertThat(tildeltHendelser).hasSize(1)
            assertThat(tildeltHendelser[0].erForsteTildeling).isTrue()
            assertThat(tildeltHendelser[0].enhetNavnOppslagFeilet).isTrue()
        }
}
