package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSoknadsStatus
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.domain.SoknadsStatusEndret
import no.nav.sosialhjelp.fiks.event.EventService.Companion.stripEnhetsnavnForKommune
import no.nav.sosialhjelp.fiks.utils.toInstant

internal fun FoldAccumulator.apply(hendelse: JsonSoknadsStatus) {
    status = SoknadsStatus.valueOf(hendelse.status.name)

    val mottakerNavn: String? =
        if (hendelse.status == JsonSoknadsStatus.Status.MOTTATT) {
            mottaker?.navn?.let { stripEnhetsnavnForKommune(it) }
        } else {
            null
        }

    hendelser.add(
        SoknadsStatusEndret(
            tidspunkt = hendelse.hendelsestidspunkt.toInstant(),
            status = status,
            mottakerNavn = mottakerNavn,
        ),
    )
}
