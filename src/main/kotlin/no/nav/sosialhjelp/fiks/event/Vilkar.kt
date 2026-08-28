package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVilkar
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.KravEndret
import no.nav.sosialhjelp.fiks.domain.KravType
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.utils.toInstant

internal fun FoldAccumulator.apply(hendelse: JsonVilkar) {
    val referanse = hendelse.vilkarreferanse
    val status = Oppgavestatus.valueOf(hendelse.status.value())
    val existing = krav.filterIsInstance<Krav.Vilkar>().firstOrNull { it.referanse == referanse }
    val tidspunkt = hendelse.hendelsestidspunkt.toInstant()

    upsertKrav(
        if (existing != null) {
            Krav.Vilkar(
                referanse = referanse,
                tittel = hendelse.tittel,
                beskrivelse = hendelse.beskrivelse,
                status = status,
                saksReferanse = hendelse.saksreferanse,
                utbetalingsReferanser = hendelse.utbetalingsreferanse ?: emptyList(),
                datoLagtTil = existing.datoLagtTil,
                datoSistEndret = tidspunkt,
            )
        } else {
            Krav.Vilkar(
                referanse = referanse,
                tittel = hendelse.tittel,
                beskrivelse = hendelse.beskrivelse,
                status = status,
                saksReferanse = hendelse.saksreferanse,
                utbetalingsReferanser = hendelse.utbetalingsreferanse ?: emptyList(),
                datoLagtTil = tidspunkt,
                datoSistEndret = tidspunkt,
            )
        },
    )

    hendelser.add(KravEndret(tidspunkt = tidspunkt, kravReferanse = referanse, kravType = KravType.VILKAR))
}
