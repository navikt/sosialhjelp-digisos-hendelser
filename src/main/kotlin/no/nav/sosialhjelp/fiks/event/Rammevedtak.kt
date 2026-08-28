package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.hendelse.JsonRammevedtak

/** Rammevedtak — intentionally a no-op. Vises ikke for bruker. */
@Suppress("UnusedParameter")
internal fun FoldAccumulator.apply(hendelse: JsonRammevedtak) {
    // Gjør ingenting
}
