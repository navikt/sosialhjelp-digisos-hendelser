package no.nav.sosialhjelp.fiks

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.digisosapi.EventServiceFactory
import no.nav.sosialhjelp.fiks.digisosapi.FiksClient
import no.nav.sosialhjelp.fiks.digisosapi.FiksService
import no.nav.sosialhjelp.fiks.navenhet.NorgClientImpl
import no.nav.sosialhjelp.fiks.routes.soknaderRoutes
import no.nav.sosialhjelp.fiks.vedlegg.VedleggService
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.UUID
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

private val log = LoggerFactory.getLogger("no.nav.sosialhjelp.fiks.Application")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val clientProperties = buildClientProperties()
    val httpClient = buildHttpClient()

    val norgClient = NorgClientImpl(httpClient, clientProperties.norgEndpointUrl)
    val fiksClient =
        FiksClient(
            httpClient = httpClient,
            baseUrl = clientProperties.fiksDigisosEndpointUrl,
            integrasjonId = clientProperties.fiksIntegrasjonId,
            integrasjonPassord = clientProperties.fiksIntegrasjonpassord,
        )
    val fiksService = FiksService(fiksClient)

    // VedleggService has no Fiks-communication and has no impl here yet
    val vedleggService = NoopVedleggService()

    val eventServiceFactory =
        EventServiceFactory(
            clientProperties = clientProperties,
            fiksService = fiksService,
            vedleggService = vedleggService,
            norgClient = norgClient,
        )

    configureMonitoring()
    configureSerialization()
    configureAuth()
    configureStatusPages()
    configureRouting(fiksService, eventServiceFactory)

    log.info("sosialhjelp-fiks-service started")
}

fun Application.configureMonitoring() {
    install(CallId) {
        generate { UUID.randomUUID().toString() }
        verify { it.isNotEmpty() }
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("callId")
    }
}

fun Application.configureSerialization() {
    install(ServerContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }
}

fun Application.configureAuth() {
    val jwksUri = System.getenv("TOKEN_X_JWKS_URI") ?: System.getenv("AZURE_APP_JWKS_URI") ?: ""
    val clientId = System.getenv("TOKEN_X_CLIENT_ID") ?: System.getenv("AZURE_APP_CLIENT_ID") ?: ""

    authentication {
        jwt("jwt") {
            if (jwksUri.isNotEmpty()) {
                verifier(
                    com.auth0.jwk
                        .JwkProviderBuilder(java.net.URI(jwksUri).toURL())
                        .build(),
                )
                validate { credential ->
                    if (clientId.isEmpty() || clientId in credential.payload.audience) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
            } else {
                // Local/test: no JWKS configured — skip validation
                skipWhen { true }
                validate { credential -> JWTPrincipal(credential.payload) }
            }
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled exception: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal Server Error"))
        }
    }
}

fun Application.configureRouting(
    fiksService: FiksService,
    eventServiceFactory: EventServiceFactory,
) {
    routing {
        get("/internal/health") {
            call.respondText("OK")
        }
        soknaderRoutes(fiksService, eventServiceFactory)
    }
}

private fun buildClientProperties(): ClientProperties =
    ClientProperties(
        fiksDigisosEndpointUrl = requireEnv("FIKS_DIGISOS_ENDPOINT_URL"),
        fiksDokumentlagerEndpointUrl = requireEnv("FIKS_DOKUMENTLAGER_ENDPOINT_URL"),
        fiksSvarUtEndpointUrl = System.getenv("FIKS_SVARUT_ENDPOINT_URL") ?: "",
        fiksIntegrasjonId = requireEnv("FIKS_INTEGRASJON_ID"),
        fiksIntegrasjonpassord = requireEnv("FIKS_INTEGRASJON_PASSORD"),
        norgEndpointUrl = System.getenv("NORG_ENDPOINT_URL") ?: "http://norg2/norg2/api/v1",
        naisTokenEndpoint = System.getenv("NAIS_TOKEN_ENDPOINT") ?: "",
        valkeyHost = System.getenv("VALKEY_HOST") ?: "localhost",
        valkeyPort = System.getenv("VALKEY_PORT")?.toIntOrNull() ?: 6379,
    )

private fun requireEnv(name: String): String = System.getenv(name) ?: error("Required environment variable $name is not set")

private fun buildHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }

/** Placeholder implementation — VedleggService lives in innsyn-api and depends on Fiks upload logic. */
private class NoopVedleggService : VedleggService {
    override suspend fun hentSoknadVedleggMedStatus(
        status: String,
        digisosSak: no.nav.sosialhjelp.api.fiks.DigisosSak,
    ) = emptyList<no.nav.sosialhjelp.fiks.vedlegg.InternalVedlegg>()
}
