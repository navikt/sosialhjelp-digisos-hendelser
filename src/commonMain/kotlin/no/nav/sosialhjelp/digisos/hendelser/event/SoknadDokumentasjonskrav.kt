package no.nav.sosialhjelp.digisos.hendelser.event

import no.nav.sosialhjelp.digisos.hendelser.domain.DokumentasjonEtterspurt
import no.nav.sosialhjelp.digisos.hendelser.domain.Oppgavestatus
import no.nav.sosialhjelp.digisos.hendelser.domain.gruppeIdForFrist
import no.nav.sosialhjelp.digisos.hendelser.domain.hendelse.SoknadKravLagtTil
import no.nav.sosialhjelp.digisos.hendelser.domain.sha256
import no.nav.sosialhjelp.digisos.hendelser.domain.unixToInstant

internal suspend fun FoldAccumulator.applySoknadKrav(
    vedleggService: VedleggService,
    timestampSendt: Long,
) {
    val tidspunkt = unixToInstant(timestampSendt)
    val vedleggKreves = vedleggService.hentSoknadVedleggMedStatus(VEDLEGG_KREVES_STATUS)

    val nyeKrav =
        vedleggKreves
            .filterNot { it.type == "annet" && it.tilleggsinfo == "annet" }
            .map {
                DokumentasjonEtterspurt(
                    referanse = sha256(timestampSendt.toString()),
                    tittel = it.type,
                    beskrivelse = it.tilleggsinfo,
                    status = Oppgavestatus.RELEVANT,
                    frist = null,
                    gruppeId = gruppeIdForFrist(null),
                    tidspunktForKrav = tidspunkt,
                    forvaltningsbrevRef = null,
                    kilde = DokumentasjonEtterspurt.Kilde.SOKNAD_VEDLEGG_KREVES,
                )
            }

    dokumentasjonEtterspurt.addAll(nyeKrav)

    if (nyeKrav.isNotEmpty()) {
        hendelser.add(SoknadKravLagtTil(tidspunkt = tidspunkt, antallKrav = nyeKrav.size))
    }
}
