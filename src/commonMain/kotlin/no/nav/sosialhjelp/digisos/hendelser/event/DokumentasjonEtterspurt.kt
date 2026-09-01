package no.nav.sosialhjelp.digisos.hendelser.event

import no.nav.sosialhjelp.digisos.hendelser.domain.DokumentRef
import no.nav.sosialhjelp.digisos.hendelser.domain.DokumentasjonEtterspurt
import no.nav.sosialhjelp.digisos.hendelser.domain.Forvaltningsbrev
import no.nav.sosialhjelp.digisos.hendelser.domain.Krav
import no.nav.sosialhjelp.digisos.hendelser.domain.Oppgavestatus
import no.nav.sosialhjelp.digisos.hendelser.domain.OppgaverTrukket
import no.nav.sosialhjelp.digisos.hendelser.domain.SoknadsStatus
import no.nav.sosialhjelp.digisos.hendelser.domain.gruppeIdForFrist
import no.nav.sosialhjelp.digisos.hendelser.domain.sha256
import no.nav.sosialhjelp.digisos.hendelser.domain.toInstant
import no.nav.sosialhjelp.digisos.hendelser.domain.toLocalDate
import no.nav.sosialhjelp.filformat.digisos.soker.DokumentlagerFilreferanse
import no.nav.sosialhjelp.filformat.digisos.soker.SvarUtFilreferanse
import no.nav.sosialhjelp.filformat.digisos.soker.DokumentasjonEtterspurt as FilformatDokumentasjonEtterspurt

internal fun FoldAccumulator.apply(hendelse: FilformatDokumentasjonEtterspurt) {
    val prevCount = krav.count { it is Krav.DokumentasjonEtterspurt }
    val tidspunkt = hendelse.hendelsestidspunkt.toInstant()

    val forvaltningsbrevRef: DokumentRef? =
        hendelse.forvaltningsbrev?.referanse?.let { ref ->
            when (ref) {
                is DokumentlagerFilreferanse -> DokumentRef.Dokumentlager(ref.id)
                is SvarUtFilreferanse -> DokumentRef.SvarUt(ref.id, ref.nr)
                else -> null
            }
        }

    if (forvaltningsbrevRef != null) {
        forvaltningsbrev.add(Forvaltningsbrev(dokumentRef = forvaltningsbrevRef, tidspunkt = tidspunkt))
    }

    clearOppgaverKrav()

    val nyeKrav =
        hendelse.dokumenter.map { dok ->
            val frist = dok.innsendelsesfrist.toLocalDate()
            Krav.DokumentasjonEtterspurt(
                referanse = sha256(dok.innsendelsesfrist),
                tittel = dok.dokumenttype,
                beskrivelse = dok.tilleggsinformasjon,
                status = Oppgavestatus.RELEVANT,
                frist = frist,
                gruppeId = gruppeIdForFrist(frist),
                tidspunktForKrav = tidspunkt,
                forvaltningsbrevRef = forvaltningsbrevRef,
            )
        }
    krav.addAll(nyeKrav)

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
