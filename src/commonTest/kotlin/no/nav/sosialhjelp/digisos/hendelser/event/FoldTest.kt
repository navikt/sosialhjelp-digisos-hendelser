package no.nav.sosialhjelp.digisos.hendelser.event

import no.nav.sosialhjelp.digisos.hendelser.domain.FoldResult
import no.nav.sosialhjelp.digisos.hendelser.domain.SoknadsStatus
import no.nav.sosialhjelp.digisos.hendelser.fold.NoopVedleggService
import no.nav.sosialhjelp.digisos.hendelser.fold.SoknadMetadata
import no.nav.sosialhjelp.digisos.hendelser.fold.fold
import no.nav.sosialhjelp.filformat.digisos.soker.SoknadsStatus as FilformatSoknadsStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FoldTest {
    private val baseMetadata = SoknadMetadata(
        fiksDigisosId = "test-id",
        kommunenummer = "1234",
        erPapirsoknad = false,
        sistEndret = Instant.parse("2024-01-01T00:00:00Z"),
        timestampSendt = 1_700_000_000_000L,
        navEksternRefId = "ref-1",
        originalSoknadDokumentlagerId = DOKUMENTLAGERID_1,
        vedleggMetadataDokumentlagerId = null,
        fagsystemNavn = null,
        fagsystemVersjon = null,
        mottakerEnhetsnummer = NAVKONTOR,
        mottakerEnhetsnavn = "Oslo",
    )

    @Test
    fun `fold produces both aggregate and event list`() = runTest {
        val digisosSoker = digisosSoker(
            soknadsStatus(FilformatSoknadsStatus.Status.MOTTATT, tidspunkt_1),
        )

        val result: FoldResult = fold(digisosSoker, baseMetadata, NoopVedleggService)

        assertEquals(SoknadsStatus.MOTTATT, result.soknad.status)
        // SoknadSendt + SoknadsStatusEndret
        assertEquals(2, result.hendelser.size)
    }

    @Test
    fun `hendelser are sorted by tidspunkt ascending`() = runTest {
        val digisosSoker = digisosSoker(
            soknadsStatus(FilformatSoknadsStatus.Status.FERDIGBEHANDLET, tidspunkt_3),
            soknadsStatus(FilformatSoknadsStatus.Status.MOTTATT, tidspunkt_1),
            soknadsStatus(FilformatSoknadsStatus.Status.UNDER_BEHANDLING, tidspunkt_2),
        )

        val result = fold(digisosSoker, baseMetadata, NoopVedleggService)

        // Result hendelser (excluding SoknadSendt) should be MOTTATT, UNDER_BEHANDLING, FERDIGBEHANDLET
        val statusHendelser = result.hendelser
            .filterIsInstance<no.nav.sosialhjelp.digisos.hendelser.domain.SoknadsStatusEndret>()
        assertEquals(SoknadsStatus.MOTTATT, statusHendelser[0].status)
        assertEquals(SoknadsStatus.UNDER_BEHANDLING, statusHendelser[1].status)
        assertEquals(SoknadsStatus.FERDIGBEHANDLET, statusHendelser[2].status)
    }

    @Test
    fun `aktive saker override - FERDIGBEHANDLET with active sak becomes UNDER_BEHANDLING`() = runTest {
        val digisosSoker = digisosSoker(
            soknadsStatus(FilformatSoknadsStatus.Status.FERDIGBEHANDLET, tidspunkt_1),
            saksStatus(REFERANSE_1, no.nav.sosialhjelp.filformat.digisos.soker.SaksStatus.Status.UNDER_BEHANDLING, tidspunkt = tidspunkt_2),
        )

        val result = fold(digisosSoker, baseMetadata, NoopVedleggService)

        assertEquals(SoknadsStatus.FERDIGBEHANDLET, result.soknad.status)
        assertEquals(SoknadsStatus.UNDER_BEHANDLING, result.soknad.avledetStatus)
    }

    @Test
    fun `null digisosSoker produces empty aggregate`() = runTest {
        val result = fold(null, baseMetadata, NoopVedleggService)

        assertNotNull(result.soknad)
        // Only SoknadSendt from metadata seeding
        assertEquals(1, result.hendelser.size)
    }

    @Test
    fun `unknown hendelse type is silently ignored`() = runTest {
        val digisosSoker = digisosSoker(
            no.nav.sosialhjelp.filformat.digisos.soker.UkjentHendelse(
                type = "fremtidigHendelsestype",
                hendelsestidspunkt = tidspunkt_1,
                raw = kotlinx.serialization.json.JsonObject(emptyMap()),
            ),
            soknadsStatus(FilformatSoknadsStatus.Status.MOTTATT, tidspunkt_2),
        )

        val result = fold(digisosSoker, baseMetadata, NoopVedleggService)

        // Should still process the known hendelse
        assertEquals(SoknadsStatus.MOTTATT, result.soknad.status)
    }
}
