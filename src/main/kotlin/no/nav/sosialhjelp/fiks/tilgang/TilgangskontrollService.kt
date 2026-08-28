package no.nav.sosialhjelp.fiks.tilgang

import no.nav.sosialhjelp.fiks.app.auth.Caller
import no.nav.sosialhjelp.fiks.app.auth.Fnr
import no.nav.sosialhjelp.fiks.tilgang.pdl.PdlClient
import no.nav.sosialhjelp.fiks.tilgang.pdl.erKode6eller7
import no.nav.sosialhjelp.fiks.tilgang.skjerming.SkjermedePersonerClient
import no.nav.sosialhjelp.fiks.utils.logger

/**
 * Tilgangskontroll — run on every request before any data is returned.
 *
 * Rules:
 *  - PDL kode 6/7: deny on ALL paths (citizen and saksbehandler).
 *  - Skjerming (skjermede-personer-pip): deny on saksbehandler (OBO) path only.
 *    A citizen viewing their own data is never considered "skjermet" in this context.
 *  - Fail-closed: any PDL or skjerming error -> deny (IkkeTilgangException).
 *
 * On denial: throws [IkkeTilgangException] which is mapped to 404 in StatusPages.
 * Reason is ONLY in logs — 404 is returned to avoid leaking gradering status.
 *
 * Audit DENY entries are logged here for the saksbehandler path. The citizen path has no
 * audit entry (a person reading their own data is not a sporingslogg event — D9).
 */
class TilgangskontrollService(
    private val pdlClient: PdlClient,
    private val skjermedePersonerClient: SkjermedePersonerClient,
) {
    private val log by logger()

    /**
     * Check access. Throws [IkkeTilgangException] if access is denied.
     *
     * @param fnr the citizen whose data is being accessed
     * @param caller the authenticated caller
     */
    suspend fun sjekkTilgang(
        fnr: Fnr,
        caller: Caller,
    ) {
        val person =
            runCatching { pdlClient.hentPerson(fnr, caller) }
                .onFailure { log.error("PDL-kall feilet for fnr=[FNR]: ${it.message}") }
                .getOrElse {
                    // fail-closed: any PDL error -> deny
                    if (caller is Caller.Saksbehandler) {
                        log.warn("PDL-feil — nekter tilgang (fail-closed). appNavn=${caller.appNavn}")
                    }
                    throw IkkeTilgangException("PDL utilgjengelig")
                }

        if (person == null) {
            log.warn("Person ikke funnet i PDL")
            throw IkkeTilgangException("Person ikke funnet i PDL")
        }

        if (person.erKode6eller7()) {
            if (caller is Caller.Saksbehandler) {
                log.warn("Tilgang nektet (kode 6/7). navIdent=${caller.navIdent} appNavn=${caller.appNavn}")
            } else {
                log.info("Tilgang nektet (kode 6/7) på citizen path")
            }
            throw IkkeTilgangException("Person har adressebeskyttelse")
        }

        if (caller is Caller.Saksbehandler) {
            val erSkjermet =
                runCatching { skjermedePersonerClient.erPersonSkjermet(fnr, caller) }
                    .onFailure { log.error("Skjerming-kall feilet: ${it.message}") }
                    .getOrElse { throw IkkeTilgangException("Skjerming utilgjengelig") }

            if (erSkjermet) {
                log.warn("Tilgang nektet (skjermet). navIdent=${caller.navIdent} appNavn=${caller.appNavn}")
                throw IkkeTilgangException("Person er skjermet")
            }
        }
    }

    /**
     * Eier-verifisering: confirm that the requested DigisosSak actually belongs to the citizen
     * identified by [expectedFnr], using PDL hentIdenter (historical idents included).
     *
     * Only called on the citizen path. The saksbehandler path verifies ownership by matching
     * the caller-supplied fnr against DigisosSak.sokerFnr.
     */
    suspend fun sjekkEierskap(
        expectedFnr: Fnr,
        sokerFnr: String,
        caller: Caller,
    ) {
        val alleIdenter =
            runCatching { pdlClient.hentIdenter(expectedFnr, caller) }
                .onFailure { log.error("PDL hentIdenter feilet: ${it.message}") }
                .getOrElse { throw IkkeTilgangException("PDL hentIdenter utilgjengelig") }

        if (sokerFnr !in alleIdenter) {
            log.warn("Eierskap-sjekk feilet: sokerFnr matcher ikke PDL-identer for citizen")
            throw IkkeTilgangException("Søknaden tilhører ikke denne brukeren")
        }
    }
}

/** Thrown when access is denied. Always mapped to 404 in StatusPages. */
class IkkeTilgangException(
    message: String,
) : RuntimeException(message)
