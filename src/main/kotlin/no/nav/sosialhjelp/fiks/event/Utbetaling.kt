package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonUtbetaling
import no.nav.sosialhjelp.fiks.domain.InternalDigisosSoker
import no.nav.sosialhjelp.fiks.domain.Utbetaling
import no.nav.sosialhjelp.fiks.domain.UtbetalingsStatus
import no.nav.sosialhjelp.fiks.utils.toLocalDate
import no.nav.sosialhjelp.fiks.utils.toLocalDateTime
import org.slf4j.LoggerFactory
import java.math.BigDecimal

private val log = LoggerFactory.getLogger(JsonUtbetaling::class.java.name)

fun InternalDigisosSoker.apply(hendelse: JsonUtbetaling) {
    if (hendelse.fom == null) log.info("utbetalingens start-periode (fom) er null")
    if (hendelse.tom == null) log.info("utbetalingens slutt-periode (tom) er null")
    if (hendelse.status == null) log.info("utbetalingsstatus er null")
    if (hendelse.status == JsonUtbetaling.Status.PLANLAGT_UTBETALING) log.info("utbetalingsstatus er PLANLAGT_UTBETALING")

    log.info("Hendelse: Tidspunkt: ${hendelse.hendelsestidspunkt} Utbetaling. Status: ${hendelse.status?.name ?: "null"}")

    val gammelUtbetaling = utbetalinger.firstOrNull { it.referanse == hendelse.utbetalingsreferanse }
    val utbetaling =
        Utbetaling(
            referanse = hendelse.utbetalingsreferanse,
            status =
                UtbetalingsStatus.valueOf(
                    hendelse.status?.value()
                        ?: JsonUtbetaling.Status.PLANLAGT_UTBETALING.value(),
                ),
            belop = BigDecimal.valueOf(hendelse.belop ?: 0.0),
            beskrivelse = hendelse.beskrivelse,
            forfallsDato = hendelse.forfallsdato?.toLocalDate(),
            utbetalingsDato = hendelse.utbetalingsdato?.toLocalDate(),
            stoppetDato =
                if (hendelse.status == JsonUtbetaling.Status.STOPPET) {
                    hendelse.hendelsestidspunkt.toLocalDateTime().toLocalDate()
                } else {
                    gammelUtbetaling?.stoppetDato
                },
            fom = hendelse.fom?.toLocalDate(),
            tom = hendelse.tom?.toLocalDate(),
            mottaker = hendelse.mottaker,
            annenMottaker = isAnnenMottaker(hendelse),
            kontonummer = hendelse.kontonummer.takeUnless { isAnnenMottaker(hendelse) },
            utbetalingsmetode = hendelse.utbetalingsmetode,
            vilkar = mutableListOf(),
            dokumentasjonkrav = mutableListOf(),
            datoHendelse = hendelse.hendelsestidspunkt.toLocalDateTime(),
        )

    val sakForReferanse =
        saker.firstOrNull { it.referanse == hendelse.saksreferanse }
            ?: saker.firstOrNull { it.referanse == "default" }

    sakForReferanse?.utbetalinger?.removeIf { t -> t.referanse == utbetaling.referanse }
    sakForReferanse?.utbetalinger?.add(utbetaling)
    utbetalinger.removeIf { t -> t.referanse == utbetaling.referanse }
    utbetalinger.add(utbetaling)
}

private fun isAnnenMottaker(hendelse: JsonUtbetaling) = hendelse.annenMottaker == null || hendelse.annenMottaker
