package no.nav.sosialhjelp.fiks.app

data class ClientProperties(
    val fiksDigisosEndpointUrl: String,
    val fiksDokumentlagerEndpointUrl: String,
    val fiksSvarUtEndpointUrl: String,
    val fiksIntegrasjonId: String,
    val fiksIntegrasjonpassord: String,
    val norgEndpointUrl: String,
    val naisTokenEndpoint: String,
    val valkeyHost: String,
    val valkeyPort: Int,
)
