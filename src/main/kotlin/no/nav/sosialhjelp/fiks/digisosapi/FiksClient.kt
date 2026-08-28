package no.nav.sosialhjelp.fiks.digisosapi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.api.fiks.exceptions.FiksClientException
import no.nav.sosialhjelp.api.fiks.exceptions.FiksNotFoundException
import no.nav.sosialhjelp.api.fiks.exceptions.FiksServerException
import no.nav.sosialhjelp.fiks.app.auth.Caller
import no.nav.sosialhjelp.fiks.app.texas.TexasClient
import no.nav.sosialhjelp.fiks.utils.logger

private const val HEADER_INTEGRASJON_ID = "IntegrasjonId"
private const val HEADER_INTEGRASJON_PASSORD = "IntegrasjonPassord"

/**
 * HTTP client for all Fiks Digisos API calls.
 *
 * - Citizen path ([Caller.Citizen]): uses the raw ID-porten token, hits /digisos/api/v1/soknader/{id}
 * - Saksbehandler path ([Caller.Saksbehandler]): uses Maskinporten (via Texas M2M), hits /digisos/api/v1/nav/soknader/{id}
 *
 * Use [FiksService] (which handles caching and mutex dedup) rather than calling this directly.
 */
class FiksClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val integrasjonId: String,
    private val integrasjonPassord: String,
    private val texasClient: TexasClient,
) {
    private val log by logger()

    suspend fun hentDigisosSak(
        digisosId: String,
        caller: Caller,
    ): DigisosSak {
        val (path, token) = pathAndToken(caller, FiksPaths.SOKNAD, FiksPaths.NAV_SOKNAD) { it.replace("{digisosId}", digisosId) }
        val url = "$baseUrl$path"
        val sporingsIdQuery = sporingsIdQuery(caller)

        val response =
            httpClient.get("$url$sporingsIdQuery") {
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HEADER_INTEGRASJON_ID, integrasjonId)
                header(HEADER_INTEGRASJON_PASSORD, integrasjonPassord)
            }

        return when {
            response.status == HttpStatusCode.NotFound ->
                throw FiksNotFoundException("Fiks - hentDigisosSak feilet - 404 for digisosId=$digisosId", null)
            response.status.value in 400..499 ->
                throw FiksClientException(response.status.value, "Fiks - hentDigisosSak feilet - ${response.status}", null)
            !response.status.isSuccess() ->
                throw FiksServerException(response.status.value, "Fiks - hentDigisosSak feilet - ${response.status}", null)
            else -> response.body<DigisosSak>().also { log.debug("Hentet DigisosSak fra Fiks for digisosId=$digisosId") }
        }
    }

    /**
     * Citizen path: GET /soknader/soknader — fetches all søknader for the user identified by the token's `pid`.
     * For the saksbehandler path, use [hentAlleDigisosSakerForFnr] instead.
     */
    suspend fun hentAlleDigisosSaker(caller: Caller.Citizen): List<DigisosSak> {
        val url = "$baseUrl${FiksPaths.ALLE_SOKNADER}"
        val response =
            httpClient.get(url) {
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${caller.rawIdportenToken}")
                header(HEADER_INTEGRASJON_ID, integrasjonId)
                header(HEADER_INTEGRASJON_PASSORD, integrasjonPassord)
            }
        return parseSakerResponse(response, "hentAlleDigisosSaker (citizen)")
    }

    suspend fun hentAlleDigisosSakerForFnr(
        fnr: String,
        caller: Caller.Saksbehandler,
    ): List<DigisosSak> {
        val maskinportenToken = texasClient.getMaskinportenToken()
        val url = "$baseUrl${FiksPaths.NAV_ALLE_SOKNADER}"
        val sporingsIdQuery = sporingsIdQuery(caller)
        val response =
            httpClient.post("$url$sporingsIdQuery") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $maskinportenToken")
                header(HEADER_INTEGRASJON_ID, integrasjonId)
                header(HEADER_INTEGRASJON_PASSORD, integrasjonPassord)
                setBody(mapOf("fnr" to fnr))
            }
        return parseSakerResponse(response, "hentAlleDigisosSakerForFnr (saksbehandler)")
    }

    suspend fun <T : Any> hentDokument(
        digisosId: String,
        dokumentlagerId: String,
        requestedClass: Class<out T>,
        caller: Caller,
    ): T {
        val (path, token) =
            pathAndToken(
                caller,
                FiksPaths.DOKUMENT,
                FiksPaths.NAV_DOKUMENT,
            ) {
                it.replace("{digisosId}", digisosId).replace("{dokumentlagerId}", dokumentlagerId)
            }
        val sporingsIdQuery = sporingsIdQuery(caller)
        val url = "$baseUrl$path$sporingsIdQuery"
        val response =
            httpClient.get(url) {
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HEADER_INTEGRASJON_ID, integrasjonId)
                header(HEADER_INTEGRASJON_PASSORD, integrasjonPassord)
            }

        return when {
            response.status.value in 400..499 ->
                throw FiksClientException(response.status.value, "Fiks - hentDokument feilet - ${response.status}", null)
            !response.status.isSuccess() ->
                throw FiksServerException(response.status.value, "Fiks - hentDokument feilet - ${response.status}", null)
            else -> {
                val typeInfo =
                    io.ktor.util.reflect
                        .TypeInfo(requestedClass.kotlin)
                @Suppress("UNCHECKED_CAST")
                (response.body<Any>(typeInfo) as T).also {
                    log.debug("Hentet dokument (${requestedClass.simpleName}) fra Fiks")
                }
            }
        }
    }

    // --- helpers ---

    /** Called from document fetching where we have a raw token but no full Caller object. */
    suspend fun <T : Any> hentDokumentMedToken(
        digisosId: String,
        dokumentlagerId: String,
        requestedClass: Class<out T>,
        token: String,
    ): T {
        val path =
            FiksPaths.DOKUMENT
                .replace("{digisosId}", digisosId)
                .replace("{dokumentlagerId}", dokumentlagerId)
        val url = "$baseUrl$path"
        val response =
            httpClient.get(url) {
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HEADER_INTEGRASJON_ID, integrasjonId)
                header(HEADER_INTEGRASJON_PASSORD, integrasjonPassord)
            }
        return when {
            response.status.value in 400..499 ->
                throw FiksClientException(response.status.value, "Fiks - hentDokument feilet - ${response.status}", null)
            !response.status.isSuccess() ->
                throw FiksServerException(response.status.value, "Fiks - hentDokument feilet - ${response.status}", null)
            else -> {
                val typeInfo =
                    io.ktor.util.reflect
                        .TypeInfo(requestedClass.kotlin)
                @Suppress("UNCHECKED_CAST")
                response.body<Any>(typeInfo) as T
            }
        }
    }

    /**
     * Select the correct path and token based on the caller type.
     * [citizenPath] is used for ID-porten callers, [navPath] for Maskinporten.
     * [transform] allows path parameter substitution.
     */
    private suspend fun pathAndToken(
        caller: Caller,
        citizenPath: String,
        navPath: String,
        transform: (String) -> String = { it },
    ): Pair<String, String> =
        when (caller) {
            is Caller.Citizen -> transform(citizenPath) to caller.rawIdportenToken
            is Caller.Saksbehandler -> transform(navPath) to texasClient.getMaskinportenToken()
            else -> error("Unknown caller type: ${caller::class.simpleName}")
        }

    /** sporingsId is derived from the OTel trace id when available; appended only on /nav/ calls. */
    private fun sporingsIdQuery(caller: Caller): String =
        when (caller) {
            is Caller.Citizen -> ""
            is Caller.Saksbehandler -> {
                val traceId =
                    io.opentelemetry.api.trace.Span
                        .current()
                        .spanContext.traceId
                if (traceId.isNotBlank() && traceId != "00000000000000000000000000000000") {
                    "?sporingsId=$traceId"
                } else {
                    "?sporingsId=${java.util.UUID.randomUUID()}"
                }
            }
            else -> ""
        }

    private suspend fun parseSakerResponse(
        response: io.ktor.client.statement.HttpResponse,
        context: String,
    ): List<DigisosSak> =
        when {
            response.status.value in 400..499 ->
                throw FiksClientException(
                    response.status.value,
                    "Fiks - $context feilet - ${response.status}",
                    null,
                )
            !response.status.isSuccess() ->
                throw FiksServerException(
                    response.status.value,
                    "Fiks - $context feilet - ${response.status}",
                    null,
                )
            else ->
                response.body<List<DigisosSak>>().also {
                    log.info("Hentet ${it.size} DigisosSaker fra Fiks ($context)")
                }
        }
}
