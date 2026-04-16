package no.nav.sosialhjelp.fiks.event

import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sbl.soknadsosialhjelp.soknad.JsonSoknad
import no.nav.sosialhjelp.api.fiks.DigisosSak

interface InnsynService {
    suspend fun hentJsonDigisosSoker(digisosSak: DigisosSak): JsonDigisosSoker?

    suspend fun hentOriginalSoknad(digisosSak: DigisosSak): JsonSoknad?
}
