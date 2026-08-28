package no.nav.sosialhjelp.fiks.digisosapi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.api.fiks.exceptions.FiksClientException
import no.nav.sosialhjelp.api.fiks.exceptions.FiksServerException
import no.nav.sosialhjelp.fiks.app.auth.Caller
import no.nav.sosialhjelp.fiks.app.texas.TexasClient
import no.nav.sosialhjelp.fiks.utils.logger
import org.slf4j.LoggerFactory

private const val HEADER_INTEGRASJON_ID = "IntegrasjonId"
private const val HEADER_INTEGRASJON_PASSORD = "IntegrasjonPassord"
private val log = LoggerFactory.getLogger("no.nav.sosialhjelp.fiks.digisosapi.BulkFiksClient")

/**
 * Request body for the bulk dokumenter endpoint.
 * Each entry is a (fiksDigisosId, dokumentlagerId) pair.
 */
data class BulkDokumentRequest(
    val dokumenter: List<BulkDokumentEntry>,
)

data class BulkDokumentEntry(
    val digisosId: String,
    val dokumentlagerId: String,
)

/**
 * Result of a bulk dokumenter fetch.
 * Key = fiksDigisosId, Value = parsed JsonDigisosSoker (null if failed for that sak).
 */
typealias BulkDokumentResult = Map<String, JsonDigisosSoker?>

/**
 * Fetches multiple JsonDigisosSoker documents in a single Fiks API call.
 *
 * Fiks API: POST /digisos/api/v1/soknader/dokumenter
 * Accept: multipart/mixed
 * Body: {"dokumenter": [{"digisosId": "...", "dokumentlagerId": "..."}, ...]}
 *
 * Response: multipart/mixed where each part:
 *   Content-Disposition: form-data; name="${fiksDigisosId}_${dokumentlagerId}"
 *   Content-Type: application/json
 *   Body: serialized JsonDigisosSoker
 *
 * This is used for saksoversikt to avoid N sequential document fetches.
 */
class BulkFiksClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val integrasjonId: String,
    private val integrasjonPassord: String,
    private val texasClient: TexasClient,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
) {
    private val log by logger()

    suspend fun hentDokumenterBulk(
        saker: List<DigisosSak>,
        caller: Caller,
    ): BulkDokumentResult {
        val eligible =
            saker.filter {
                it.digisosSoker?.metadata != null && it.digisosSoker?.timestampSistOppdatert != null
            }
        if (eligible.isEmpty()) return emptyMap()

        val dokumenter =
            eligible.map { sak ->
                BulkDokumentEntry(
                    digisosId = sak.fiksDigisosId,
                    dokumentlagerId = sak.digisosSoker!!.metadata!!,
                )
            }

        val token =
            when (caller) {
                is Caller.Citizen -> caller.rawIdportenToken
                is Caller.Saksbehandler -> texasClient.getMaskinportenToken()
                else -> error("Unknown caller type")
            }

        val url = "$baseUrl${FiksPaths.DOKUMENTER_BULK}"

        val response =
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                accept(ContentType.parse("multipart/mixed"))
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HEADER_INTEGRASJON_ID, integrasjonId)
                header(HEADER_INTEGRASJON_PASSORD, integrasjonPassord)
                setBody(BulkDokumentRequest(dokumenter))
            }

        return when {
            response.status.value in 400..499 ->
                throw FiksClientException(response.status.value, "Fiks - bulk dokumenter feilet - ${response.status}", null)
            !response.status.isSuccess() ->
                throw FiksServerException(response.status.value, "Fiks - bulk dokumenter feilet - ${response.status}", null)
            else -> parseMultipartResponse(response.bodyAsChannel(), eligible)
        }
    }

    /**
     * Parses a multipart/mixed response from Fiks.
     *
     * Each part header is of the form:
     *   --<boundary>\r\n
     *   Content-Disposition: form-data; name="${fiksDigisosId}_${dokumentlagerId}"\r\n
     *   Content-Type: application/json\r\n
     *   \r\n
     *   <json body>\r\n
     *
     * We extract the fiksDigisosId from the part name (it's the prefix before the first '_').
     */
    private suspend fun parseMultipartResponse(
        channel: ByteReadChannel,
        eligible: List<DigisosSak>,
    ): BulkDokumentResult {
        val result = mutableMapOf<String, JsonDigisosSoker?>()

        // Build a lookup: dokumentlagerId -> fiksDigisosId
        val idLookup =
            eligible.associate {
                "${it.fiksDigisosId}_${it.digisosSoker!!.metadata}" to it.fiksDigisosId
            }

        try {
            val bytes = channel.readRemaining().readByteArray()
            val bodyText = String(bytes, Charsets.UTF_8)

            // Split on boundaries (the boundary value is in the Content-Type header but we parse pragmatically)
            val parts = bodyText.split(Regex("--[^\r\n]+\r\n")).drop(1)

            for (part in parts) {
                if (part.trim() == "--" || part.isBlank()) continue

                // Find the blank line separating headers from body
                val headerBodySplit = part.indexOf("\r\n\r\n")
                if (headerBodySplit < 0) continue

                val headers = part.substring(0, headerBodySplit)
                val body = part.substring(headerBodySplit + 4).trimEnd('\r', '\n', '-').trim()

                // Extract name from Content-Disposition header
                val nameMatch = Regex("""name="([^"]+)"""").find(headers)
                val partName = nameMatch?.groupValues?.get(1) ?: continue

                val fiksDigisosId = idLookup[partName] ?: partName.split("_").firstOrNull() ?: continue

                val jsonDigisosSoker =
                    runCatching {
                        objectMapper.readValue(body, JsonDigisosSoker::class.java)
                    }.onFailure {
                        log.warn("Klarte ikke parse JsonDigisosSoker for part '$partName': ${it.message}")
                    }.getOrNull()

                result[fiksDigisosId] = jsonDigisosSoker
            }
        } catch (e: Exception) {
            log.error("Feil ved parsing av multipart/mixed respons: ${e.message}", e)
        }

        log.info("Bulk: parsed ${result.size} av ${eligible.size} dokumenter")
        return result
    }
}
