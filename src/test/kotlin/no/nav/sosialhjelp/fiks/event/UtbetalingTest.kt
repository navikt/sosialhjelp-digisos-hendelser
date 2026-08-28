package no.nav.sosialhjelp.fiks.event

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonUtbetaling
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.domain.UtbetalingsStatus
import no.nav.sosialhjelp.fiks.navenhet.NorgClient
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

internal class UtbetalingTest {
    private val innsynService: InnsynService = mockk()
    private val vedleggService: VedleggService = mockk()
    private val norgClient: NorgClient = mockk()

    private val service = TestEventService.build(innsynService, vedleggService, norgClient)

    private val mockDigisosSak = mockDigisosSak()

    @BeforeEach
    fun init() {
        clearAllMocks()
        resetHendelser()
    }

    @Test
    fun `utbetaling ETTER vedtakFattet og saksStatus`() =
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
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_3),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_4),
                            SOKNADS_STATUS_FERDIGBEHANDLET.withHendelsestidspunkt(tidspunkt_5),
                            UTBETALING.withHendelsestidspunkt(tidspunkt_6),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.FERDIGBEHANDLET)
            assertThat(response.soknad.saker).hasSize(1)

            // tittel for sak fra saksstatus-hendelse
            assertThat(response.soknad.saker[0].tittel).isEqualTo(TITTEL_1)

            val utbetalinger = response.soknad.utbetalinger.filter { it.saksReferanse == REFERANSE_1 }
            assertThat(utbetalinger).hasSize(1)
            val utbetaling = utbetalinger[0]
            assertThat(utbetaling.referanse).isEqualTo(UTBETALING_REF_1)
            assertThat(utbetaling.status).isEqualTo(UtbetalingsStatus.UTBETALT)
            assertThat(utbetaling.belop).isEqualByComparingTo("1234.56")
            assertThat(utbetaling.beskrivelse).isEqualTo(TITTEL_1)
            assertThat(utbetaling.forfallsDato).isNotNull()
            assertThat(utbetaling.utbetalingsDato).isNotNull()
            assertThat(utbetaling.fom).isNotNull()
            assertThat(utbetaling.tom).isNotNull()
            assertThat(utbetaling.mottaker).isEqualTo("fnr")
            assertThat(utbetaling.kontonummer).isNull()
            assertThat(utbetaling.utbetalingsmetode).isEqualTo("pose med krølla femtilapper")
        }

    @Test
    fun `utbetaling UTEN vedtakFattet`() =
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
                            UTBETALING_BANKOVERFORING.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.UNDER_BEHANDLING)

            val utbetaling = response.soknad.utbetalinger[0]
            assertThat(utbetaling.belop).isEqualByComparingTo("1234.56")
        }

    @Test
    fun `utbetaling kontonummer settes kun hvis annenMottaker er false`() =
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
                            UTBETALING_BANKOVERFORING_ANNEN_MOTTAKER.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response).isNotNull
            assertThat(response.soknad.status).isEqualTo(SoknadsStatus.UNDER_BEHANDLING)

            assertThat(response.soknad.utbetalinger[0].belop).isEqualByComparingTo("1234.56")
            assertThat(response.soknad.utbetalinger[0].kontonummer).isNull()
            assertThat(response.soknad.utbetalinger[0].mottaker).isEqualTo("utleier")
        }

    @Test
    fun `utbetaling med PLANLAGT_UTBETALING status`() =
        runTest(timeout = 5.seconds) {
            val utbetalingPlanlagt =
                JsonUtbetaling()
                    .withType(no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse.Type.UTBETALING)
                    .withUtbetalingsreferanse(UTBETALING_REF_1)
                    .withSaksreferanse(REFERANSE_1)
                    .withStatus(JsonUtbetaling.Status.PLANLAGT_UTBETALING)
                    .withBelop(500.0)
                    .withAnnenMottaker(false)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            utbetalingPlanlagt.withHendelsestidspunkt(tidspunkt_4),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.utbetalinger).hasSize(1)
            assertThat(response.soknad.utbetalinger[0].status).isEqualTo(UtbetalingsStatus.PLANLAGT_UTBETALING)
            assertThat(response.soknad.utbetalinger[0].belop).isEqualByComparingTo(BigDecimal.valueOf(500.0))
        }

    @Test
    fun `utbetaling med STOPPET status - stoppetDato settes fra hendelsestidspunkt`() =
        runTest(timeout = 5.seconds) {
            val utbetalingStoppet =
                JsonUtbetaling()
                    .withType(no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse.Type.UTBETALING)
                    .withUtbetalingsreferanse(UTBETALING_REF_1)
                    .withSaksreferanse(REFERANSE_1)
                    .withStatus(JsonUtbetaling.Status.STOPPET)
                    .withBelop(1000.0)
                    .withAnnenMottaker(false)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            utbetalingStoppet.withHendelsestidspunkt(tidspunkt_4),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.utbetalinger).hasSize(1)
            assertThat(response.soknad.utbetalinger[0].status).isEqualTo(UtbetalingsStatus.STOPPET)
            assertThat(response.soknad.utbetalinger[0].stoppetDato).isNotNull()
        }

    @Test
    fun `utbetaling med ANNULLERT status`() =
        runTest(timeout = 5.seconds) {
            val utbetalingAnnullert =
                JsonUtbetaling()
                    .withType(no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse.Type.UTBETALING)
                    .withUtbetalingsreferanse(UTBETALING_REF_1)
                    .withSaksreferanse(REFERANSE_1)
                    .withStatus(JsonUtbetaling.Status.ANNULLERT)
                    .withBelop(750.0)
                    .withAnnenMottaker(false)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            utbetalingAnnullert.withHendelsestidspunkt(tidspunkt_4),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.utbetalinger).hasSize(1)
            assertThat(response.soknad.utbetalinger[0].status).isEqualTo(UtbetalingsStatus.ANNULLERT)
        }

    @Test
    fun `utbetaling med null status - defaults til PLANLAGT_UTBETALING`() =
        runTest(timeout = 5.seconds) {
            val utbetalingUtenStatus =
                JsonUtbetaling()
                    .withType(no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse.Type.UTBETALING)
                    .withUtbetalingsreferanse(UTBETALING_REF_1)
                    .withSaksreferanse(REFERANSE_1)
                    .withBelop(250.0)
                    .withAnnenMottaker(false)
            // status intentionally not set → null

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            utbetalingUtenStatus.withHendelsestidspunkt(tidspunkt_4),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.utbetalinger).hasSize(1)
            assertThat(response.soknad.utbetalinger[0].status).isEqualTo(UtbetalingsStatus.PLANLAGT_UTBETALING)
        }

    @Test
    fun `utbetaling oppdatert - samme referanse to ganger erstatter forste`() =
        runTest(timeout = 5.seconds) {
            val utbetalingOppdatert =
                JsonUtbetaling()
                    .withType(no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse.Type.UTBETALING)
                    .withUtbetalingsreferanse(UTBETALING_REF_1)
                    .withSaksreferanse(REFERANSE_1)
                    .withStatus(JsonUtbetaling.Status.UTBETALT)
                    .withBelop(9999.0)
                    .withBeskrivelse("oppdatert beskrivelse")
                    .withAnnenMottaker(false)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            UTBETALING.withHendelsestidspunkt(tidspunkt_4),
                            utbetalingOppdatert.withHendelsestidspunkt(tidspunkt_5),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.utbetalinger).hasSize(1)
            assertThat(response.soknad.utbetalinger[0].belop).isEqualByComparingTo(BigDecimal.valueOf(9999.0))
            assertThat(response.soknad.utbetalinger[0].beskrivelse).isEqualTo("oppdatert beskrivelse")
        }

    @Test
    fun `utbetaling med null belop - defaults til 0`() =
        runTest(timeout = 5.seconds) {
            val utbetalingNullBelop =
                JsonUtbetaling()
                    .withType(no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse.Type.UTBETALING)
                    .withUtbetalingsreferanse(UTBETALING_REF_1)
                    .withSaksreferanse(REFERANSE_1)
                    .withStatus(JsonUtbetaling.Status.PLANLAGT_UTBETALING)
                    .withAnnenMottaker(false)
            // belop intentionally not set → null

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_SAKS_STATUS_UNDERBEHANDLING.withHendelsestidspunkt(tidspunkt_2),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_3),
                            utbetalingNullBelop.withHendelsestidspunkt(tidspunkt_4),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            assertThat(response.soknad.utbetalinger).hasSize(1)
            assertThat(response.soknad.utbetalinger[0].belop).isEqualByComparingTo(BigDecimal.ZERO)
        }

    @Test
    fun `utbetaling uten matchende saksreferanse legges til model utbetalinger`() =
        runTest(timeout = 5.seconds) {
            val utbetalingAnnenSak =
                JsonUtbetaling()
                    .withType(no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonHendelse.Type.UTBETALING)
                    .withUtbetalingsreferanse(UTBETALING_REF_1)
                    .withSaksreferanse("ukjent-saksreferanse")
                    .withStatus(JsonUtbetaling.Status.UTBETALT)
                    .withBelop(300.0)
                    .withAnnenMottaker(false)

            val digisosSak = mockDigisosSak()
            coEvery { innsynService.hentJsonDigisosSoker(any()) } returns
                JsonDigisosSoker()
                    .withAvsender(avsender)
                    .withVersion("123")
                    .withHendelser(
                        listOf(
                            SOKNADS_STATUS_MOTTATT.withHendelsestidspunkt(tidspunkt_1),
                            SAK1_VEDTAK_FATTET_INNVILGET.withHendelsestidspunkt(tidspunkt_2),
                            utbetalingAnnenSak.withHendelsestidspunkt(tidspunkt_3),
                        ),
                    )
            coEvery { innsynService.hentOriginalSoknad(any()) } returns null
            coEvery { vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, any()) } returns emptyList()

            val response = service.createModel(digisosSak)

            // Utbetaling legges til model.utbetalinger selv uten saksreferanse-match
            assertThat(response.soknad.utbetalinger).hasSize(1)
            assertThat(response.soknad.utbetalinger[0].status).isEqualTo(UtbetalingsStatus.UTBETALT)
        }
}
