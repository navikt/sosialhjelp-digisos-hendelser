package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSoknadsStatus
import no.nav.sosialhjelp.fiks.domain.Hendelse
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.SoknadsStatus
import no.nav.sosialhjelp.fiks.event.EventService.Companion.stripEnhetsnavnForKommune
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(JsonSoknadsStatus::class.java.name)

fun InternalDigisosSoker.apply(hendelse: JsonSoknadsStatus) {
    status = SoknadsStatus.valueOf(hendelse.status.name)

    val hendelseMedTittel =
        when (hendelse.status) {
            JsonSoknadsStatus.Status.MOTTATT -> {
                val navEnhetsnavn = soknadsmottaker?.navEnhetsnavn

                if (navEnhetsnavn == null) {
                    Hendelse(
                        HendelseTekstType.SOKNAD_MOTTATT_UTEN_KOMMUNENAVN,
                        hendelse.hendelsestidspunkt.toLocalDateTime(),
                    )
                } else {
                    Hendelse(
                        HendelseTekstType.SOKNAD_MOTTATT_MED_KOMMUNENAVN,
                        hendelse.hendelsestidspunkt.toLocalDateTime(),
                        tekstArgument = stripEnhetsnavnForKommune(navEnhetsnavn),
                    )
                }
            }

            JsonSoknadsStatus.Status.UNDER_BEHANDLING ->
                Hendelse(
                    HendelseTekstType.SOKNAD_UNDER_BEHANDLING,
                    hendelse.hendelsestidspunkt.toLocalDateTime(),
                )

            JsonSoknadsStatus.Status.FERDIGBEHANDLET ->
                Hendelse(
                    HendelseTekstType.SOKNAD_FERDIGBEHANDLET,
                    hendelse.hendelsestidspunkt.toLocalDateTime(),
                )

            JsonSoknadsStatus.Status.BEHANDLES_IKKE ->
                Hendelse(
                    HendelseTekstType.SOKNAD_BEHANDLES_IKKE,
                    hendelse.hendelsestidspunkt.toLocalDateTime(),
                )

            else -> throw RuntimeException("Statustype ${hendelse.status.value()} mangler mapping")
        }

    log.info(
        "Hendelse: Tidspunkt: ${hendelse.hendelsestidspunkt} " +
            "Søknadsstatus: ${hendelse.status} " +
            "Tittel: ${hendelseMedTittel.hendelseType} " +
            "NavEnhetsnavn: ${hendelseMedTittel.tekstArgument ?: ""}",
    )
    historikk.add(hendelseMedTittel)
}
