package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonSaksStatus
import no.nav.sosialhjelp.fiks.domain.Hendelse
import no.nav.sosialhjelp.fiks.domain.HendelseTekstType
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.Sak
import no.nav.sosialhjelp.fiks.domain.SaksStatus
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(JsonSaksStatus::class.java.name)

fun InternalDigisosSoker.apply(hendelse: JsonSaksStatus) {
    val sakForReferanse = saker.firstOrNull { it.referanse == hendelse.referanse }

    if (sakForReferanse != null) {
        sakForReferanse.tittel = hendelse.tittel

        if (hendelse.status != null) {
            val prevStatus = sakForReferanse.saksStatus

            sakForReferanse.saksStatus =
                SaksStatus.valueOf(
                    hendelse.status?.name
                        ?: JsonSaksStatus.Status.UNDER_BEHANDLING.name,
                )

            if (prevStatus != sakForReferanse.saksStatus &&
                (sakForReferanse.saksStatus == SaksStatus.IKKE_INNSYN || sakForReferanse.saksStatus == SaksStatus.BEHANDLES_IKKE)
            ) {
                if (sakForReferanse.tittel != null) {
                    historikk.add(
                        Hendelse(
                            hendelseType = HendelseTekstType.SOKNAD_KAN_IKKE_VISE_STATUS_MED_TITTEL,
                            hendelse.hendelsestidspunkt.toLocalDateTime(),
                            tekstArgument = sakForReferanse.tittel,
                            saksReferanse = hendelse.referanse,
                        ),
                    )
                } else {
                    historikk.add(
                        Hendelse(
                            hendelseType = HendelseTekstType.SOKNAD_KAN_IKKE_VISE_STATUS_UTEN_TITTEL,
                            hendelse.hendelsestidspunkt.toLocalDateTime(),
                            saksReferanse = hendelse.referanse,
                        ),
                    )
                }
            }
            if (sakForReferanse.saksStatus == SaksStatus.UNDER_BEHANDLING &&
                (prevStatus == SaksStatus.IKKE_INNSYN || prevStatus == SaksStatus.BEHANDLES_IKKE)
            ) {
                log.info("Sak har gått fra status ${prevStatus.name} til status ${SaksStatus.UNDER_BEHANDLING.name}.")
            }
        }
    } else {
        val status = SaksStatus.valueOf(hendelse.status?.name ?: JsonSaksStatus.Status.UNDER_BEHANDLING.name)
        saker.add(
            Sak(
                referanse = hendelse.referanse,
                saksStatus = status,
                tittel = hendelse.tittel,
                vedtak = mutableListOf(),
                utbetalinger = mutableListOf(),
            ),
        )
        val hendelsestype: HendelseTekstType? =
            when (status) {
                SaksStatus.UNDER_BEHANDLING ->
                    if (hendelse.tittel != null) {
                        HendelseTekstType.SAK_UNDER_BEHANDLING_MED_TITTEL
                    } else {
                        HendelseTekstType.SAK_UNDER_BEHANDLING_UTEN_TITTEL
                    }

                SaksStatus.BEHANDLES_IKKE, SaksStatus.IKKE_INNSYN ->
                    if (hendelse.tittel != null) {
                        HendelseTekstType.SAK_KAN_IKKE_VISE_STATUS_MED_TITTEL
                    } else {
                        HendelseTekstType.SAK_KAN_IKKE_VISE_STATUS_UTEN_TITTEL
                    }

                else -> null
            }
        if (hendelsestype != null) {
            historikk.add(
                Hendelse(
                    hendelsestype,
                    hendelse.hendelsestidspunkt.toLocalDateTime(),
                    tekstArgument = hendelse.tittel,
                    saksReferanse = hendelse.referanse,
                ),
            )
        }
    }
    log.info("Hendelse: Tidspunkt: ${hendelse.hendelsestidspunkt} Sakstatus: ${hendelse.status?.name ?: "null"}")
}
