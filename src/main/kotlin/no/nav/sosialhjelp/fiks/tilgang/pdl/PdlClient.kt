package no.nav.sosialhjelp.fiks.tilgang.pdl

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
import no.nav.sosialhjelp.fiks.utils.logger
import no.nav.sosialhjelp.fiks.utils.maskerFnr

private const val QUERY_HENT_PERSON = """
    query HentPerson(${'$'}ident: ID!) {
        hentPerson(ident: ${'$'}ident) {
            adressebeskyttelse(historikk: false) {
                gradering
            }
        }
        hentIdenter(ident: ${'$'}ident, historikk: true) {
            identer {
                ident
                historisk
            }
        }
    }
"""

/**
 * PDL client for adressebeskyttelse (kode 6/7) and hentIdenter (eier-verifisering).
 *
 * Token strategy (see docs/domenetjeneste-plan.md §4 S1 spike):
 *  - Saksbehandler path: Texas BEHALF_OF with entra_id (OBO on the saksbehandler token)
 *  - Citizen path: Texas BEHALF_OF with tokenx using the user's ID-porten token.
 *    If tokenx exchange of a foreign token is rejected by tokendings, fall back to
 *    PdlTokenStrategy.EntraM2M and get personvern sign-off.
 *
 * behandlingsnummer is supplied per caller (validated by CallerRegistry).
 */
class PdlClient(
    private val httpClient: HttpClient,
    private val pdlEndpointUrl: String,
    private val tokenStrategy: PdlTokenStrategy,
) {
    private val log by logger()

    suspend fun hentPerson(
        fnr: Fnr,
        caller: Caller,
    ): PdlPerson? {
        val behandlingsnummer =
            when (caller) {
                is Caller.Citizen -> "B478" // innsyn behandling — default for citizen path
                is Caller.Saksbehandler -> caller.behandlingsnummer
            }
        val token = tokenStrategy.hentToken(caller)
        return executeQuery(fnr.value, token, behandlingsnummer)?.data?.hentPerson
    }

    suspend fun hentIdenter(
        fnr: Fnr,
        caller: Caller,
    ): List<String> {
        val behandlingsnummer =
            when (caller) {
                is Caller.Citizen -> "B478"
                is Caller.Saksbehandler -> caller.behandlingsnummer
            }
        val token = tokenStrategy.hentToken(caller)
        return executeQuery(fnr.value, token, behandlingsnummer)
            ?.data
            ?.hentIdenter
            ?.identer
            ?.map { it.ident }
            ?: emptyList()
    }

    private suspend fun executeQuery(
        ident: String,
        token: String,
        behandlingsnummer: String,
    ): PdlResponse? =
        runCatching {
            httpClient
                .post(pdlEndpointUrl) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("behandlingsnummer", behandlingsnummer)
                    header(
                        "Nav-Call-Id",
                        io.opentelemetry.api.trace.Span
                            .current()
                            .spanContext.traceId,
                    )
                    setBody(mapOf("query" to QUERY_HENT_PERSON, "variables" to mapOf("ident" to ident)))
                }.body<PdlResponse>()
        }.onFailure { log.error("PDL-kall feilet: ${it.message?.maskerFnr}") }
            .getOrNull()
}
