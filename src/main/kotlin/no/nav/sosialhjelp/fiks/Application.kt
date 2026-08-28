package no.nav.sosialhjelp.fiks

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.sosialhjelp.fiks.app.ClientProperties
import no.nav.sosialhjelp.fiks.app.auth.CallerRegistry
import no.nav.sosialhjelp.fiks.app.texas.TexasClient
import no.nav.sosialhjelp.fiks.digisosapi.BulkFiksClient
import no.nav.sosialhjelp.fiks.digisosapi.BulkOversiktService
import no.nav.sosialhjelp.fiks.digisosapi.EventServiceFactory
import no.nav.sosialhjelp.fiks.digisosapi.FiksClient
import no.nav.sosialhjelp.fiks.digisosapi.FiksService
import no.nav.sosialhjelp.fiks.kommuneinfo.KommuneInfoClient
import no.nav.sosialhjelp.fiks.navenhet.NorgClientImpl
import no.nav.sosialhjelp.fiks.routes.soknaderRoutes
import no.nav.sosialhjelp.fiks.tilgang.TilgangskontrollService
import no.nav.sosialhjelp.fiks.tilgang.pdl.EntraM2M
import no.nav.sosialhjelp.fiks.tilgang.pdl.PdlClient
import no.nav.sosialhjelp.fiks.tilgang.pdl.TokenXOnBehalfOf
import no.nav.sosialhjelp.fiks.tilgang.skjerming.SkjermedePersonerClient
import no.nav.sosialhjelp.fiks.valkey.ValkeyClient
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URI
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

private val log = LoggerFactory.getLogger("no.nav.sosialhjelp.fiks.Application")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val props = buildClientProperties()
    val httpClient = buildHttpClient()
    val texasClient = TexasClient(props.naisTokenEndpoint)
    val valkeyClient =
        ValkeyClient(
            host = props.valkeyHost,
            port = props.valkeyPort,
            username = props.valkeyUsername,
            password = props.valkeyPassword,
        )
    val callerRegistry = CallerRegistry.fromEnv(props.callerConfig)
    val appMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    // PDL token strategy: try TokenX first (S1 spike result).
    // If tokenX exchange of foreign tokens is rejected by tokendings, swap to EntraM2M.
    val pdlTokenStrategy =
        if (System.getenv("PDL_USE_M2M_TOKEN") == "true") {
            EntraM2M(texasClient, props.pdlScope)
        } else {
            TokenXOnBehalfOf(texasClient, props.pdlScope)
        }

    dependencies {
        provide<NorgClientImpl> { NorgClientImpl(httpClient, props.norgEndpointUrl) }
        provide<FiksClient> {
            FiksClient(
                httpClient = httpClient,
                baseUrl = props.fiksDigisosEndpointUrl,
                integrasjonId = props.fiksIntegrasjonId,
                integrasjonPassord = props.fiksIntegrasjonpassord,
                texasClient = texasClient,
            )
        }
        provide<ValkeyClient> { valkeyClient }
        provide<BulkFiksClient> {
            val jacksonMapper =
                com.fasterxml.jackson.databind
                    .ObjectMapper()
                    .registerKotlinModule()
                    .registerModule(
                        com.fasterxml.jackson.datatype.jsr310
                            .JavaTimeModule(),
                    ).disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            BulkFiksClient(
                httpClient = httpClient,
                baseUrl = props.fiksDigisosEndpointUrl,
                integrasjonId = props.fiksIntegrasjonId,
                integrasjonPassord = props.fiksIntegrasjonpassord,
                texasClient = texasClient,
                objectMapper = jacksonMapper,
            )
        }
        provide<FiksService> { FiksService(resolve(), resolve(), resolve()) }
        provide<EventServiceFactory> { EventServiceFactory(fiksService = resolve(), norgClient = resolve()) }
        provide<BulkOversiktService> {
            val factory: EventServiceFactory = resolve()
            // Create a no-op vedleggService for oversikt (krav via soknadKrav not needed for list view)
            val noopVedlegg =
                object : no.nav.sosialhjelp.fiks.vedlegg.VedleggService {
                    override suspend fun hentSoknadVedleggMedStatus(
                        status: String,
                        digisosSak: no.nav.sosialhjelp.api.fiks.DigisosSak,
                    ) = emptyList<no.nav.sosialhjelp.fiks.vedlegg.InternalVedlegg>()
                }
            val dummyInnsynService =
                object : no.nav.sosialhjelp.fiks.event.InnsynService {
                    override suspend fun hentJsonDigisosSoker(digisosSak: no.nav.sosialhjelp.api.fiks.DigisosSak) = null

                    override suspend fun hentOriginalSoknad(digisosSak: no.nav.sosialhjelp.api.fiks.DigisosSak) = null
                }
            val templateEventService =
                no.nav.sosialhjelp.fiks.event
                    .EventService(dummyInnsynService, noopVedlegg, resolve())
            BulkOversiktService(resolve(), templateEventService, noopVedlegg)
        }
        provide<PdlClient> { PdlClient(httpClient, props.pdlEndpointUrl, pdlTokenStrategy) }
        provide<SkjermedePersonerClient> {
            SkjermedePersonerClient(
                httpClient = httpClient,
                endpointUrl = props.skjermedePersonerEndpointUrl,
                texasClient = texasClient,
                scope = props.skjermedePersonerScope,
            )
        }
        provide<TilgangskontrollService> { TilgangskontrollService(resolve(), resolve()) }
        provide<KommuneInfoClient> {
            KommuneInfoClient(
                httpClient = httpClient,
                baseUrl = props.fiksDigisosEndpointUrl,
                integrasjonId = props.fiksIntegrasjonId,
                integrasjonPassord = props.fiksIntegrasjonpassord,
                texasClient = texasClient,
            )
        }
        provide<CallerRegistry> { callerRegistry }
    }

    this.monitor.subscribe(ApplicationStopped) {
        httpClient.close()
        valkeyClient.close()
    }

    configureMonitoring(appMeterRegistry)
    configureSerialization()
    configureAuth(props)
    configureStatusPages()
    configureRouting(appMeterRegistry, callerRegistry)

    log.info("sosialhjelp-fiks-service started")
}

fun Application.configureMonitoring(meterRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) { registry = meterRegistry }
    install(CallLogging) { level = Level.INFO }
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

fun Application.configureAuth(props: ClientProperties) {
    val idportenJwksUri = System.getenv("IDPORTEN_JWKS_URI") ?: ""
    val idportenIssuer = System.getenv("IDPORTEN_ISSUER") ?: ""
    val azureJwksUri = System.getenv("AZURE_APP_JWKS_URI") ?: ""
    val azureIssuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER") ?: ""
    val azureClientId = System.getenv("AZURE_APP_CLIENT_ID") ?: ""

    authentication {
        // ID-porten: citizen path (raw token forwarded to Fiks /soknader/*)
        // NOTE: fiks-service intentionally accepts a token whose aud is not itself.
        // Mitigations: client_id allowlist (CallerRegistry), acr check, accessPolicy.inbound.
        jwt("idporten") {
            if (idportenJwksUri.isNotEmpty()) {
                verifier(
                    JwkProviderBuilder(URI(idportenJwksUri).toURL())
                        .cached(10, 1, java.util.concurrent.TimeUnit.HOURS)
                        .rateLimited(10, 1, java.util.concurrent.TimeUnit.MINUTES)
                        .build(),
                    idportenIssuer,
                )
                validate { credential ->
                    val acr = credential.payload.getClaim("acr")?.asString()
                    if (acr !in listOf("Level4", "idporten-loa-high")) return@validate null
                    JWTPrincipal(credential.payload)
                }
            } else {
                // Local/test: no JWKS — skip validation
                // Note: in production this branch is unreachable because IDPORTEN_JWKS_URI is set.
                skipWhen { System.getenv("NAIS_CLUSTER_NAME") == null }
                validate { JWTPrincipal(it.payload) }
            }
        }

        // Entra ID: saksbehandler (OBO) path
        jwt("entra") {
            if (azureJwksUri.isNotEmpty()) {
                verifier(
                    JwkProviderBuilder(URI(azureJwksUri).toURL())
                        .cached(10, 1, java.util.concurrent.TimeUnit.HOURS)
                        .rateLimited(10, 1, java.util.concurrent.TimeUnit.MINUTES)
                        .build(),
                    azureIssuer,
                )
                validate { credential ->
                    val aud = credential.payload.audience
                    if (azureClientId.isNotEmpty() && azureClientId !in aud) return@validate null
                    if (credential.payload.getClaim("NAVident")?.asString() == null) return@validate null
                    JWTPrincipal(credential.payload)
                }
            } else {
                skipWhen { System.getenv("NAIS_CLUSTER_NAME") == null }
                validate { JWTPrincipal(it.payload) }
            }
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled exception: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}

private suspend fun pdlTokenxProbe(call: io.ktor.server.application.ApplicationCall) {
    val texas = TexasClient(System.getenv("NAIS_TOKEN_ENDPOINT") ?: "")
    val pdlScope = System.getenv("PDL_SCOPE") ?: "prod-fss:pdl:pdl-api"

    @Suppress("UNCHECKED_CAST")
    val body: Map<String, String> =
        try {
            call.receive()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Body must contain {\"token\":\"...\"}"))
            return
        }
    val token =
        body["token"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing 'token' field"))
            return
        }
    try {
        texas.getTokenOnBehalfOf(target = pdlScope, userToken = token, identityProvider = "tokenx")
        call.respond(mapOf("success" to "true"))
    } catch (e: Exception) {
        call.respond(mapOf("success" to "false", "error" to (e.message ?: "unknown")))
    }
}

fun Application.configureRouting(
    meterRegistry: PrometheusMeterRegistry,
    callerRegistry: CallerRegistry,
) {
    val fiksService: FiksService by dependencies
    val eventServiceFactory: EventServiceFactory by dependencies
    val tilgangskontroll: TilgangskontrollService by dependencies
    val kommuneInfoClient: KommuneInfoClient by dependencies
    val bulkOversiktService: BulkOversiktService by dependencies

    routing {
        get("/internal/health") { call.respondText("OK") }
        get("/internal/metrics") { call.respondText(meterRegistry.scrape()) }

        // S1 spike probe: only enabled outside prod-gcp.
        // POST /internal/pdl-tokenx-probe  body: {"token": "<raw idporten token>"}
        // Returns {"success":true}  or  {"success":false,"error":"..."}
        if (System.getenv("NAIS_CLUSTER_NAME") != "prod-gcp") {
            post("/internal/pdl-tokenx-probe") {
                pdlTokenxProbe(call)
            }
        }

        soknaderRoutes(fiksService, eventServiceFactory, tilgangskontroll, callerRegistry, kommuneInfoClient, bulkOversiktService)
    }
}

private fun buildClientProperties(): ClientProperties =
    ClientProperties(
        fiksDigisosEndpointUrl = requireEnv("FIKS_DIGISOS_ENDPOINT_URL"),
        fiksIntegrasjonId = requireEnv("FIKS_INTEGRASJON_ID"),
        fiksIntegrasjonpassord = requireEnv("FIKS_INTEGRASJON_PASSORD"),
        norgEndpointUrl = System.getenv("NORG_ENDPOINT_URL") ?: "http://norg2/norg2/api/v1",
        naisTokenEndpoint = System.getenv("NAIS_TOKEN_ENDPOINT") ?: "",
        valkeyHost = System.getenv("VALKEY_HOST") ?: "localhost",
        valkeyPort = System.getenv("VALKEY_PORT")?.toIntOrNull() ?: 6379,
        valkeyUsername = System.getenv("VALKEY_USERNAME") ?: "",
        valkeyPassword = System.getenv("VALKEY_PASSWORD") ?: "",
        pdlEndpointUrl = System.getenv("PDL_ENDPOINT_URL") ?: "https://pdl-api.prod-fss-pub.nais.io/graphql",
        pdlScope = System.getenv("PDL_SCOPE") ?: "prod-fss:pdl:pdl-api",
        skjermedePersonerEndpointUrl = System.getenv("SKJERMEDE_PERSONER_ENDPOINT_URL") ?: "http://skjermede-personer-pip.nom",
        skjermedePersonerScope = System.getenv("SKJERMEDE_PERSONER_SCOPE") ?: "",
        callerConfig = System.getenv("CALLER_CONFIG") ?: "",
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
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 30_000
        }
        install(HttpRequestRetry) {
            maxRetries = 5
            retryIf { _, response -> response.status.value in 500..599 }
            exponentialDelay(2.0, 100L, 10_000L)
        }
    }
