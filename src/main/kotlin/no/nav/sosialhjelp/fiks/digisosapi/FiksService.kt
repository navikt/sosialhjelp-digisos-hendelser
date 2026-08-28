package no.nav.sosialhjelp.fiks.digisosapi

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.sbl.soknadsosialhjelp.digisos.soker.JsonDigisosSoker
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.app.auth.Caller
import no.nav.sosialhjelp.fiks.utils.logger
import no.nav.sosialhjelp.fiks.utils.maskerFnr
import no.nav.sosialhjelp.fiks.valkey.ValkeyClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val DIGISOSSAK_TTL: Duration = Duration.ofSeconds(60)
private val DOCUMENT_TTL: Duration = Duration.ofHours(1)

/**
 * Coordinates calls to Fiks:
 * - Checks Valkey cache before HTTP calls
 * - Deduplicates concurrent calls with the same key using per-key mutexes
 *
 * Cache keys are global (not per-user) because the access gate runs on every request.
 */
class FiksService(
    private val fiksClient: FiksClient,
    private val valkeyClient: ValkeyClient,
    private val bulkFiksClient: BulkFiksClient? = null,
) {
    private val log by logger()

    /**
     * Map from cache key to Mutex. Keys are removed when the lock is released.
     * Note: there is a small window where two coroutines can both create a Mutex for the same key
     * (computeIfAbsent is not atomic with the remove), but this is benign — at worst one extra
     * upstream call is made. The important invariant is that responses are never served without
     * the access gate having run.
     */
    private val requestLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Fetch all søknader with their innsynsfiler for the authenticated citizen.
     * For the saksbehandler path, use [getAllSoknaderForFnrMedInnsynsfiler] instead.
     */
    suspend fun getAllSoknaderMedInnsynsfiler(caller: Caller.Citizen): List<Pair<DigisosSak, JsonDigisosSoker?>> {
        val saker = getAllSoknader(caller)
        return hentInnsynsfilerBulk(saker, caller)
    }

    suspend fun getAllSoknaderForFnrMedInnsynsfiler(
        fnr: String,
        caller: Caller.Saksbehandler,
    ): List<Pair<DigisosSak, JsonDigisosSoker?>> {
        val saker = getAllSoknaderForFnr(fnr, caller)
        return hentInnsynsfilerBulk(saker, caller)
    }

    /**
     * Fetches all DigisosSak for a user, plus all their JsonDigisosSoker in one bulk call.
     *
     * Returns a list of (DigisosSak, JsonDigisosSoker?) pairs.
     * JsonDigisosSoker may be null if the sak has no innsynsfil or if the bulk fetch failed for that sak.
     *
     * Used by the oversikt endpoints — avoids N sequential document fetches.
     */
    private suspend fun hentInnsynsfilerBulk(
        saker: List<DigisosSak>,
        caller: Caller,
    ): List<Pair<DigisosSak, JsonDigisosSoker?>> {
        if (saker.isEmpty()) return emptyList()

        // Warm the DigisosSak cache
        saker.forEach { sak ->
            valkeyClient.set("fiks:digisossak:${sak.fiksDigisosId}", sak, DIGISOSSAK_TTL)
        }

        val bulkResult =
            if (bulkFiksClient != null) {
                runCatching { bulkFiksClient!!.hentDokumenterBulk(saker, caller) }
                    .onFailure { log.warn("Bulk-henting feilet, faller tilbake til individuell fetching: ${it.message?.maskerFnr}") }
                    .getOrElse { emptyMap() }
            } else {
                emptyMap()
            }

        // Cache the fetched innsynsfiler
        saker.forEach { sak ->
            val metadataId = sak.digisosSoker?.metadata
            val sistOppdatert = sak.digisosSoker?.timestampSistOppdatert
            if (metadataId != null && sistOppdatert != null) {
                val cacheKey = "fiks:document:${metadataId}_$sistOppdatert"
                val innsynsfil = bulkResult[sak.fiksDigisosId]
                if (innsynsfil != null) {
                    valkeyClient.set(cacheKey, innsynsfil, DOCUMENT_TTL)
                }
            }
        }

        return saker.map { sak ->
            val metadataId = sak.digisosSoker?.metadata
            val sistOppdatert = sak.digisosSoker?.timestampSistOppdatert

            val innsynsfil =
                bulkResult[sak.fiksDigisosId]
                    ?: if (metadataId != null && sistOppdatert != null) {
                        // Try individual cache lookup for saker that weren't in the bulk result
                        valkeyClient.get("fiks:document:${metadataId}_$sistOppdatert", JsonDigisosSoker::class.java)
                    } else {
                        null
                    }

            sak to innsynsfil
        }
    }

    /**
     * Fetch all søknader for the authenticated citizen.
     * For the saksbehandler path, use [getAllSoknaderForFnr] instead.
     */
    suspend fun getAllSoknader(caller: Caller.Citizen): List<DigisosSak> =
        fiksClient
            .hentAlleDigisosSaker(caller)
            .also { log.info("Hentet ${it.size} DigisosSaker (citizen)") }

    suspend fun getAllSoknaderForFnr(
        fnr: String,
        caller: Caller.Saksbehandler,
    ): List<DigisosSak> =
        fiksClient
            .hentAlleDigisosSakerForFnr(fnr, caller)
            .also { log.info("Hentet ${it.size} DigisosSaker (saksbehandler)") }

    suspend fun getSoknad(
        digisosId: String,
        caller: Caller,
    ): DigisosSak {
        val cacheKey = "fiks:digisossak:$digisosId"
        valkeyClient.get(cacheKey, DigisosSak::class.java)?.let { return it }

        return withDedup(cacheKey) {
            valkeyClient.get(cacheKey, DigisosSak::class.java)
                ?: fiksClient.hentDigisosSak(digisosId, caller).also {
                    valkeyClient.set(cacheKey, it, DIGISOSSAK_TTL)
                }
        }
    }

    /** Called from FiksInnsynService which carries a raw token (not a full Caller). */
    suspend fun <T : Any> getDocumentWithToken(
        digisosId: String,
        dokumentlagerId: String,
        requestedClass: Class<out T>,
        cacheKey: String = dokumentlagerId,
        token: String,
    ): T {
        val key = "fiks:document:$cacheKey"
        valkeyClient.get(key, requestedClass)?.let { return it }
        val lockKey = "$digisosId:$cacheKey:${requestedClass.name}"
        return withDedup(lockKey) {
            valkeyClient.get(key, requestedClass)
                ?: fiksClient.hentDokumentMedToken(digisosId, dokumentlagerId, requestedClass, token).also {
                    valkeyClient.set(key, it, DOCUMENT_TTL)
                }
        }
    }

    suspend fun <T : Any> getDocument(
        digisosId: String,
        dokumentlagerId: String,
        requestedClass: Class<out T>,
        cacheKey: String = dokumentlagerId,
        caller: Caller,
    ): T {
        val key = "fiks:document:$cacheKey"
        valkeyClient.get(key, requestedClass)?.let { return it }

        val lockKey = "$digisosId:$cacheKey:${requestedClass.name}"
        return withDedup(lockKey) {
            valkeyClient.get(key, requestedClass)
                ?: fiksClient.hentDokument(digisosId, dokumentlagerId, requestedClass, caller).also {
                    valkeyClient.set(key, it, DOCUMENT_TTL)
                }
        }
    }

    private suspend fun <T> withDedup(
        key: String,
        block: suspend () -> T,
    ): T {
        val mutex = requestLocks.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            try {
                block()
            } finally {
                // Only remove if we are not the last holder; see class-level note.
                requestLocks.remove(key, mutex)
            }
        }
    }
}
