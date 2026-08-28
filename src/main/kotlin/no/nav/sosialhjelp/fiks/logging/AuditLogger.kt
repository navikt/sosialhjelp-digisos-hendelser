package no.nav.sosialhjelp.fiks.logging

import no.nav.sosialhjelp.fiks.app.auth.Caller
import no.nav.sosialhjelp.fiks.app.auth.Fnr
import org.slf4j.LoggerFactory

/**
 * CEF-formatted audit logging to the `sporingslogg` logger.
 *
 * Entries are only emitted on the Entra OBO (saksbehandler) path:
 *  - PERMIT: logged on EVERY inbound OBO request, BEFORE any upstream call (D8).
 *    This fixes modia-api's existing gap where a cache hit produces no audit entry.
 *  - DENY: logged on every access denial (kode 6/7 or skjerming).
 *
 * The citizen path does NOT generate sporingslogg entries (D9) — a person reading their own
 * data is not a sporingslogg event. It is counted as a metric instead.
 *
 * CEF format: CEF:0|<vendor>|<product>|<version>|<signatureId>|<name>|<severity>|<extension>
 */
object AuditLogger {
    private val log = LoggerFactory.getLogger("sporingslogg")

    private const val CEF_VERSION = "CEF:0"
    private const val VENDOR = "sosialhjelp-fiks-service"
    private const val PRODUCT = "sporingslogg"
    private const val SCHEMA_VERSION = "1.0"
    private const val SIGNATURE_ID = "audit:access"

    fun logPermit(
        caller: Caller.Saksbehandler,
        brukerFnr: Fnr,
        sporingsId: String,
        url: String,
        httpMethod: String,
    ) {
        log.info(
            buildCef(
                title = caller.appNavn,
                severity = "INFO",
                navIdent = caller.navIdent,
                brukerFnr = brukerFnr.value,
                traceId = sporingsId,
                callerApp = caller.appNavn,
                url = url,
                httpMethod = httpMethod,
                sporingsId = sporingsId,
            ),
        )
    }

    fun logDeny(
        caller: Caller.Saksbehandler,
        brukerFnr: Fnr,
        sporingsId: String,
        url: String,
        httpMethod: String,
    ) {
        log.warn(
            buildCef(
                title = caller.appNavn,
                severity = "WARN",
                navIdent = caller.navIdent,
                brukerFnr = brukerFnr.value,
                traceId = sporingsId,
                callerApp = caller.appNavn,
                url = url,
                httpMethod = httpMethod,
                sporingsId = sporingsId,
            ),
        )
    }

    private fun buildCef(
        title: String,
        severity: String,
        navIdent: String,
        brukerFnr: String,
        traceId: String,
        callerApp: String,
        url: String,
        httpMethod: String,
        sporingsId: String,
    ): String {
        val extension =
            "end=${System.currentTimeMillis()} " +
                "suid=$navIdent " +
                "duid=$brukerFnr " +
                "sproc=$traceId " +
                "dproc=$callerApp " +
                "request=$url " +
                "requestMethod=$httpMethod " +
                "cs5=$sporingsId " +
                "cs5Label=sporingsId"
        return "$CEF_VERSION|$VENDOR|$PRODUCT|$SCHEMA_VERSION|$SIGNATURE_ID|$title|$severity|$extension"
    }
}
