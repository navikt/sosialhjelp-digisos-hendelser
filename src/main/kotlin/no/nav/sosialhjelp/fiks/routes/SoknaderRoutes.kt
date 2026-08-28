package no.nav.sosialhjelp.fiks.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.sosialhjelp.api.fiks.exceptions.FiksClientException
import no.nav.sosialhjelp.api.fiks.exceptions.FiksNotFoundException
import no.nav.sosialhjelp.api.fiks.exceptions.FiksServerException
import no.nav.sosialhjelp.fiks.app.auth.Caller
import no.nav.sosialhjelp.fiks.app.auth.CallerRegistry
import no.nav.sosialhjelp.fiks.app.auth.Fnr
import no.nav.sosialhjelp.fiks.digisosapi.BulkOversiktService
import no.nav.sosialhjelp.fiks.digisosapi.EventServiceFactory
import no.nav.sosialhjelp.fiks.digisosapi.FiksService
import no.nav.sosialhjelp.fiks.kommuneinfo.KommuneInfoClient
import no.nav.sosialhjelp.fiks.logging.AuditLogger
import no.nav.sosialhjelp.fiks.tilgang.IkkeTilgangException
import no.nav.sosialhjelp.fiks.tilgang.TilgangskontrollService
import no.nav.sosialhjelp.fiks.vedlegg.FiksVedleggService
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.sosialhjelp.fiks.routes")

data class FnrBody(
    val fnr: String,
)

fun Route.soknaderRoutes(
    fiksService: FiksService,
    eventServiceFactory: EventServiceFactory,
    tilgangskontroll: TilgangskontrollService,
    callerRegistry: CallerRegistry,
    kommuneInfoClient: KommuneInfoClient,
    bulkOversiktService: BulkOversiktService,
) {
    // --- Citizen path (ID-porten) ---
    authenticate("idporten") {
        // GET /api/v1/soknader — all søknader for the authenticated citizen
        get("/api/v1/soknader") {
            val caller = call.resolveCitizenCaller(callerRegistry) ?: return@get
            val fnr = caller.pid
            runCatching { tilgangskontroll.sjekkTilgang(fnr, caller) }
                .onFailure { e ->
                    if (e is IkkeTilgangException) return@get call.respond(HttpStatusCode.NotFound)
                    call.handleFiksError(e)
                    return@get
                }
            runCatching { fiksService.getAllSoknaderMedInnsynsfiler(caller) }
                .onSuccess { sakerMedInnsynsfiler ->
                    val oversikt = bulkOversiktService.createOversikt(sakerMedInnsynsfiler, caller.rawIdportenToken)
                    call.respond(oversikt)
                }.onFailure { call.handleFiksError(it) }
        }

        // GET /api/v1/soknader/{digisosId} — single søknad for the authenticated citizen
        get("/api/v1/soknader/{digisosId}") {
            val digisosId = call.parameters["digisosId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val caller = call.resolveCitizenCaller(callerRegistry) ?: return@get
            val fnr = caller.pid

            runCatching { tilgangskontroll.sjekkTilgang(fnr, caller) }
                .onFailure { e ->
                    if (e is IkkeTilgangException) return@get call.respond(HttpStatusCode.NotFound)
                    call.handleFiksError(e)
                    return@get
                }

            runCatching {
                val digisosSak = fiksService.getSoknad(digisosId, caller)
                // Eier-verifisering: confirm søknad belongs to this citizen
                tilgangskontroll.sjekkEierskap(fnr, digisosSak.sokerFnr, caller)
                val vedleggService = FiksVedleggService(fiksService, caller)
                eventServiceFactory.createForToken(caller.rawIdportenToken, vedleggService).createModel(digisosSak)
            }.onSuccess { call.respond(it) }
                .onFailure { e ->
                    if (e is IkkeTilgangException) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.handleFiksError(e)
                    }
                }
        }
    }

    // --- Saksbehandler path (Entra OBO) ---
    authenticate("entra") {
        // POST /api/v1/soknader/sok — all søknader for a given fnr (with folded models)
        post("/api/v1/soknader/sok") {
            val caller = call.resolveSaksbehandlerCaller(callerRegistry) ?: return@post
            val body =
                runCatching { call.receive<FnrBody>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Body must contain {\"fnr\":\"...\"}"))
                    return@post
                }
            val fnr =
                runCatching { Fnr(body.fnr) }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Ugyldig fnr"))
                    return@post
                }
            val sporingsId = currentSporingsId()

            // Audit BEFORE any upstream call
            AuditLogger.logPermit(caller, fnr, sporingsId, call.request.uri, "POST")

            runCatching { tilgangskontroll.sjekkTilgang(fnr, caller) }
                .onFailure { e ->
                    if (e is IkkeTilgangException) {
                        AuditLogger.logDeny(caller, fnr, sporingsId, call.request.uri, "POST")
                        return@post call.respond(HttpStatusCode.NotFound)
                    }
                    call.handleFiksError(e)
                    return@post
                }

            runCatching { fiksService.getAllSoknaderForFnrMedInnsynsfiler(fnr.value, caller) }
                .onSuccess { sakerMedInnsynsfiler ->
                    val filtered = sakerMedInnsynsfiler.filter { (sak, _) -> sak.sokerFnr == fnr.value }
                    val oversikt = bulkOversiktService.createOversikt(filtered, caller.oboToken)
                    call.respond(oversikt)
                }.onFailure { call.handleFiksError(it) }
        }

        // POST /api/v1/soknader/{digisosId} — single søknad for saksbehandler
        post("/api/v1/soknader/{digisosId}") {
            val digisosId = call.parameters["digisosId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val caller = call.resolveSaksbehandlerCaller(callerRegistry) ?: return@post
            val body =
                runCatching { call.receive<FnrBody>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Body must contain {\"fnr\":\"...\"}"))
                    return@post
                }
            val fnr =
                runCatching { Fnr(body.fnr) }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Ugyldig fnr"))
                    return@post
                }
            val sporingsId = currentSporingsId()

            // Audit BEFORE any upstream call
            AuditLogger.logPermit(caller, fnr, sporingsId, call.request.uri, "POST")

            runCatching { tilgangskontroll.sjekkTilgang(fnr, caller) }
                .onFailure { e ->
                    if (e is IkkeTilgangException) {
                        AuditLogger.logDeny(caller, fnr, sporingsId, call.request.uri, "POST")
                        return@post call.respond(HttpStatusCode.NotFound)
                    }
                    call.handleFiksError(e)
                    return@post
                }

            runCatching {
                val digisosSak = fiksService.getSoknad(digisosId, caller)
                // Verify the caller-supplied fnr matches the sak
                if (digisosSak.sokerFnr != fnr.value) {
                    log.warn("fnr mismatch: caller-supplied fnr does not match sokerFnr for digisosId=$digisosId")
                    return@runCatching null
                }
                val vedleggService = FiksVedleggService(fiksService, caller)
                eventServiceFactory
                    .createForToken(
                        // For OBO path, we reuse the caller's oboToken to drive document fetching
                        // (the token for /nav/* paths is Maskinporten, handled in FiksClient)
                        token = caller.oboToken,
                        vedleggService = vedleggService,
                    ).createModel(digisosSak)
            }.onSuccess { response ->
                if (response == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(response)
                }
            }.onFailure { call.handleFiksError(it) }
        }
    }

    // --- Kommuneinfo (Maskinporten, no user token needed) ---
    authenticate("entra") {
        get("/api/v1/kommuner/{kommunenummer}") {
            val kommunenummer = call.parameters["kommunenummer"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            runCatching { kommuneInfoClient.hentKommuneInfo(kommunenummer) }
                .onSuccess { info ->
                    if (info == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(info)
                    }
                }.onFailure { call.handleFiksError(it) }
        }
    }
}

private fun currentSporingsId(): String {
    val traceId =
        io.opentelemetry.api.trace.Span
            .current()
            .spanContext.traceId
    return if (traceId.isNotBlank() && traceId != "00000000000000000000000000000000") {
        traceId
    } else {
        java.util.UUID
            .randomUUID()
            .toString()
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.resolveCitizenCaller(callerRegistry: CallerRegistry): Caller.Citizen? {
    val principal =
        principal<JWTPrincipal>() ?: run {
            respond(HttpStatusCode.Unauthorized)
            return null
        }
    val clientId =
        principal.payload.getClaim("client_id")?.asString()
            ?: principal.payload.getClaim("azp")?.asString()
            ?: run {
                respond(HttpStatusCode.Unauthorized)
                return null
            }

    if (!callerRegistry.isAllowed(clientId)) {
        log.warn("Ukjent client_id=$clientId — avvist")
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    val pid =
        principal.payload.getClaim("pid")?.asString() ?: run {
            respond(HttpStatusCode.Unauthorized)
            return null
        }
    val rawToken =
        request.headers["Authorization"]?.removePrefix("Bearer ")?.trim() ?: run {
            respond(HttpStatusCode.Unauthorized)
            return null
        }
    return Caller.Citizen(
        pid = Fnr(pid),
        rawIdportenToken = rawToken,
        appNavn = callerRegistry.lookup(clientId)?.appNavn ?: clientId,
    )
}

private suspend fun io.ktor.server.application.ApplicationCall.resolveSaksbehandlerCaller(
    callerRegistry: CallerRegistry,
): Caller.Saksbehandler? {
    val principal =
        principal<JWTPrincipal>() ?: run {
            respond(HttpStatusCode.Unauthorized)
            return null
        }
    val clientId =
        principal.payload.getClaim("azp")?.asString()
            ?: principal.payload.getClaim("appid")?.asString()
            ?: run {
                respond(HttpStatusCode.Unauthorized)
                return null
            }

    val callerInfo =
        callerRegistry.lookup(clientId) ?: run {
            log.warn("Ukjent client_id=$clientId — avvist")
            respond(HttpStatusCode.Unauthorized)
            return null
        }
    val navIdent =
        principal.payload.getClaim("NAVident")?.asString() ?: run {
            respond(HttpStatusCode.Unauthorized)
            return null
        }
    val behandlingsnummer =
        request.headers["behandlingsnummer"] ?: run {
            respond(HttpStatusCode.BadRequest, mapOf("message" to "Header 'behandlingsnummer' er påkrevd"))
            return null
        }
    if (behandlingsnummer !in callerInfo.tillatteBehandlingsnummer) {
        log.warn("Ikke-tillatt behandlingsnummer='$behandlingsnummer' for clientId=$clientId")
        respond(HttpStatusCode.Forbidden, mapOf("message" to "Ikke tillatt behandlingsnummer"))
        return null
    }
    val oboToken =
        request.headers["Authorization"]?.removePrefix("Bearer ")?.trim() ?: run {
            respond(HttpStatusCode.Unauthorized)
            return null
        }
    return Caller.Saksbehandler(
        navIdent = navIdent,
        oboToken = oboToken,
        appNavn = callerInfo.appNavn,
        behandlingsnummer = behandlingsnummer,
    )
}

private suspend fun io.ktor.server.application.ApplicationCall.handleFiksError(cause: Throwable) {
    when (cause) {
        is IkkeTilgangException -> respond(HttpStatusCode.NotFound)
        is FiksNotFoundException -> {
            log.warn("Fiks 404: ${cause.message}")
            respond(HttpStatusCode.NotFound)
        }
        is FiksClientException -> {
            log.warn("Fiks 4xx (${cause.status}): ${cause.message}")
            respond(HttpStatusCode.BadGateway)
        }
        is FiksServerException -> {
            log.error("Fiks 5xx (${cause.status}): ${cause.message}")
            respond(HttpStatusCode.BadGateway)
        }
        else -> {
            log.error("Uventet feil: ${cause.message}", cause)
            respond(HttpStatusCode.InternalServerError)
        }
    }
}
