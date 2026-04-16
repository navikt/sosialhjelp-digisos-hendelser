package no.nav.sosialhjelp.fiks.digisosapi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.api.fiks.exceptions.FiksClientException
import no.nav.sosialhjelp.api.fiks.exceptions.FiksNotFoundException
import no.nav.sosialhjelp.api.fiks.exceptions.FiksServerException
import no.nav.sosialhjelp.fiks.utils.logger

private const val HEADER_INTEGRASJON_ID = "IntegrasjonId"
private const val HEADER_INTEGRASJON_PASSORD = "IntegrasjonPassord"

/**
 * Ansvarlig for all HTTP-kommunikasjon mot Fiks API.
 * Bruk FiksService (som håndterer caching og mutex) fremfor direkte kall.
 */
class FiksClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val integrasjonId: String,
    private val integrasjonPassord: String,
) {
    private val log by logger()

    suspend fun hentDigisosSak(
        digisosId: String,
        token: String,
    ): DigisosSak {
        log.debug("Forsøker å hente DigisosSak for digisosId=$digisosId")
        val url = "$baseUrl${FiksPaths.PATH_DIGISOSSAK.replace("{digisosId}", digisosId)}"
        val response =
            httpClient.get(url) {
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HEADER_INTEGRASJON_ID, integrasjonId)
                header(HEADER_INTEGRASJON_PASSORD, integrasjonPassord)
            }

        return when {
            response.status == HttpStatusCode.NotFound ->
                throw FiksNotFoundException("Fiks - hentDigisosSak feilet - 404 Not Found for digisosId=$digisosId", null)
            response.status.value in 400..499 ->
                throw FiksClientException(response.status.value, "Fiks - hentDigisosSak feilet - ${response.status}", null)
            !response.status.isSuccess() ->
                throw FiksServerException(response.status.value, "Fiks - hentDigisosSak feilet - ${response.status}", null)
            else -> response.body<DigisosSak>().also { log.debug("Hentet DigisosSak fra Fiks") }
        }
    }

    suspend fun hentAlleDigisosSaker(token: String): List<DigisosSak> {
        log.debug("Forsøker å hente alle DigisosSaker")
        val url = "$baseUrl${FiksPaths.PATH_ALLE_DIGISOSSAKER}"
        val response =
            httpClient.get(url) {
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HEADER_INTEGRASJON_ID, integrasjonId)
                header(HEADER_INTEGRASJON_PASSORD, integrasjonPassord)
            }

        return when {
            response.status.value in 400..499 ->
                throw FiksClientException(response.status.value, "Fiks - hentAlleDigisosSaker feilet - ${response.status}", null)
            !response.status.isSuccess() ->
                throw FiksServerException(response.status.value, "Fiks - hentAlleDigisosSaker feilet - ${response.status}", null)
            else -> response.body<List<DigisosSak>>().also { log.debug("Hentet ${0} DigisosSaker fra Fiks") }
        }
    }

    suspend fun <T : Any> hentDokument(
        digisosId: String,
        dokumentlagerId: String,
        requestedClass: Class<out T>,
        token: String,
    ): T {
        log.debug("Forsøker å hente dokument for digisosId=$digisosId, dokumentlagerId=$dokumentlagerId")
        val url =
            "$baseUrl${FiksPaths.PATH_DOKUMENT
                .replace("{digisosId}", digisosId)
                .replace("{dokumentlagerId}", dokumentlagerId)}"
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

    companion object {
        private val log by logger()
    }
}
