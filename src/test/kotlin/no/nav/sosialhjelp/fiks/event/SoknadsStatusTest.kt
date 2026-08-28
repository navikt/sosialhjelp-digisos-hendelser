package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.domain.SoknadsStatusEndret
import no.nav.sosialhjelp.fiks.navenhet.NavEnhet
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class SoknadsStatusTest {
    private val innsynService: InnsynService = mockk()
    private val vedleggService: VedleggService = mockk(relaxed = true)
    private val norgClient: NorgClient = mockk()

    private val service = TestEventService.build(innsynService, vedleggService, norgClient)

    private val mockJsonSoknad: JsonSoknad = mockk()
    private val mockNavEnhet: NavEnhet = mockk()
    private val enhetsnr = "2317"
    private val soknadsmottaker = "The Office"

    @BeforeEach
    fun init() {
        clearAllMocks()
        every { mockJsonSoknad.mottaker?.navEnhetsnavn } returns soknadsmottaker
        every { mockJsonSoknad.mottaker?.enhetsnummer } returns enhetsnr
        every { mockNavEnhet.navn } returns soknadsmottaker
        coEvery { norgClient.hentNavEnhet(enhetsnr) } returns mockNavEnhet
        resetHendelser()
    }

    @Test
    fun `soknadsStatus MOTTATT med kommunenavn`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.MOTTATT)
            val hendelse = response.eventsOf<SoknadsStatusEndret>().first { it.status == SoknadsStatus.MOTTATT }
            assertThat(hendelse.mottakerNavn).isEqualTo(soknadsmottaker)
        }

    @Test
    fun `soknadsStatus MOTTATT uten kommunenavn naar mottaker er null`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            val soknadUtenMottaker = mockk<JsonSoknad>()
            every { soknadUtenMottaker.mottaker } returns null
            coEvery { innsynService.hentOriginalSoknad(any()) } returns soknadUtenMottaker
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.MOTTATT)
            val hendelse = response.eventsOf<SoknadsStatusEndret>().first { it.status == SoknadsStatus.MOTTATT }
            assertThat(hendelse.mottakerNavn).isNull()
        }

    @Test
    fun `soknadsStatus UNDER_BEHANDLING`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.UNDER_BEHANDLING)
        }

    @Test
    fun `soknadsStatus FERDIGBEHANDLET`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SOKNADS_STATUS_FERDIGBEHANDLET.withHendelsestidspunkt(tidspunkt_3),
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.FERDIGBEHANDLET)
            assertThat(response.soknad.avledetStatus).isEqualTo(SoknadsStatus.FERDIGBEHANDLET)
        }

    @Test
    fun `avledetStatus UNDER_BEHANDLING naar det finnes aktive saker`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            SOKNADS_STATUS_FERDIGBEHANDLET.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            // Folded status is FERDIGBEHANDLET
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.FERDIGBEHANDLET)
            // But avledetStatus is overridden because there is an active sak with no vedtak
            assertThat(response.soknad.avledetStatus).isEqualTo(SoknadsStatus.UNDER_BEHANDLING)
        }

    @Test
    fun `MOTTATT sorteres foer UNDER_BEHANDLING ved identisk tidspunkt`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1)
            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_1) // same timestamp
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            SOKNADS_STATUS_UNDERBEHANDLING,
                            SOKNADS_STATUS_MOTTATT,
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            // MOTTATT should be first in the event list
            val statusEvents = response.eventsOf<SoknadsStatusEndret>()
            assertThat(statusEvents.first().status).isEqualTo(SoknadsStatus.MOTTATT)
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.UNDER_BEHANDLING)
        }
}
