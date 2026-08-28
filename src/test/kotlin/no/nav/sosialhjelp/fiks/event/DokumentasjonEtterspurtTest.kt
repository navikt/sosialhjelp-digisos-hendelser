package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sosialhjelp.fiks.domain.DokumentasjonEtterspurt
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.OppgaverTrukket
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class DokumentasjonEtterspurtTest {
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
    fun `dokumentasjonEtterspurt med dokumenter og forvaltningsbrev`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            DOKUMENTASJONETTERSPURT.withHendelsestidspunkt(tidspunkt_1),
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            val krav = response.kravOf<Krav.DokumentasjonEtterspurt>()
            assertThat(krav).hasSize(1)
            assertThat(krav[0].tittel).isEqualTo(DOKUMENTTYPE)
            assertThat(krav[0].beskrivelse).isEqualTo(TILLEGGSINFO)

            val hendelse = response.eventsOf<DokumentasjonEtterspurt>()
            assertThat(hendelse).hasSize(1)
            assertThat(hendelse[0].harDokumenter).isTrue()
            assertThat(hendelse[0].forvaltningsbrevRef).isNotNull()
        }

    @Test
    fun `dokumentasjonEtterspurt uten forvaltningsbrev - ingen hendelse emittet`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            DOKUMENTASJONETTERSPURT_UTEN_FORVALTNINGSBREV.withHendelsestidspunkt(tidspunkt_1),
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            val krav = response.kravOf<Krav.DokumentasjonEtterspurt>()
            assertThat(krav).hasSize(1)
            // No DokumentasjonEtterspurt event because there's no forvaltningsbrev
            assertThat(response.eventsOf<DokumentasjonEtterspurt>()).isEmpty()
        }

    @Test
    fun `dokumentasjonEtterspurt med tom dokumentliste erstatter eksisterende oppgaver`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_1)
            DOKUMENTASJONETTERSPURT.withHendelsestidspunkt(tidspunkt_2)
            DOKUMENTASJONETTERSPURT_TOM_DOKUMENT_LISTE.withHendelsestidspunkt(tidspunkt_3)
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            SOKNADS_STATUS_UNDERBEHANDLING,
                            DOKUMENTASJONETTERSPURT,
                            DOKUMENTASJONETTERSPURT_TOM_DOKUMENT_LISTE,
                        ) as MutableList<JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            // All DokumentasjonEtterspurt krav should be gone
            assertThat(response.kravOf<Krav.DokumentasjonEtterspurt>()).isEmpty()
            // OppgaverTrukket event should have been emitted
            assertThat(response.eventsOf<OppgaverTrukket>()).hasSize(1)
        }
}
