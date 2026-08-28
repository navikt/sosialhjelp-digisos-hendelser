package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSaksStatus
import no.nav.sosialhjelp.fiks.domain.SaksStatus
import no.nav.sosialhjelp.fiks.domain.SaksStatusEndret
import no.nav.sosialhjelp.fiks.utils.toInstant

internal fun FoldAccumulator.apply(hendelse: JsonSaksStatus) {
    val referanse = hendelse.referanse
    val nySaksStatus = SaksStatus.valueOf(hendelse.status?.name ?: JsonSaksStatus.Status.UNDER_BEHANDLING.name)
    val tittel = hendelse.tittel
    val existing = getSak(referanse)
    val prevStatus = existing?.saksStatus

    val erNySak = existing == null
    upsertSak(referanse, nySaksStatus, tittel)

    // Emit an event only on transitions that are visible/meaningful to consumers:
    // - Any new sak
    // - Transition INTO IKKE_INNSYN or BEHANDLES_IKKE (so consumers can show "status unavailable")
    // - NOT the transition out of IKKE_INNSYN/BEHANDLES_IKKE back to UNDER_BEHANDLING
    //   (that was only logged internally in the original code, never shown as a historikk entry)
    val shouldEmit =
        erNySak ||
            (nySaksStatus == SaksStatus.IKKE_INNSYN && prevStatus != SaksStatus.IKKE_INNSYN) ||
            (nySaksStatus == SaksStatus.BEHANDLES_IKKE && prevStatus != SaksStatus.BEHANDLES_IKKE) ||
            (nySaksStatus == SaksStatus.FERDIGBEHANDLET && prevStatus != SaksStatus.FERDIGBEHANDLET)

    if (shouldEmit) {
        hendelser.add(
            SaksStatusEndret(
                tidspunkt = hendelse.hendelsestidspunkt.toInstant(),
                saksReferanse = referanse,
                tittel = tittel,
                status = nySaksStatus,
                erNyeSak = erNySak,
            ),
        )
    }
}
