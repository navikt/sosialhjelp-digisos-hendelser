package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonForelopigSvar
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.domain.ForelopigSvar
import no.nav.sosialhjelp.fiks.domain.Hendelse
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.UrlResponse
import no.nav.sosialhjelp.fiks.utils.hentUrlFraFilreferanse
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(JsonForelopigSvar::class.java.name)

fun InternalDigisosSoker.apply(
    hendelse: JsonForelopigSvar,
    clientProperties: ClientProperties,
) {
    forelopigSvar =
        ForelopigSvar(
            true,
            hentUrlFraFilreferanse(clientProperties, hendelse.forvaltningsbrev.referanse),
            hendelse.hendelsestidspunkt.toLocalDateTime(),
        )

    log.info(
        "Hendelse: Tidspunkt: ${hendelse.hendelsestidspunkt} Forelopig svar. Du har fått et brev om saksbehandlingstiden for søknaden din.",
    )
    historikk.add(
        Hendelse(
            HendelseTekstType.BREV_OM_SAKSBEANDLINGSTID,
            hendelse.hendelsestidspunkt.toLocalDateTime(),
            UrlResponse(
                HendelseTekstType.VIS_BREVET_LENKETEKST,
                hentUrlFraFilreferanse(clientProperties, hendelse.forvaltningsbrev.referanse),
            ),
        ),
    )
}
