package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonForvaltningsbrev
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonDokumentlagerFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonForelopigSvar
import no.nav.sosialhjelp.fiks.domain.DokumentRef
import no.nav.sosialhjelp.fiks.domain.ForelopigSvarMottatt
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class ForelopigSvarTest {
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
    fun `forelopigSvar setter forelopigSvar pa soknad`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            FORELOPIGSVAR.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.forelopigSvar).isNotNull()
        }

    @Test
    fun `forelopigSvar med SvarUt referanse gir ForelopigSvarMottatt-hendelse og SvarUt dokumentRef`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            FORELOPIGSVAR.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.forelopigSvar).isNotNull()
            assertThat(response.soknad.forelopigSvar!!.dokumentRef).isInstanceOf(DokumentRef.SvarUt::class.java)
            assertThat(response.soknad.forelopigSvar!!.tidspunkt).isNotNull()

            val hendelse = response.eventsOf<ForelopigSvarMottatt>()
            assertThat(hendelse).hasSize(1)
            assertThat(hendelse[0].brevRef).isInstanceOf(DokumentRef.SvarUt::class.java)
        }

    @Test
    fun `forelopigSvar med Dokumentlager referanse gir ForelopigSvarMottatt-hendelse med Dokumentlager ref`() =
        runTest(timeout = 5.seconds) {
            val forelopigSvarMedDokumentlager =
                JsonForelopigSvar()
                    .withType(JsonHendelse.Type.FORELOPIG_SVAR)
                    .withForvaltningsbrev(
                        JsonForvaltningsbrev().withReferanse(
                            JsonDokumentlagerFilreferanse()
                                .withType(JsonFilreferanse.Type.DOKUMENTLAGER)
                                .withId(DOKUMENTLAGERID_1),
                        ),
                    )

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            forelopigSvarMedDokumentlager.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.forelopigSvar).isNotNull()
            assertThat(response.soknad.forelopigSvar!!.dokumentRef).isInstanceOf(DokumentRef.Dokumentlager::class.java)

            val hendelse = response.eventsOf<ForelopigSvarMottatt>()
            assertThat(hendelse).hasSize(1)
            assertThat(hendelse[0].brevRef).isInstanceOf(DokumentRef.Dokumentlager::class.java)
        }

    @Test
    fun `forelopigSvar tidspunkt settes fra hendelsestidspunkt`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            FORELOPIGSVAR.withHendelsestidspunkt(tidspunkt_2),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.forelopigSvar).isNotNull()
            assertThat(response.soknad.forelopigSvar!!.tidspunkt).isNotNull()
        }

    @Test
    fun `to forelopigSvar-hendelser - siste erstatter forste og begge gir ForelopigSvarMottatt-hendelse`() =
        runTest(timeout = 5.seconds) {
            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            FORELOPIGSVAR.withHendelsestidspunkt(tidspunkt_2),
                            FORELOPIGSVAR.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.forelopigSvar).isNotNull()
            // Begge hendelser legges til typed events list
            val forelopigSvarHendelser = response.eventsOf<ForelopigSvarMottatt>()
            assertThat(forelopigSvarHendelser).hasSize(2)
        }
}
