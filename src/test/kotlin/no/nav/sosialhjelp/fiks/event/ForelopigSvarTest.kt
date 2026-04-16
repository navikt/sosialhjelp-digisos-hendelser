package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonForvaltningsbrev
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonDokumentlagerFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonForelopigSvar
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.navenhet.NavEnhet
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class ForelopigSvarTest {
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
    fun `forelopigSvar setter harMottattForelopigSvar til true`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.forelopigSvar.harMottattForelopigSvar).isTrue()
        }

    @Test
    fun `forelopigSvar med SvarUt referanse gir url og BREV_OM_SAKSBEANDLINGSTID i historikk`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.forelopigSvar.harMottattForelopigSvar).isTrue()
            assertThat(model.forelopigSvar.link).isNotNull()
            assertThat(model.forelopigSvar.timestamp).isNotNull()

            val forelopigSvarHendelse = model.historikk.last()
            assertThat(forelopigSvarHendelse.hendelseType).isEqualTo(HendelseTekstType.BREV_OM_SAKSBEANDLINGSTID)
            assertThat(forelopigSvarHendelse.url).isNotNull()
            assertThat(forelopigSvarHendelse.url!!.linkTekst).isEqualTo(HendelseTekstType.VIS_BREVET_LENKETEKST)
        }

    @Test
    fun `forelopigSvar med Dokumentlager referanse gir url og BREV_OM_SAKSBEANDLINGSTID i historikk`() =
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.forelopigSvar.harMottattForelopigSvar).isTrue()
            assertThat(model.forelopigSvar.link).isNotNull()

            val forelopigSvarHendelse = model.historikk.last()
            assertThat(forelopigSvarHendelse.hendelseType).isEqualTo(HendelseTekstType.BREV_OM_SAKSBEANDLINGSTID)
            assertThat(forelopigSvarHendelse.url).isNotNull()
        }

    @Test
    fun `forelopigSvar timestamp settes fra hendelsestidspunkt`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.forelopigSvar.timestamp).isEqualTo(tidspunkt_2.toLocalDateTime())
        }

    @Test
    fun `to forelopigSvar-hendelser - siste erstatter forste`() =
        runTest(timeout = 5.seconds) {
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
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.forelopigSvar.harMottattForelopigSvar).isTrue()
            // Siste forelopigSvar skal gjelde
            assertThat(model.forelopigSvar.timestamp).isEqualTo(tidspunkt_3.toLocalDateTime())
            // Begge hendelser legges til historikk
            val forelopigSvarHendelser =
                model.historikk.filter { it.hendelseType == HendelseTekstType.BREV_OM_SAKSBEANDLINGSTID }
            assertThat(forelopigSvarHendelser).hasSize(2)
        }
}
