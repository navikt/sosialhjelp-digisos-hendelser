package no.nav.sosialhjelp.fiks.navenhet

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import no.nav.sosialhjelp.fiks.app.exceptions.NorgException
import no.nav.sosialhjelp.fiks.utils.logger

class NorgClientImpl(
    private val httpClient: HttpClient,
    private val norgEndpointUrl: String,
) : NorgClient {
    private val log by logger()

    override suspend fun hentNavEnhet(enhetsnr: String): NavEnhet {
        log.debug("Forsøker å hente Nav-enhet $enhetsnr fra NORG2")
        val response =
            httpClient.get("$norgEndpointUrl/enhet/$enhetsnr") {
                accept(ContentType.Application.Json)
            }

        return when {
            response.status == HttpStatusCode.NotFound ->
                throw NorgException("Norg - enhetsnr $enhetsnr ikke funnet", null)
            !response.status.isSuccess() ->
                throw NorgException("Norg - hentNavEnhet feilet med status ${response.status}", null)
            else -> response.body<NavEnhet>().also { log.info("Hentet Nav-enhet $enhetsnr fra NORG2") }
        }
    }
}
