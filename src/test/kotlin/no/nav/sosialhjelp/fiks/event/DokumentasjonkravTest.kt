package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.KravEndret
import no.nav.sosialhjelp.fiks.domain.KravType
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class DokumentasjonkravTest {
    private val innsynService: InnsynService = mockk()
    private val vedleggService: VedleggService = mockk(relaxed = true)
    private val norgClient: NorgClient = mockk()

    private val service = TestEventService.build(innsynService, vedleggService, norgClient)

    @BeforeEach
    fun init() {
        clearAllMocks()
        resetHendelser()
    }

    @Test
    fun `dokumentasjonkrav legges til`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            UTBETALING.withHendelsestidspunkt(tidspunkt_1),
                            DOKUMENTASJONKRAV_OPPFYLT.withHendelsestidspunkt(tidspunkt_2),
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            val dokkrav = response.kravOf<Krav.Dokumentasjonkrav>()
            assertThat(dokkrav).hasSize(1)
            assertThat(dokkrav[0].referanse).isEqualTo(DOKUMENTASJONKRAV_REF_1)
            assertThat(dokkrav[0].status).isEqualTo(Oppgavestatus.OPPFYLT)
            assertThat(dokkrav[0].utbetalingsReferanser).contains(UTBETALING_REF_1)
        }

    @Test
    fun `dokumentasjonkrav upsert - oppdaterer eksisterende`() =
        runTest(timeout = 5.seconds) {
            val dokkravOppdatert =
                no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse
                    .JsonDokumentasjonkrav()
                    .withType(JsonHendelse.Type.DOKUMENTASJONKRAV)
                    .withDokumentasjonkravreferanse(DOKUMENTASJONKRAV_REF_1)
                    .withUtbetalingsreferanse(listOf(UTBETALING_REF_1))
                    .withBeskrivelse("oppdatert")
                    .withStatus(no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumentasjonkrav.Status.RELEVANT)
                    .also { it.withHendelsestidspunkt(tidspunkt_3) }

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            UTBETALING.withHendelsestidspunkt(tidspunkt_1),
                            DOKUMENTASJONKRAV_OPPFYLT.withHendelsestidspunkt(tidspunkt_2),
                            dokkravOppdatert,
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            val dokkrav = response.kravOf<Krav.Dokumentasjonkrav>()
            assertThat(dokkrav).hasSize(1) // still one, not two
            assertThat(dokkrav[0].status).isEqualTo(Oppgavestatus.RELEVANT)
            assertThat(dokkrav[0].beskrivelse).isEqualTo("oppdatert")
        }

    @Test
    fun `KravEndret hendelse emittet for dokumentasjonkrav`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            DOKUMENTASJONKRAV_OPPFYLT.withHendelsestidspunkt(tidspunkt_1),
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            val kravEvents = response.eventsOf<KravEndret>()
            assertThat(kravEvents).hasSize(1)
            assertThat(kravEvents[0].kravReferanse).isEqualTo(DOKUMENTASJONKRAV_REF_1)
            assertThat(kravEvents[0].kravType).isEqualTo(KravType.DOKUMENTASJONKRAV)
        }
}
