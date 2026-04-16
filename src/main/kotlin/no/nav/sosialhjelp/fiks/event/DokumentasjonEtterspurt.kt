package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonDokumentasjonEtterspurt
import no.nav.sbl.soknadsosialhjelp.vedlegg.JsonVedlegg
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.domain.Forvaltningsbrev
import no.nav.sosialhjelp.fiks.domain.Hendelse
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.Oppgave
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.domain.UrlResponse
import no.nav.sosialhjelp.fiks.utils.hentUrlFraFilreferanse
import no.nav.sosialhjelp.fiks.utils.sha256
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(JsonDokumentasjonEtterspurt::class.java.name)

fun InternalDigisosSoker.apply(
    hendelse: JsonDokumentasjonEtterspurt,
    clientProperties: ClientProperties,
) {
    val prevSize = oppgaver.size

    oppgaver =
        hendelse.dokumenter
            .map {
                Oppgave(
                    sha256(it.innsendelsesfrist),
                    it.dokumenttype,
                    it.tilleggsinformasjon,
                    JsonVedlegg.HendelseType.DOKUMENTASJON_ETTERSPURT,
                    it.dokumentreferanse,
                    it.innsendelsesfrist.toLocalDateTime(),
                    hendelse.hendelsestidspunkt.toLocalDateTime(),
                    true,
                    forvaltningsbrev =
                        hendelse.forvaltningsbrev?.let { forvaltningsbrev ->
                            Forvaltningsbrev(
                                hentUrlFraFilreferanse(clientProperties, forvaltningsbrev.referanse),
                                hendelse.hendelsestidspunkt.toLocalDateTime(),
                            )
                        },
                )
            }.toMutableList()

    if (hendelse.dokumenter.isNotEmpty() && hendelse.forvaltningsbrev != null) {
        val url = hentUrlFraFilreferanse(clientProperties, hendelse.forvaltningsbrev.referanse)
        log.info("Hendelse: Dokumentasjon etterspurt. Vi trenger flere opplysninger til søknaden din.")
        historikk.add(
            Hendelse(
                HendelseTekstType.ETTERSPOR_MER_DOKUMENTASJON,
                hendelse.hendelsestidspunkt.toLocalDateTime(),
                UrlResponse(HendelseTekstType.VIS_BREVET_LENKETEKST, url),
            ),
        )
    }

    if (prevSize > 0 && oppgaver.isEmpty() && status != SoknadsStatus.BEHANDLES_IKKE) {
        log.info(
            "Hendelse: Tidspunkt: ${hendelse.hendelsestidspunkt} Dokumentasjon etterspurt. " +
                "Vi har sett på opplysningene dine og vil gi beskjed om vi trenger noe mer fra deg.",
        )
        historikk.add(Hendelse(HendelseTekstType.ETTERSPOR_IKKE_MER_DOKUMENTASJON, hendelse.hendelsestidspunkt.toLocalDateTime(), null))
    }
}
