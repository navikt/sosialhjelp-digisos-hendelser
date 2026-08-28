package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonUtbetaling
import no.nav.sosialhjelp.fiks.domain.Utbetaling
import no.nav.sosialhjelp.fiks.domain.UtbetalingEndret
import no.nav.sosialhjelp.fiks.domain.UtbetalingsStatus
import no.nav.sosialhjelp.fiks.utils.toInstant
import no.nav.sosialhjelp.fiks.utils.toLocalDate
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.sosialhjelp.fiks.event.Utbetaling")

internal fun FoldAccumulator.apply(hendelse: JsonUtbetaling) {
    val prevUtbetaling = getUtbetaling(hendelse.utbetalingsreferanse)
    val annenMottaker = isAnnenMottaker(hendelse)
    val status =
        UtbetalingsStatus.valueOf(
            hendelse.status?.value() ?: JsonUtbetaling.Status.PLANLAGT_UTBETALING.value(),
        )

    val utbetaling =
        Utbetaling(
            referanse = hendelse.utbetalingsreferanse,
            status = status,
            belop = java.math.BigDecimal.valueOf(hendelse.belop ?: 0.0),
            beskrivelse = hendelse.beskrivelse,
            forfallsDato = hendelse.forfallsdato?.toLocalDate(),
            utbetalingsDato = hendelse.utbetalingsdato?.toLocalDate(),
            stoppetDato =
                if (hendelse.status == JsonUtbetaling.Status.STOPPET) {
                    hendelse.hendelsestidspunkt
                        .toInstant()
                        .atZone(java.time.ZoneId.of("Europe/Oslo"))
                        .toLocalDate()
                } else {
                    prevUtbetaling?.stoppetDato
                },
            fom = hendelse.fom?.toLocalDate(),
            tom = hendelse.tom?.toLocalDate(),
            mottaker = hendelse.mottaker,
            annenMottaker = annenMottaker,
            kontonummer = hendelse.kontonummer.takeUnless { annenMottaker },
            utbetalingsmetode = hendelse.utbetalingsmetode,
            saksReferanse = hendelse.saksreferanse,
            datoHendelse = hendelse.hendelsestidspunkt.toInstant(),
        )

    upsertUtbetaling(utbetaling)

    hendelser.add(
        UtbetalingEndret(
            tidspunkt = hendelse.hendelsestidspunkt.toInstant(),
            utbetalingsReferanse = hendelse.utbetalingsreferanse,
            status = status,
        ),
    )
}

/** annenMottaker == null counts as true — fail-safe default. */
private fun isAnnenMottaker(hendelse: JsonUtbetaling) = hendelse.annenMottaker == null || hendelse.annenMottaker
