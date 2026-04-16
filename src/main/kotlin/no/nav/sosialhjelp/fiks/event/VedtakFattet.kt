package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonDokumentlagerFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.filreferanse.JsonSvarUtFilreferanse
import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonVedtakFattet
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.digisossak.saksstatus.DEFAULT_SAK_TITTEL
import no.nav.sosialhjelp.fiks.domain.Hendelse
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.Sak
import no.nav.sosialhjelp.fiks.domain.SaksStatus
import no.nav.sosialhjelp.fiks.domain.UrlResponse
import no.nav.sosialhjelp.fiks.domain.UtfallVedtak
import no.nav.sosialhjelp.fiks.domain.Vedtak
import no.nav.sosialhjelp.fiks.utils.hentUrlFraFilreferanse
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(JsonVedtakFattet::class.java.name)

fun InternalDigisosSoker.apply(
    hendelse: JsonVedtakFattet,
    clientProperties: ClientProperties,
) {
    val utfallString = hendelse.utfall?.name
    val utfall = utfallString?.let { UtfallVedtak.valueOf(it) }
    val vedtaksfilUrl = hentUrlFraFilreferanse(clientProperties, hendelse.vedtaksfil.referanse)

    val id =
        when (val referanse = hendelse.vedtaksfil.referanse) {
            is JsonDokumentlagerFilreferanse -> referanse.id
            is JsonSvarUtFilreferanse -> referanse.id
            else -> error("Ikke støttet referansetype ${referanse.type}")
        }
    val vedtakFattet =
        Vedtak(
            id,
            utfall,
            vedtaksfilUrl,
            hendelse.hendelsestidspunkt.toLocalDateTime().toLocalDate(),
        )

    var sakForReferanse = saker.firstOrNull { it.referanse == hendelse.saksreferanse || it.referanse == "default" }

    if (sakForReferanse == null) {
        sakForReferanse =
            Sak(
                referanse = hendelse.saksreferanse ?: "default",
                saksStatus = SaksStatus.UNDER_BEHANDLING,
                tittel = DEFAULT_SAK_TITTEL,
                vedtak = mutableListOf(),
                utbetalinger = mutableListOf(),
            )
        saker.add(sakForReferanse)
    }
    sakForReferanse.vedtak.add(vedtakFattet)

    log.info("Hendelse: Tidspunkt: ${hendelse.hendelsestidspunkt} Vedtak fattet. <skjult tittel> er ferdigbehandlet")
    if (sakForReferanse.tittel != null) {
        historikk.add(
            Hendelse(
                HendelseTekstType.SAK_FERDIGBEHANDLET_MED_TITTEL,
                hendelse.hendelsestidspunkt.toLocalDateTime(),
                UrlResponse(HendelseTekstType.VIS_BREVET_LENKETEKST, vedtaksfilUrl),
                tekstArgument = sakForReferanse.tittel,
                saksReferanse = hendelse.saksreferanse,
            ),
        )
    } else {
        historikk.add(
            Hendelse(
                HendelseTekstType.SAK_FERDIGBEHANDLET_UTEN_TITTEL,
                hendelse.hendelsestidspunkt.toLocalDateTime(),
                UrlResponse(HendelseTekstType.VIS_BREVET_LENKETEKST, vedtaksfilUrl),
                saksReferanse = hendelse.saksreferanse,
            ),
        )
    }
}
