package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonDokumentlagerFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonSvarUtFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVedtakFattet
import no.nav.sosialhjelp.fiks.domain.DokumentRef
import no.nav.sosialhjelp.fiks.domain.UtfallVedtak
import no.nav.sosialhjelp.fiks.domain.Vedtak
import no.nav.sosialhjelp.fiks.domain.VedtakFattet
import no.nav.sosialhjelp.fiks.utils.toInstant

internal fun FoldAccumulator.apply(hendelse: JsonVedtakFattet) {
    val dokumentRef: DokumentRef =
        when (val ref = hendelse.vedtaksfil.referanse) {
            is JsonDokumentlagerFilreferanse -> DokumentRef.Dokumentlager(ref.id)
            is JsonSvarUtFilreferanse -> DokumentRef.SvarUt(ref.id, ref.nr)
            else -> error("Ikke støttet referansetype ${ref.type}")
        }

    val utfall = hendelse.utfall?.name?.let { UtfallVedtak.valueOf(it) }
    val saksReferanse = hendelse.saksreferanse

    // Find or create the sak for this vedtak.
    // If saksreferanse is non-null but no matching sak exists, create one.
    // If saksreferanse is null, the vedtak is sakless (Vedtak.saksReferanse == null).
    val sak =
        if (saksReferanse != null) {
            getSak(saksReferanse) ?: upsertSak(saksReferanse, null, null)
        } else {
            null
        }

    vedtak.add(
        Vedtak(
            referanse = dokumentRef,
            utfall = utfall,
            dato =
                hendelse.hendelsestidspunkt
                    .toInstant()
                    .atZone(java.time.ZoneId.of("Europe/Oslo"))
                    .toLocalDate(),
            saksReferanse = saksReferanse,
        ),
    )

    hendelser.add(
        VedtakFattet(
            tidspunkt = hendelse.hendelsestidspunkt.toInstant(),
            saksReferanse = saksReferanse,
            saksTittel = sak?.tittel,
            utfall = utfall,
            vedtakRef = dokumentRef,
        ),
    )
}
