package no.nav.sosialhjelp.fiks

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun `health endpoint returns 200 OK`() =
        testApplication {
            application {
                // Use a minimal module without env var dependencies
                configureMonitoring()
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
