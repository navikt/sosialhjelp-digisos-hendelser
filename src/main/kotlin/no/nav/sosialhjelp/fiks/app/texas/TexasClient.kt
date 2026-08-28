package no.nav.sosialhjelp.fiks.app.texas

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import no.nav.sosialhjelp.fiks.utils.logger

/**
 * Client for NAIS Texas token sidecar.
 *
 * Supports three operations:
 *  - [getMaskinportenToken]: M2M token for Fiks (Maskinporten, ks:fiks) and Entra M2M
 *  - [getTokenOnBehalfOf]: OBO exchange for PDL and skjermede-personer
 *
 * Token endpoint: NAIS_TOKEN_ENDPOINT (M2M and OBO share one endpoint in Texas)
 */
class TexasClient(
    private val tokenEndpointUrl: String,
) {
    private val log by logger()

    private val httpClient =
        HttpClient {
            install(ContentNegotiation) { jackson() }
        }

    /** Fetch an M2M token. identity_provider defaults to "maskinporten" for Fiks. */
    suspend fun getMaskinportenToken(
        identityProvider: String = "maskinporten",
        target: String = "ks:fiks",
    ): String {
        val response =
            httpClient.post(tokenEndpointUrl) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("identity_provider" to identityProvider, "target" to target))
            }
        return response.body<TokenResponse>().accessToken.also {
            log.debug("Hentet M2M token ($identityProvider) fra Texas")
        }
    }

    /**
     * Fetch an On-Behalf-Of token.
     *
     * @param target the audience/scope (e.g. PDL scope or skjermede-personer scope)
     * @param userToken the inbound user token to exchange
     * @param identityProvider "tokenx" for ID-porten tokens, "entra_id" for Azure AD OBO
     */
    suspend fun getTokenOnBehalfOf(
        target: String,
        userToken: String,
        identityProvider: String,
    ): String {
        val response =
            httpClient.post(tokenEndpointUrl) {
                contentType(ContentType.Application.Json)
                setBody(
                    mapOf(
                        "identity_provider" to identityProvider,
                        "target" to target,
                        "user_token" to userToken,
                    ),
                )
            }
        return response.body<TokenResponse>().accessToken.also {
            log.debug("Hentet OBO token ($identityProvider -> $target) fra Texas")
        }
    }

    private data class TokenResponse(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("expires_in") val expiresIn: Int,
        @JsonProperty("token_type") val tokenType: String,
    )
}
