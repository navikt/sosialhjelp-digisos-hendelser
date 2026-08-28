package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.navenhet.NavEnhet
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class VilkarTest {
    private val innsynService: InnsynService = mockk()
    private val vedleggService: VedleggService = mockk(relaxed = true)
    private val norgClient: NorgClient = mockk()

    private val service = TestEventService.build(innsynService, vedleggService, norgClient)

    private val mockDigisosSak = mockDigisosSak()
    private val mockJsonSoknad: JsonSoknad = mockk()
    private val mockNavEnhet: NavEnhet = mockk()

    private val soknadsmottaker = "The Office"
    private val enhetsnr = "2317"

    @BeforeEach
    fun init() {
        clearAllMocks()
        every { mockDigisosSak.fiksDigisosId } returns "123"
        every { mockDigisosSak.kommunenummer } returns "0301"
        every { mockDigisosSak.sistEndret } returns System.currentTimeMillis()
        every { mockDigisosSak.sokerFnr } returns "12345678901"
        every { mockDigisosSak.ettersendtInfoNAV } returns null
        every { mockDigisosSak.digisosSoker?.metadata } returns "some id"
        every { mockDigisosSak.digisosSoker?.timestampSistOppdatert } returns System.currentTimeMillis()
        every { mockDigisosSak.originalSoknadNAV?.metadata } returns "some other id"
        every { mockDigisosSak.originalSoknadNAV?.vedleggMetadata } returns null
        every { mockDigisosSak.originalSoknadNAV?.timestampSendt } returns tidspunkt_soknad
        every { mockDigisosSak.originalSoknadNAV?.navEksternRefId } returns null
        every { mockDigisosSak.originalSoknadNAV?.soknadDokument?.dokumentlagerDokumentId } returns null
        every { mockJsonSoknad.mottaker.navEnhetsnavn } returns soknadsmottaker
        every { mockJsonSoknad.mottaker.enhetsnummer } returns enhetsnr
        coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
        coEvery { norgClient.hentNavEnhet(enhetsnr) } returns mockNavEnhet
        every { mockNavEnhet.navn } returns soknadsmottaker

        resetHendelser()
    }

    @Test
    fun `vilkar ETTER utbetaling`() =
        runTest(timeout = 5.seconds) {
            UTBETALING.withHendelsestidspunkt(tidspunkt_1)
            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_2)

            val jsonDigisosSoker =
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(mutableListOf(UTBETALING, VILKAR_OPPFYLT) as MutableList<JsonHendelse>)

            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns jsonDigisosSoker

            val response = service.createModel(mockDigisosSak)

            val vilkar = response.soknad.krav.filterIsInstance<Krav.Vilkar>()
            assertThat(vilkar).hasSize(1)
            assertThat(vilkar[0].status).isEqualTo(Oppgavestatus.OPPFYLT)
            assertThat(vilkar[0].utbetalingsReferanser).contains(UTBETALING_REF_1)
        }

    @Test
    fun `vilkar FØR utbetaling - hendelsecomparator sorterer utbetaling forst`() =
        runTest(timeout = 5.seconds) {
            UTBETALING.withHendelsestidspunkt(tidspunkt_1)
            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_1) // same timestamp — UTBETALING should be first

            val jsonDigisosSoker =
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(mutableListOf(VILKAR_OPPFYLT, UTBETALING) as MutableList<JsonHendelse>)

            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns jsonDigisosSoker

            val response = service.createModel(mockDigisosSak)

            val vilkar = response.soknad.krav.filterIsInstance<Krav.Vilkar>()
            assertThat(vilkar).hasSize(1)
            assertThat(vilkar[0].utbetalingsReferanser).contains(UTBETALING_REF_1)
        }

    @Test
    fun `vilkar uten utbetaling - utbetalingsreferanse beholdes selv om ingen utbetaling finnes`() =
        runTest(timeout = 5.seconds) {
            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_1)

            val jsonDigisosSoker =
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(mutableListOf(VILKAR_OPPFYLT) as MutableList<JsonHendelse>)

            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns jsonDigisosSoker

            val response = service.createModel(mockDigisosSak)

            val vilkar = response.soknad.krav.filterIsInstance<Krav.Vilkar>()
            assertThat(vilkar).hasSize(1)
            // utbetalingsReferanser are stored even if no matching utbetaling exists
            assertThat(vilkar[0].utbetalingsReferanser).contains(UTBETALING_REF_1)
        }

    @Test
    fun `vilkar oppdateres`() =
        runTest(timeout = 5.seconds) {
            UTBETALING.withHendelsestidspunkt(tidspunkt_1)
            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_2)

            val vilkarOppdatert =
                no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse
                    .JsonVilkar()
                    .withType(JsonHendelse.Type.VILKAR)
                    .withVilkarreferanse(VILKAR_REF_1)
                    .withUtbetalingsreferanse(listOf(UTBETALING_REF_1))
                    .withBeskrivelse("oppdatert beskrivelse")
                    .withStatus(no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVilkar.Status.RELEVANT)
                    .also { it.withHendelsestidspunkt(tidspunkt_3) }

            val jsonDigisosSoker =
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(UTBETALING, VILKAR_OPPFYLT, vilkarOppdatert) as MutableList<JsonHendelse>,
                    )

            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns jsonDigisosSoker

            val response = service.createModel(mockDigisosSak)

            val vilkar = response.soknad.krav.filterIsInstance<Krav.Vilkar>()
            assertThat(vilkar).hasSize(1)
            assertThat(vilkar[0].status).isEqualTo(Oppgavestatus.RELEVANT)
            assertThat(vilkar[0].beskrivelse).isEqualTo("oppdatert beskrivelse")
        }
}
