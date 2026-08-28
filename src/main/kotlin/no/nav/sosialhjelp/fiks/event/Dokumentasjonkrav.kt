package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumentasjonkrav
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.KravEndret
import no.nav.sosialhjelp.fiks.domain.KravType
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.domain.gruppeIdForFrist
import no.nav.sosialhjelp.fiks.utils.sha256
import no.nav.sosialhjelp.fiks.utils.toInstant
import no.nav.sosialhjelp.fiks.utils.toLocalDate

internal fun FoldAccumulator.apply(hendelse: JsonDokumentasjonkrav) {
    val referanse = hendelse.dokumentasjonkravreferanse
    val status = Oppgavestatus.valueOf(hendelse.status.value())
    val frist = hendelse.frist?.toLocalDate()
    val tidspunkt = hendelse.hendelsestidspunkt.toInstant()
    val existing = krav.filterIsInstance<Krav.Dokumentasjonkrav>().firstOrNull { it.referanse == referanse }

    val dokkrav =
        Krav.Dokumentasjonkrav(
            referanse = referanse,
            tittel = hendelse.tittel,
            beskrivelse = hendelse.beskrivelse,
            status = status,
            frist = frist,
            saksReferanse = hendelse.saksreferanse,
            utbetalingsReferanser = hendelse.utbetalingsreferanse ?: emptyList(),
            gruppeId = frist?.let { sha256(gruppeIdForFrist(it) ?: it.toString()) },
            datoLagtTil = existing?.datoLagtTil ?: tidspunkt,
        )

    krav.removeAll { it is Krav.Dokumentasjonkrav && it.referanse == referanse }
    krav.add(dokkrav)

    hendelser.add(KravEndret(tidspunkt = tidspunkt, kravReferanse = referanse, kravType = KravType.DOKUMENTASJONKRAV))
}
