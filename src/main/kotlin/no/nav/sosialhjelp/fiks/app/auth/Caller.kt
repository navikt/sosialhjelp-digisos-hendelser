package no.nav.sosialhjelp.fiks.app.auth

/**
 * Represents the authenticated caller of the service.
 *
 * Two modes:
 *  - [Citizen]: an end-user authenticated via ID-porten. The raw ID-porten token is carried
 *    so it can be forwarded verbatim to the Fiks citizen endpoints.
 *    fnr is taken from the "pid" claim.
 *  - [Saksbehandler]: an internal user (NAV-ansatt) authenticated via Entra ID (Azure AD) OBO.
 *    Fiks is reached via Maskinporten (machine-to-machine). fnr of the citizen they are looking
 *    up is NOT in the token - it must be supplied by the caller in the request body.
 *
 * NOTE: fiks-service intentionally accepts an ID-porten token whose aud is not itself.
 * The mitigations are: client_id allowlist in CallerRegistry, acr check, and
 * accessPolicy.inbound on the nais manifest. See docs/domenetjeneste-plan.md section 5.
 */
sealed interface Caller {
    val appNavn: String

    data class Citizen(
        val pid: Fnr,
        /** Raw ID-porten token - forwarded verbatim to Fiks /soknader/ endpoints. */
        val rawIdportenToken: String,
        override val appNavn: String,
    ) : Caller

    data class Saksbehandler(
        val navIdent: String,
        val oboToken: String,
        override val appNavn: String,
        val behandlingsnummer: String,
    ) : Caller
}

/** Validated Norwegian national identity number (fnr/dnr). */
@JvmInline
value class Fnr(
    val value: String,
) {
    init {
        require(value.length == 11 && value.all { it.isDigit() }) {
            "Ugyldig fnr-format"
        }
    }
}
