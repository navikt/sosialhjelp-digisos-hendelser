package no.nav.sosialhjelp.fiks.kommuneinfo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.sosialhjelp.fiks.app.texas.TexasClient
import no.nav.sosialhjelp.fiks.digisosapi.FiksPaths
import no.nav.sosialhjelp.fiks.utils.logger

@JsonIgnoreProperties(ignoreUnknown = true)
data class KommuneInfo(
    val kommunenummer: String,
    val kanMottaSoknader: Boolean,
    val kanOppdatereStatus: Boolean,
    val harMidlertidigDeaktivertMottak: Boolean?,
    val harMidlertidigDeaktivertOppdateringer: Boolean?,
    val harNksTilgang: Boolean?,
    val behandlingsansvarlig: String?,
)

class KommuneInfoClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val integrasjonId: String,
    private val integrasjonPassord: String,
    private val texasClient: TexasClient,
) {
    private val log by logger()

    suspend fun hentKommuneInfo(kommunenummer: String): KommuneInfo? {
        val maskinportenToken = texasClient.getMaskinportenToken()
        val path = FiksPaths.KOMMUNEINFO.replace("{kommunenummer}", kommunenummer)
        return runCatching {
            httpClient
                .get("$baseUrl$path") {
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $maskinportenToken")
                    header("IntegrasjonId", integrasjonId)
                    header("IntegrasjonPassord", integrasjonPassord)
                }.body<KommuneInfo>()
        }.onFailure { log.error("Henting av kommuneinfo for $kommunenummer feilet: ${it.message}") }
            .getOrNull()
    }
}
