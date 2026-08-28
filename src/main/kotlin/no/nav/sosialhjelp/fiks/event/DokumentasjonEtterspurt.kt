package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonDokumentlagerFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonSvarUtFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumentasjonEtterspurt
import no.nav.sosialhjelp.fiks.domain.DokumentRef
import no.nav.sosialhjelp.fiks.domain.DokumentasjonEtterspurt
import no.nav.sosialhjelp.fiks.domain.Forvaltningsbrev
import no.nav.sosialhjelp.fiks.domain.Krav
import no.nav.sosialhjelp.fiks.domain.OppgaverTrukket
import no.nav.sosialhjelp.fiks.domain.Oppgavestatus
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.domain.gruppeIdForFrist
import no.nav.sosialhjelp.fiks.utils.sha256
import no.nav.sosialhjelp.fiks.utils.toInstant
import no.nav.sosialhjelp.fiks.utils.toLocalDate

internal fun FoldAccumulator.apply(hendelse: JsonDokumentasjonEtterspurt) {
    val prevCount = krav.count { it is Krav.DokumentasjonEtterspurt }
    val tidspunkt = hendelse.hendelsestidspunkt.toInstant()

    val forvaltningsbrevRef: DokumentRef? =
        hendelse.forvaltningsbrev?.referanse?.let { ref ->
            when (ref) {
                is JsonDokumentlagerFilreferanse -> DokumentRef.Dokumentlager(ref.id)
                is JsonSvarUtFilreferanse -> DokumentRef.SvarUt(ref.id, ref.nr)
                else -> null
            }
        }

    // Register forvaltningsbrev on the aggregate
    if (forvaltningsbrevRef != null) {
        forvaltningsbrev.add(Forvaltningsbrev(dokumentRef = forvaltningsbrevRef, tidspunkt = tidspunkt))
    }

    // Wholesale replacement of DokumentasjonEtterspurt krav
    clearOppgaverKrav()

    val nyeKrav =
        hendelse.dokumenter.map { dok ->
            val frist = dok.innsendelsesfrist?.toLocalDate()
            Krav.DokumentasjonEtterspurt(
                referanse = sha256(dok.innsendelsesfrist ?: ""),
                tittel = dok.dokumenttype,
                beskrivelse = dok.tilleggsinformasjon,
                status = Oppgavestatus.RELEVANT,
                frist = frist,
                gruppeId = frist?.let { gruppeIdForFrist(it) },
                tidspunktForKrav = tidspunkt,
                forvaltningsbrevRef = forvaltningsbrevRef,
            )
        }
    krav.addAll(nyeKrav)

    // Emit event
    if (hendelse.dokumenter.isNotEmpty() && forvaltningsbrevRef != null) {
        hendelser.add(
            DokumentasjonEtterspurt(
                tidspunkt = tidspunkt,
                harDokumenter = true,
                forvaltningsbrevRef = forvaltningsbrevRef,
            ),
        )
    }

    if (prevCount > 0 && nyeKrav.isEmpty() && status != SoknadsStatus.BEHANDLES_IKKE) {
        hendelser.add(OppgaverTrukket(tidspunkt = tidspunkt))
    }
}
