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

/** Texas-client for å hente Maskinporten-token (M2M) via NAIS token-endepunkt. */
class TexasClient(
    private val tokenEndpointUrl: String,
) {
    private val log by logger()

    private val httpClient =
        HttpClient {
            install(ContentNegotiation) { jackson() }
        }

    suspend fun getMaskinportenToken(): String {
        val response =
            httpClient.post(tokenEndpointUrl) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("identity_provider" to "maskinporten", "target" to "ks:fiks"))
            }
        return response.body<TokenResponse>().accessToken.also {
            log.debug("Hentet Maskinporten-token fra Texas")
        }
    }

    private data class TokenResponse(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("expires_in") val expiresIn: Int,
        @JsonProperty("token_type") val tokenType: String,
    )
}
