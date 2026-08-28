package no.nav.sosialhjelp.fiks.tilgang.pdl

import no.nav.sosialhjelp.fiks.app.auth.Caller
import no.nav.sosialhjelp.fiks.app.texas.TexasClient

/**
 * Strategy for obtaining a PDL token depending on the caller type.
 *
 * See docs/domenetjeneste-plan.md §4 (S1 spike) for the background.
 * Two implementations:
 *  - [TokenXOnBehalfOf]: exchanges the caller's token via tokendings/tokenx. Works only if
 *    tokendings accepts a subject_token where client_id != this app's own client_id.
 *    Use this for the citizen path if the S1 spike confirms tokendings allows it.
 *  - [EntraM2M]: uses fiks-service's own Entra client-credentials token for PDL.
 *    Use this as fallback for the citizen path if tokenX exchange is rejected.
 *    Requires personvern sign-off: PDL will see a machine identity, not the user.
 *
 * Swap between the two by changing which implementation is provided at DI time.
 */
interface PdlTokenStrategy {
    suspend fun hentToken(caller: Caller): String
}

/** Exchange the caller's token via Texas BEHALF_OF. */
class TokenXOnBehalfOf(
    private val texasClient: TexasClient,
    private val pdlScope: String,
) : PdlTokenStrategy {
    override suspend fun hentToken(caller: Caller): String =
        when (caller) {
            is Caller.Citizen ->
                texasClient.getTokenOnBehalfOf(
                    target = pdlScope,
                    userToken = caller.rawIdportenToken,
                    identityProvider = "tokenx",
                )
            is Caller.Saksbehandler ->
                texasClient.getTokenOnBehalfOf(
                    target = pdlScope,
                    userToken = caller.oboToken,
                    identityProvider = "entra_id",
                )
        }
}

/** Use fiks-service's own Entra M2M (client credentials) token for PDL. */
class EntraM2M(
    private val texasClient: TexasClient,
    private val pdlScope: String,
) : PdlTokenStrategy {
    override suspend fun hentToken(caller: Caller): String =
        texasClient.getMaskinportenToken(
            identityProvider = "entra_id",
            target = pdlScope,
        )
}
