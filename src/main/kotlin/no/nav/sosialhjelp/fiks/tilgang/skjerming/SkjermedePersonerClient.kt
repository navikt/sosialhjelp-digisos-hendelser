package no.nav.sosialhjelp.fiks.tilgang.skjerming

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import no.nav.sosialhjelp.fiks.app.auth.Caller
import no.nav.sosialhjelp.fiks.app.auth.Fnr
import no.nav.sosialhjelp.fiks.app.texas.TexasClient
import no.nav.sosialhjelp.fiks.utils.logger

/**
 * Client for skjermede-personer-pip.
 *
 * Only called on the saksbehandler (OBO) path — a citizen looking at their own data is never
 * considered "skjermet" in the access-control sense (skjerming protects employees from being
 * looked up by other saksbehandlere, not self-service).
 *
 * Fail-closed: any exception (network, parse error, unexpected response) is treated as "skjermet".
 *
 * Response parsing fix vs modia-api: modia does `"false" != response` which treats any transport
 * oddity (empty body, non-boolean JSON) as skjermet but without a clear error. Here we parse
 * a boolean explicitly and throw — so any oddity also results in deny, but the error is logged.
 */
class SkjermedePersonerClient(
    private val httpClient: HttpClient,
    private val endpointUrl: String,
    private val texasClient: TexasClient,
    private val scope: String,
) {
    private val log by logger()

    suspend fun erPersonSkjermet(
        fnr: Fnr,
        caller: Caller.Saksbehandler,
    ): Boolean {
        return runCatching {
            val oboToken =
                texasClient.getTokenOnBehalfOf(
                    target = scope,
                    userToken = caller.oboToken,
                    identityProvider = "entra_id",
                )
            val response: String =
                httpClient
                    .post("$endpointUrl/skjermet") {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboToken")
                        setBody(mapOf("personident" to fnr.value))
                    }.body<String>()

            // Explicit boolean parse — fail-closed on anything unexpected
            when (response.trim().lowercase()) {
                "false" -> false
                "true" -> true
                else -> {
                    log.error("Uventet respons fra skjermede-personer: '$response' — behandler som skjermet")
                    throw IllegalStateException("Uventet respons fra skjermede-personer: $response")
                }
            }
        }.onFailure { log.error("Kall til skjermede-personer feilet: ${it.message}") }
            .getOrElse { true } // fail-closed
    }
}
