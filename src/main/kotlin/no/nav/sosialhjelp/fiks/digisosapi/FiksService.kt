package no.nav.sosialhjelp.fiks.digisosapi

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.utils.logger
import no.nav.sosialhjelp.fiks.valkey.ValkeyClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val DIGISOSSAK_TTL: Duration = Duration.ofSeconds(60)
private val DOCUMENT_TTL: Duration = Duration.ofHours(1)

/**
 * Koordinerer kall mot Fiks:
 * - Sjekker Valkey-cache før HTTP-kall
 * - Sørger for at samtidige kall med samme nøkkel ikke går parallelt (mutex-locking)
 */
class FiksService(
    private val fiksClient: FiksClient,
    private val valkeyClient: ValkeyClient,
) {
    private val log by logger()

    private val requestLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun getAllSoknader(token: String): List<DigisosSak> =
        fiksClient
            .hentAlleDigisosSaker(token)
            .also { log.info("Hentet ${it.size} DigisosSaker") }

    suspend fun getSoknad(
        digisosId: String,
        token: String,
    ): DigisosSak {
        val cacheKey = "fiks:digisossak:$digisosId"
        valkeyClient.get(cacheKey, DigisosSak::class.java)?.let { return it }

        val mutex = requestLocks.computeIfAbsent(cacheKey) { Mutex() }
        return try {
            mutex.withLock {
                // Double-checked: another coroutine may have populated the cache while we waited
                valkeyClient.get(cacheKey, DigisosSak::class.java)?.let { return it }
                fiksClient.hentDigisosSak(digisosId, token).also {
                    valkeyClient.set(cacheKey, it, DIGISOSSAK_TTL)
                }
            }
        } finally {
            requestLocks.remove(cacheKey)
        }
    }

    suspend fun <T : Any> getDocument(
        digisosId: String,
        dokumentlagerId: String,
        requestedClass: Class<out T>,
        cacheKey: String = dokumentlagerId,
        token: String,
    ): T {
        val key = "fiks:document:$cacheKey"
        valkeyClient.get(key, requestedClass)?.let { return it }

        val lockKey = "$digisosId:$cacheKey:${requestedClass.name}"
        val mutex = requestLocks.computeIfAbsent(lockKey) { Mutex() }
        return try {
            mutex.withLock {
                valkeyClient.get(key, requestedClass)?.let { return it }
                fiksClient.hentDokument(digisosId, dokumentlagerId, requestedClass, token).also {
                    valkeyClient.set(key, it, DOCUMENT_TTL)
                }
            }
        } finally {
            requestLocks.remove(lockKey)
        }
    }
}
