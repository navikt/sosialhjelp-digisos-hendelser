package no.nav.sosialhjelp.fiks.digisosapi

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.sosialhjelp.api.fiks.DigisosSak
import no.nav.sosialhjelp.fiks.utils.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Koordinerer kall mot Fiks:
 * - Videresender til FiksClient
 * - Sørger for at samtidige kall med samme nøkkel ikke går parallelt (mutex-locking)
 * - Henter DigisosSak og dokumenter
 */
class FiksService(
    private val fiksClient: FiksClient,
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
        val key = "DigisosSak:$digisosId"
        val mutex = requestLocks.computeIfAbsent(key) { Mutex() }
        return try {
            mutex.withLock { fiksClient.hentDigisosSak(digisosId, token) }
        } finally {
            requestLocks.remove(key)
        }
    }

    suspend fun <T : Any> getDocument(
        digisosId: String,
        dokumentlagerId: String,
        requestedClass: Class<out T>,
        cacheKey: String = dokumentlagerId,
        token: String,
    ): T {
        val key = "$digisosId:$cacheKey:${requestedClass.name}"
        val mutex = requestLocks.computeIfAbsent(key) { Mutex() }
        return try {
            mutex.withLock { fiksClient.hentDokument(digisosId, dokumentlagerId, requestedClass, token) }
        } finally {
            requestLocks.remove(key)
        }
    }
}
