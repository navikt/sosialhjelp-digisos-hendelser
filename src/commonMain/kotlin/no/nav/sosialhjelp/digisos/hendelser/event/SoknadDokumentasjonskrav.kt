package no.nav.sosialhjelp.digisos.hendelser.event

import no.nav.sosialhjelp.digisos.hendelser.domain.Krav
import no.nav.sosialhjelp.digisos.hendelser.domain.Oppgavestatus
import no.nav.sosialhjelp.digisos.hendelser.domain.SoknadKravLagtTil
import no.nav.sosialhjelp.digisos.hendelser.domain.sha256

internal suspend fun FoldAccumulator.applySoknadKrav(
    vedleggService: VedleggService,
    timestampSendt: Long,
) {
    val tidspunkt = no.nav.sosialhjelp.digisos.hendelser.domain.unixToInstant(timestampSendt)
    val vedleggKreves = vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS)

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
