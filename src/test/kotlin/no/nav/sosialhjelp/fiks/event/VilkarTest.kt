package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonUtbetaling
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVilkar
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.navenhet.NavEnhet
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class VilkarTest {
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
    fun `vilkar ETTER utbetaling`() =
        runTest(timeout = 5.seconds) {
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            SOKNADS_STATUS_FERDIGBEHANDLET.withHendelsestidspunkt(tidspunkt_4),
                            UTBETALING.withHendelsestidspunkt(tidspunkt_5),
                            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_6),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.FERDIGBEHANDLET)
            assertThat(model.saker).hasSize(1)
            assertThat(model.historikk).hasSize(5)

            assertThat(model.saker[0].utbetalinger).hasSize(1)
            val utbetaling = model.saker[0].utbetalinger[0]
            assertThat(utbetaling.vilkar).hasSize(1)
            assertThat(utbetaling.vilkar[0].referanse).isEqualTo(VILKAR_REF_1)
            assertThat(utbetaling.vilkar[0].beskrivelse).isEqualTo("beskrivelse")
            assertThat(utbetaling.vilkar[0].getOppgaveStatus()).isEqualTo(Oppgavestatus.RELEVANT)
        }

    @Test
    fun `vilkar UTEN utbetaling`() =
        runTest(timeout = 5.seconds) {
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.UNDER_BEHANDLING)
            assertThat(model.saker).hasSize(0)
            assertThat(model.historikk).hasSize(3)
        }

    @Test
    fun `vilkar FOR utbetaling - vilkar knyttes ikke til noen utbetaling`() =
        runTest(timeout = 5.seconds) {
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            SOKNADS_STATUS_FERDIGBEHANDLET.withHendelsestidspunkt(tidspunkt_4),
                            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_5),
                            UTBETALING.withHendelsestidspunkt(tidspunkt_6),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.saker).hasSize(1)
            assertThat(model.historikk).hasSize(5)
            assertThat(model.saker[0].utbetalinger).hasSize(1)
            val utbetaling = model.saker[0].utbetalinger[0]
            assertThat(utbetaling.vilkar).hasSize(0)
        }

    @Test
    fun `vilkar og utbetaling har samme hendelsestidspunkt`() =
        runTest(timeout = 5.seconds) {
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SOKNADS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            SOKNADS_STATUS_FERDIGBEHANDLET.withHendelsestidspunkt(tidspunkt_4),
                            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_5),
                            UTBETALING.withHendelsestidspunkt(tidspunkt_5),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model).isNotNull
            assertThat(model.status).isEqualTo(SoknadsStatus.FERDIGBEHANDLET)
            assertThat(model.saker).hasSize(1)
            assertThat(model.historikk).hasSize(5)

            assertThat(model.saker[0].utbetalinger).hasSize(1)
            val utbetaling = model.saker[0].utbetalinger[0]
            assertThat(utbetaling.vilkar).hasSize(1)
            assertThat(utbetaling.vilkar[0].referanse).isEqualTo(VILKAR_REF_1)
        }

    @Test
    fun `vilkar oppdatert - samme referanse gir oppdatert status`() =
        runTest(timeout = 5.seconds) {
            val vilkarAnnullert =
                JsonVilkar()
                    .withType(JsonHendelse.Type.VILKAR)
                    .withVilkarreferanse(VILKAR_REF_1)
                    .withUtbetalingsreferanse(listOf(UTBETALING_REF_1))
                    .withBeskrivelse("oppdatert beskrivelse")
                    .withStatus(JsonVilkar.Status.ANNULLERT)

            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_2),
                            UTBETALING.withHendelsestidspunkt(tidspunkt_3),
                            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_4),
                            vilkarAnnullert.withHendelsestidspunkt(tidspunkt_5),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            // Kun ett vilkår - det andre oppdaterte det første
            assertThat(model.vilkar).hasSize(1)
            assertThat(model.vilkar[0].referanse).isEqualTo(VILKAR_REF_1)
            assertThat(model.vilkar[0].status).isEqualTo(Oppgavestatus.ANNULLERT)
            assertThat(model.vilkar[0].beskrivelse).isEqualTo("oppdatert beskrivelse")

            // Oppdatert vilkår legges på utbetalingen
            assertThat(model.saker[0].utbetalinger[0].vilkar).hasSize(1)
            assertThat(
                model.saker[0]
                    .utbetalinger[0]
                    .vilkar[0]
                    .status,
            ).isEqualTo(Oppgavestatus.ANNULLERT)
        }

    @Test
    fun `vilkar ANNULLERT status - getOppgaveStatus returnerer ANNULLERT`() =
        runTest(timeout = 5.seconds) {
            val vilkarAnnullert =
                JsonVilkar()
                    .withType(JsonHendelse.Type.VILKAR)
                    .withVilkarreferanse(VILKAR_REF_1)
                    .withUtbetalingsreferanse(listOf(UTBETALING_REF_1))
                    .withBeskrivelse("annullert vilkår")
                    .withStatus(JsonVilkar.Status.ANNULLERT)

            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_2),
                            UTBETALING.withHendelsestidspunkt(tidspunkt_3),
                            vilkarAnnullert.withHendelsestidspunkt(tidspunkt_4),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            assertThat(model.vilkar).hasSize(1)
            assertThat(model.vilkar[0].status).isEqualTo(Oppgavestatus.ANNULLERT)
            assertThat(model.vilkar[0].getOppgaveStatus()).isEqualTo(Oppgavestatus.ANNULLERT)
        }

    @Test
    fun `vilkar fjernes fra utbetaling nar utbetalingsreferanse endres`() =
        runTest(timeout = 5.seconds) {
            val utbetalingRef2 = "utbetaling 2"
            val utbetaling2 =
                JsonUtbetaling()
                    .withType(JsonHendelse.Type.UTBETALING)
                    .withUtbetalingsreferanse(utbetalingRef2)
                    .withSaksreferanse(REFERANSE_1)
                    .withStatus(JsonUtbetaling.Status.UTBETALT)
                    .withBelop(500.0)
                    .withAnnenMottaker(false)

            // Vilkar peker nå kun på utbetaling2, ikke lenger på UTBETALING_REF_1
            val vilkarOppdatertReferanse =
                JsonVilkar()
                    .withType(JsonHendelse.Type.VILKAR)
                    .withVilkarreferanse(VILKAR_REF_1)
                    .withUtbetalingsreferanse(listOf(utbetalingRef2))
                    .withBeskrivelse("beskrivelse")
                    .withStatus(JsonVilkar.Status.OPPFYLT)

            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_2),
                            UTBETALING.withHendelsestidspunkt(tidspunkt_3),
                            utbetaling2.withHendelsestidspunkt(tidspunkt_4),
                            VILKAR_OPPFYLT.withHendelsestidspunkt(tidspunkt_5),
                            vilkarOppdatertReferanse.withHendelsestidspunkt(tidspunkt_6),
                        ),
                    )
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val model = service.createModel(mockDigisosSak)

            val utbetalinger = model.saker[0].utbetalinger
            val opprinneligUtbetaling = utbetalinger.first { it.referanse == UTBETALING_REF_1 }
            val nyUtbetaling = utbetalinger.first { it.referanse == utbetalingRef2 }

            // Vilkaret er fjernet fra den opprinnelige utbetalingen
            assertThat(opprinneligUtbetaling.vilkar).isEmpty()
            // Vilkaret er knyttet til den nye utbetalingen
            assertThat(nyUtbetaling.vilkar).hasSize(1)
            assertThat(nyUtbetaling.vilkar[0].referanse).isEqualTo(VILKAR_REF_1)
        }
}
