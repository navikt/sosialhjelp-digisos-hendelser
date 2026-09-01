package no.nav.sosialhjelp.digisos.hendelser.event

import no.nav.sosialhjelp.digisos.hendelser.domain.Krav
import no.nav.sosialhjelp.digisos.hendelser.domain.KravEndret
import no.nav.sosialhjelp.digisos.hendelser.domain.KravType
import no.nav.sosialhjelp.digisos.hendelser.domain.Oppgavestatus
import no.nav.sosialhjelp.digisos.hendelser.domain.toInstant
import no.nav.sosialhjelp.filformat.digisos.soker.Vilkar as FilformatVilkar

internal fun FoldAccumulator.apply(hendelse: FilformatVilkar) {
    val referanse = hendelse.vilkarreferanse
    val status =
        when (hendelse.status) {
            FilformatVilkar.Status.RELEVANT -> Oppgavestatus.RELEVANT
            FilformatVilkar.Status.ANNULLERT -> Oppgavestatus.ANNULLERT
            FilformatVilkar.Status.OPPFYLT -> Oppgavestatus.OPPFYLT
            FilformatVilkar.Status.IKKE_OPPFYLT -> Oppgavestatus.IKKE_OPPFYLT
            FilformatVilkar.Status.UKJENT, null -> Oppgavestatus.RELEVANT
        }
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
