package no.nav.sosialhjelp.digisos.hendelser.event

import no.nav.sosialhjelp.digisos.hendelser.domain.DokumentRef
import no.nav.sosialhjelp.digisos.hendelser.domain.ForelopigSvar
import no.nav.sosialhjelp.digisos.hendelser.domain.ForelopigSvarMottatt
import no.nav.sosialhjelp.digisos.hendelser.domain.toInstant
import no.nav.sosialhjelp.filformat.digisos.soker.DokumentlagerFilreferanse
import no.nav.sosialhjelp.filformat.digisos.soker.SvarUtFilreferanse
import no.nav.sosialhjelp.filformat.digisos.soker.ForelopigSvar as FilformatForelopigSvar

internal fun FoldAccumulator.apply(hendelse: FilformatForelopigSvar) {
    val ref = hendelse.forvaltningsbrev.referanse
    val dokumentRef: DokumentRef =
        when (ref) {
            is DokumentlagerFilreferanse -> DokumentRef.Dokumentlager(ref.id)
            is SvarUtFilreferanse -> DokumentRef.SvarUt(ref.id, ref.nr)
            else -> error("Ikke støttet filreferanse-type: ${ref.type}")
        }

    val tidspunkt = hendelse.hendelsestidspunkt.toInstant()
    forelopigSvar = ForelopigSvar(dokumentRef = dokumentRef, tidspunkt = tidspunkt)
    hendelser.add(ForelopigSvarMottatt(tidspunkt = tidspunkt, brevRef = dokumentRef))
}
