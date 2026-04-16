package no.nav.sosialhjelp.fiks.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.sosialhjelp.api.fiks.exceptions.FiksClientException
import no.nav.sosialhjelp.api.fiks.exceptions.FiksNotFoundException
import no.nav.sosialhjelp.api.fiks.exceptions.FiksServerException
import no.nav.sosialhjelp.fiks.digisosapi.EventServiceFactory
import no.nav.sosialhjelp.fiks.digisosapi.FiksService
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.sosialhjelp.fiks.routes.SoknaderRoutes")

fun Route.soknaderRoutes(
    fiksService: FiksService,
    eventServiceFactory: EventServiceFactory,
) {
    authenticate("jwt") {
        get("/api/v1/soknader") {
            val token = call.extractToken() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            runCatching { fiksService.getAllSoknader(token) }
                .onSuccess { call.respond(it) }
                .onFailure { call.handleFiksError(it) }
        }

        get("/api/v1/soknader/{digisosId}") {
            val digisosId = call.parameters["digisosId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val token = call.extractToken() ?: return@get call.respond(HttpStatusCode.Unauthorized)

            runCatching {
                val digisosSak = fiksService.getSoknad(digisosId, token)
                eventServiceFactory.createForToken(token).createModel(digisosSak)
            }.onSuccess { call.respond(it) }
                .onFailure { call.handleFiksError(it) }
        }
    }
}

private fun ApplicationCall.extractToken(): String? =
    request
        .header(HttpHeaders.Authorization)
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private suspend fun ApplicationCall.handleFiksError(cause: Throwable) {
    when (cause) {
        is FiksNotFoundException -> {
            log.warn("Fiks 404: ${cause.message}")
            respond(HttpStatusCode.NotFound, mapOf("message" to cause.message))
        }
        is FiksClientException -> {
            log.warn("Fiks 4xx (${cause.status}): ${cause.message}")
            respond(HttpStatusCode.BadGateway, mapOf("message" to cause.message))
        }
        is FiksServerException -> {
            log.error("Fiks 5xx (${cause.status}): ${cause.message}", cause)
            respond(HttpStatusCode.BadGateway, mapOf("message" to cause.message))
        }
        else -> {
            log.error("Uventet feil: ${cause.message}", cause)
            respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal Server Error"))
        }
    }
}
