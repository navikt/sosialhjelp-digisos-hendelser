package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonDokumentlagerFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonSvarUtFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonForelopigSvar
import no.nav.sosialhjelp.fiks.domain.DokumentRef
import no.nav.sosialhjelp.fiks.domain.ForelopigSvar
import no.nav.sosialhjelp.fiks.domain.ForelopigSvarMottatt
import no.nav.sosialhjelp.fiks.utils.toInstant

internal fun FoldAccumulator.apply(hendelse: JsonForelopigSvar) {
    val ref = hendelse.forvaltningsbrev.referanse
    val dokumentRef: DokumentRef =
        when (ref) {
            is JsonDokumentlagerFilreferanse -> DokumentRef.Dokumentlager(ref.id)
            is JsonSvarUtFilreferanse -> DokumentRef.SvarUt(ref.id, ref.nr)
            else -> error("Ikke støttet referansetype ${ref.type}")
        }

    val tidspunkt = hendelse.hendelsestidspunkt.toInstant()
    forelopigSvar = ForelopigSvar(dokumentRef = dokumentRef, tidspunkt = tidspunkt)

    hendelser.add(ForelopigSvarMottatt(tidspunkt = tidspunkt, brevRef = dokumentRef))
}
