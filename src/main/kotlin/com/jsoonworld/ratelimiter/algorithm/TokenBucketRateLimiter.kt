package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.model.RateLimitResult
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component

@Component
class TokenBucketRateLimiter(
    private val redisTemplate: ReactiveStringRedisTemplate
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun tryAcquire(key: String, permits: Long): RateLimitResult {
        val redisKey = "$KEY_PREFIX$key"
        val now = System.currentTimeMillis() / 1000.0

        return try {
            val script = RedisScript.of<List<*>>(
                TOKEN_BUCKET_SCRIPT,
                List::class.java
            )

            val result = redisTemplate.execute(
                script,
                listOf(redisKey),
                listOf(
                    DEFAULT_CAPACITY.toString(),
                    DEFAULT_REFILL_RATE.toString(),
                    now.toString(),
                    permits.toString()
                )
            ).collectList().awaitSingle()

            val flatResult = result.flatten().map { it.toString().toLong() }

            val allowed = flatResult[0] == 1L
            val remaining = flatResult[1]
            val resetTime = flatResult[2]

            if (allowed) {
                logger.debug("Request allowed for key: $key, remaining: $remaining")
                RateLimitResult.allowed(remaining, resetTime)
            } else {
                logger.debug("Request denied for key: $key, retry after: $resetTime seconds")
                RateLimitResult.denied(resetTime, resetTime)
            }
        } catch (e: Exception) {
            logger.error("Error executing rate limit check for key: $key", e)
            RateLimitResult.allowed(DEFAULT_CAPACITY, 0)
        }
    }

    override suspend fun getRemainingLimit(key: String): Long {
        val redisKey = "$KEY_PREFIX$key"
        return try {
            val tokens = redisTemplate.opsForHash<String, String>()
                .get(redisKey, "tokens")
                .awaitSingleOrNull()
            tokens?.toLongOrNull() ?: DEFAULT_CAPACITY
        } catch (e: Exception) {
            logger.error("Error getting remaining limit for key: $key", e)
            DEFAULT_CAPACITY
        }
    }

    override suspend fun reset(key: String) {
        val redisKey = "$KEY_PREFIX$key"
        try {
            redisTemplate.delete(redisKey).awaitSingle()
            logger.info("Rate limit reset for key: $key")
        } catch (e: Exception) {
            logger.error("Error resetting rate limit for key: $key", e)
        }
    }

    companion object {
        private const val KEY_PREFIX = "rate_limiter:token_bucket:"
        private const val DEFAULT_CAPACITY = 100L
        private const val DEFAULT_REFILL_RATE = 10L

        private val TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])

            if tokens == nil then
                tokens = capacity
                last_refill = now
            end

            local time_passed = now - last_refill
            local tokens_to_add = time_passed * refill_rate
            tokens = math.min(capacity, tokens + tokens_to_add)

            local allowed = 0
            local remaining = tokens

            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
                remaining = tokens
            end

            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
            redis.call('EXPIRE', key, math.ceil(capacity / refill_rate) + 1)

            return {allowed, math.floor(remaining), math.ceil((capacity - remaining) / refill_rate)}
        """.trimIndent()
    }
}
