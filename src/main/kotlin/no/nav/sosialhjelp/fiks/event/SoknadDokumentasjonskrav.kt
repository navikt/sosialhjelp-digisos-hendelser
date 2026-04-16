package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.vedlegg.JsonVedlegg
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.Oppgave
import no.nav.sosialhjelp.fiks.utils.sha256
import no.nav.sosialhjelp.fiks.utils.unixToLocalDateTime
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService

suspend fun InternalDigisosSoker.applySoknadKrav(
    digisosSak: DigisosSak,
    vedleggService: VedleggService,
    timestampSendt: Long,
) {
    val vedleggKreves = vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, digisosSak)

    oppgaver =
        vedleggKreves
            .filterNot { it.type == "annet" && it.tilleggsinfo == "annet" }
            .map {
                Oppgave(
                    sha256(timestampSendt.toString()),
                    it.type,
                    it.tilleggsinfo,
                    JsonVedlegg.HendelseType.SOKNAD,
                    it.hendelseReferanse,
                    null,
                    unixToLocalDateTime(timestampSendt),
                    false,
                )
            }.toMutableList()
}
