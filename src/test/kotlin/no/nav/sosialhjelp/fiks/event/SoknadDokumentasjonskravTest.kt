package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.api.fiks.DokumentInfo
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.SoknadKravLagtTil
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.InternalVedlegg
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds

internal class SoknadDokumentasjonskravTest {
    private val innsynService: InnsynService = mockk()
    private val vedleggService: VedleggService = mockk(relaxed = true)
    private val norgClient: NorgClient = mockk()

    private val service = TestEventService.build(innsynService, vedleggService, norgClient)

    private val mockJsonSoknad: JsonSoknad = mockk()

    @BeforeEach
    fun init() {
        clearAllMocks()
        every { mockJsonSoknad.mottaker } returns null
        resetHendelser()
    }

    @Test
    fun `soknadKrav legges til naar ingen DokumentasjonEtterspurt og soknad er ny`() =
        runTest(timeout = 5.seconds) {
            val digisosSak =
                mockDigisosSak(
                    // timestampSendt = recent (less than 30 days ago)
                    timestampSendt = System.currentTimeMillis() - 1_000 * 60 * 60 * 24,
                )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker().withAvsender(avsender)
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns
                listOf(
                    InternalVedlegg(
                        type = "lonnslipp",
                        tilleggsinfo = null,
                        hendelseType = null,
                        hendelseReferanse = null,
                        dokumentInfoList = mutableListOf(DokumentInfo("lonnslipp.pdf", "", 0L)),
                        tidspunktLastetOpp = LocalDateTime.now(),
                        innsendelsesfrist = null,
                    ),
                )

            val response = service.createModel(digisosSak)

            val krav = response.kravOf<Krav.SoknadVedleggKreves>()
            assertThat(krav).hasSize(1)
            assertThat(krav[0].tittel).isEqualTo("lonnslipp")

            val hendelse = response.eventsOf<SoknadKravLagtTil>()
            assertThat(hendelse).hasSize(1)
            assertThat(hendelse[0].antallKrav).isEqualTo(1)
        }

    @Test
    fun `soknadKrav legges IKKE til naar DokumentasjonEtterspurt finnes`() =
        runTest(timeout = 5.seconds) {
            val digisosSak =
                mockDigisosSak(timestampSendt = System.currentTimeMillis() - 1_000 * 60 * 60 * 24)
            coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withHendelser(
                        mutableListOf(
                            DOKUMENTASJONETTERSPURT.withHendelsestidspunkt(tidspunkt_1),
                        ) as MutableList<no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse>,
                    )

            val response = service.createModel(digisosSak)

            // Should not add SoknadVedleggKreves because DokumentasjonEtterspurt exists
            assertThat(response.kravOf<Krav.SoknadVedleggKreves>()).isEmpty()
        }

    @Test
    fun `soknadKrav filtrerer ut annet-annet`() =
        runTest(timeout = 5.seconds) {
            val digisosSak =
                mockDigisosSak(timestampSendt = System.currentTimeMillis() - 1_000 * 60 * 60 * 24)
            coEvery { innsynService.hentOriginalSoknad(any()) } returns mockJsonSoknad
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker().withAvsender(avsender)
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns
                listOf(
                    InternalVedlegg(
                        type = "lonnslipp",
                        tilleggsinfo = null,
                        hendelseType = null,
                        hendelseReferanse = null,
                        dokumentInfoList = mutableListOf(),
                        tidspunktLastetOpp = LocalDateTime.now(),
                        innsendelsesfrist = null,
                    ),
                    InternalVedlegg(
                        type = "annet",
                        tilleggsinfo = "annet",
                        hendelseType = null,
                        hendelseReferanse = null,
                        dokumentInfoList = mutableListOf(),
                        tidspunktLastetOpp = LocalDateTime.now(),
                        innsendelsesfrist = null,
                    ),
                )

            val response = service.createModel(digisosSak)

            // annet+annet should be filtered out
            val krav = response.kravOf<Krav.SoknadVedleggKreves>()
            assertThat(krav).hasSize(1)
            assertThat(krav[0].tittel).isEqualTo("lonnslipp")
        }
}
