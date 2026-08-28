package no.nav.sosialhjelp.fiks.event

import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.domain.SoknadKravLagtTil
import no.nav.sosialhjelp.fiks.utils.sha256
import no.nav.sosialhjelp.fiks.utils.unixToInstant
import no.nav.sosialhjelp.fiks.vedlegg.VEDLEGG_KREVES_STATUS
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService

internal suspend fun FoldAccumulator.applySoknadKrav(
    digisosSak: DigisosSak,
    vedleggService: VedleggService,
    timestampSendt: Long,
) {
    val vedleggKreves = vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS, digisosSak)
    val tidspunkt = unixToInstant(timestampSendt)

    val nyeKrav =
        vedleggKreves
            .filterNot { it.type == "annet" && it.tilleggsinfo == "annet" }
            .map {
                Krav.SoknadVedleggKreves(
                    referanse = sha256(timestampSendt.toString()),
                    tittel = it.type,
                    beskrivelse = it.tilleggsinfo,
                    status = Oppgavestatus.RELEVANT,
                    frist = null,
                    gruppeId = null,
                    tidspunktForKrav = tidspunkt,
                )
            }

    krav.addAll(nyeKrav)

    if (nyeKrav.isNotEmpty()) {
        hendelser.add(SoknadKravLagtTil(tidspunkt = tidspunkt, antallKrav = nyeKrav.size))
    }
}
