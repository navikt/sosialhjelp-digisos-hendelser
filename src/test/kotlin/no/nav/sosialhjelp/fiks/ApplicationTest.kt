package no.nav.sosialhjelp.fiks

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    private val testMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    @Test
    fun `health endpoint returns 200 OK`() =
        testApplication {
            application {
                configureMonitoring(testMeterRegistry)
                configureSerialization()
                configureStatusPages()
                routing {
                    get("/internal/health") { call.respondText("OK") }
                }
            }
            val response = client.get("/internal/health")
            assertEquals(HttpStatusCode.OK, response.status)
        }
}
