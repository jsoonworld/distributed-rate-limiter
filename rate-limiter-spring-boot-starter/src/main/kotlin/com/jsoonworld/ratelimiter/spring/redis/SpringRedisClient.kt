package com.jsoonworld.ratelimiter.spring.redis

import com.jsoonworld.ratelimiter.core.redis.RedisClient
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript

/**
 * Spring Data Redis implementation of RedisClient.
 */
class SpringRedisClient(
    private val redisTemplate: ReactiveStringRedisTemplate
) : RedisClient {

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> executeScript(
        script: String,
        keys: List<String>,
        args: List<String>,
        resultType: Class<T>
    ): T {
        val redisScript = RedisScript.of<Any>(script, resultType as Class<Any>)

        return if (List::class.java.isAssignableFrom(resultType)) {
            // For List return types, collect all results
            redisTemplate.execute(redisScript, keys, args)
                .collectList()
                .awaitSingle() as T
        } else {
            // For single value return types, get first result using next() to convert Flux to Mono
            redisTemplate.execute(redisScript, keys, args)
                .next()
                .awaitSingleOrNull() as T
        }
    }

    override suspend fun delete(key: String): Boolean {
        return redisTemplate.delete(key).awaitSingle() > 0
    }
}
