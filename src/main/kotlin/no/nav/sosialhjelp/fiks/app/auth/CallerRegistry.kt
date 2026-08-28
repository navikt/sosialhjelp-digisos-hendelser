package no.nav.sosialhjelp.fiks.app.auth

/**
 * Registry of allowed callers and their permitted behandlingsnummer.
 *
 * Keyed on the `client_id` claim from the inbound JWT.
 * For ID-porten tokens: the `client_id` of the consuming app (e.g. innsyn-api's client ID).
 * For Entra tokens: the `azp` / `appid` claim.
 *
 * @param entries map from client_id to [CallerInfo]
 */
class CallerRegistry(
    private val entries: Map<String, CallerInfo>,
) {
    fun lookup(clientId: String): CallerInfo? = entries[clientId]

    fun isAllowed(clientId: String): Boolean = entries.containsKey(clientId)

    fun allowedBehandlingsnummer(clientId: String): Set<String> = entries[clientId]?.tillatteBehandlingsnummer ?: emptySet()

    companion object {
        /**
         * Build from environment config.
         * Format: comma-separated entries of "clientId:appNavn:behandlingsnummer1+behandlingsnummer2"
         * Example: "abc123:sosialhjelp-innsyn-api:B478,xyz789:sosialhjelp-modia-api:B117"
         *
         * Falls back to a permissive local-dev registry when [callerConfig] is blank.
         */
        fun fromEnv(callerConfig: String): CallerRegistry {
            if (callerConfig.isBlank()) return localDevRegistry()
            val entries =
                callerConfig.split(",").associate { entry ->
                    val parts = entry.trim().split(":")
                    require(parts.size >= 3) { "Invalid caller config entry: $entry" }
                    val clientId = parts[0].trim()
                    val appNavn = parts[1].trim()
                    val behandlingsnummer = parts[2].trim().split("+").toSet()
                    clientId to CallerInfo(appNavn, behandlingsnummer)
                }
            return CallerRegistry(entries)
        }

        private fun localDevRegistry(): CallerRegistry =
            CallerRegistry(
                mapOf(
                    "local-innsyn" to CallerInfo("sosialhjelp-innsyn-api", setOf("B478")),
                    "local-modia" to CallerInfo("sosialhjelp-modia-api", setOf("B117")),
                ),
            )
    }
}

data class CallerInfo(
    val appNavn: String,
    val tillatteBehandlingsnummer: Set<String>,
)
