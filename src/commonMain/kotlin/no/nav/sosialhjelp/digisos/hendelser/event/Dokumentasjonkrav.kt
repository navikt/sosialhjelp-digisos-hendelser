package no.nav.sosialhjelp.digisos.hendelser.event

import no.nav.sosialhjelp.digisos.hendelser.domain.Krav
import no.nav.sosialhjelp.digisos.hendelser.domain.KravEndret
import no.nav.sosialhjelp.digisos.hendelser.domain.KravType
import no.nav.sosialhjelp.digisos.hendelser.domain.Oppgavestatus
import no.nav.sosialhjelp.digisos.hendelser.domain.gruppeIdForFrist
import no.nav.sosialhjelp.digisos.hendelser.domain.toInstant
import no.nav.sosialhjelp.digisos.hendelser.domain.toLocalDate
import no.nav.sosialhjelp.filformat.digisos.soker.Dokumentasjonkrav as FilformatDokumentasjonkrav

internal fun FoldAccumulator.apply(hendelse: FilformatDokumentasjonkrav) {
    val referanse = hendelse.dokumentasjonkravreferanse
    val status =
        when (hendelse.status) {
            FilformatDokumentasjonkrav.Status.RELEVANT -> Oppgavestatus.RELEVANT
            FilformatDokumentasjonkrav.Status.LEVERT_TIDLIGERE -> Oppgavestatus.LEVERT_TIDLIGERE
            FilformatDokumentasjonkrav.Status.ANNULLERT -> Oppgavestatus.ANNULLERT
            FilformatDokumentasjonkrav.Status.OPPFYLT -> Oppgavestatus.OPPFYLT
            FilformatDokumentasjonkrav.Status.IKKE_OPPFYLT -> Oppgavestatus.IKKE_OPPFYLT
            FilformatDokumentasjonkrav.Status.UKJENT, null -> Oppgavestatus.RELEVANT
        }
    val frist = hendelse.frist?.toLocalDate()
    val tidspunkt = hendelse.hendelsestidspunkt.toInstant()
    val existing = krav.filterIsInstance<Krav.Dokumentasjonkrav>().firstOrNull { it.referanse == referanse }

    upsertKrav(
        Krav.Dokumentasjonkrav(
            referanse = referanse,
            tittel = hendelse.tittel,
            beskrivelse = hendelse.beskrivelse,
            status = status,
            frist = frist,
            saksReferanse = hendelse.saksreferanse,
            utbetalingsReferanser = hendelse.utbetalingsreferanse ?: emptyList(),
            gruppeId = frist?.let { gruppeIdForFrist(it) },
            datoLagtTil = existing?.datoLagtTil ?: tidspunkt,
        ),
    )

    hendelser.add(KravEndret(tidspunkt = tidspunkt, kravReferanse = referanse, kravType = KravType.DOKUMENTASJONKRAV))
}
