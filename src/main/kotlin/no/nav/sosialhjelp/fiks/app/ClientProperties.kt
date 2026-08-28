package no.nav.sosialhjelp.fiks.app

data class ClientProperties(
    val fiksDigisosEndpointUrl: String,
    val fiksIntegrasjonId: String,
    val fiksIntegrasjonpassord: String,
    val norgEndpointUrl: String,
    val naisTokenEndpoint: String,
    val valkeyHost: String,
    val valkeyPort: Int,
    val valkeyUsername: String,
    val valkeyPassword: String,
    /** PDL GraphQL endpoint. */
    val pdlEndpointUrl: String,
    /** PDL scope for Texas OBO/M2M. */
    val pdlScope: String,
    /** Skjermede-personer-pip endpoint URL. */
    val skjermedePersonerEndpointUrl: String,
    /** Skjermede-personer-pip scope for Texas OBO. */
    val skjermedePersonerScope: String,
    /**
     * Comma-separated caller allowlist entries.
     * Format: "clientId:appNavn:behandlingsnummer1+behandlingsnummer2,..."
     */
    val callerConfig: String,
)
