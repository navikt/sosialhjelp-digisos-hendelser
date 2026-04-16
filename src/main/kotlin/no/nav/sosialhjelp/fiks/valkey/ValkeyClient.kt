package no.nav.sosialhjelp.fiks.valkey

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisStringCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.sosialhjelp.fiks.utils.logger
import java.io.Closeable
import java.time.Duration

class ValkeyClient(
    host: String,
    port: Int,
) : Closeable {
    private val log by logger()

    private val objectMapper: ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private val redisClient: RedisClient = RedisClient.create(RedisURI.create(host, port))

    private val connection: StatefulRedisConnection<String, String>? =
        try {
            redisClient.connect().also { log.info("Connected to Valkey at $host:$port") }
        } catch (e: Exception) {
            log.warn("Could not connect to Valkey at $host:$port — caching disabled", e)
            null
        }

    suspend fun <T : Any> get(
        key: String,
        type: Class<T>,
    ): T? {
        val commands: RedisStringCommands<String, String> = connection?.sync() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                commands.get(key)?.let { objectMapper.readValue(it, type) }
            } catch (e: Exception) {
                log.warn("Cache get failed for key='$key': ${e.message}")
                null
            }
        }
    }

    suspend fun set(
        key: String,
        value: Any,
        ttl: Duration,
    ) {
        val commands: RedisStringCommands<String, String> = connection?.sync() ?: return
        withContext(Dispatchers.IO) {
            try {
                val json = objectMapper.writeValueAsString(value)
                commands.setex(key, ttl.toSeconds(), json)
            } catch (e: Exception) {
                log.warn("Cache set failed for key='$key': ${e.message}")
            }
        }
    }

    override fun close() {
        connection?.close()
        redisClient.shutdown()
    }
}
