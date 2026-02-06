package com.jsoonworld.ratelimiter.core.algorithm

import com.jsoonworld.ratelimiter.core.*
import com.jsoonworld.ratelimiter.core.redis.RedisClient
import org.slf4j.LoggerFactory

/**
 * Token Bucket algorithm Rate Limiter.
 * Allows burst traffic with a token refill mechanism.
 */
class TokenBucketRateLimiter(
    private val redisClient: RedisClient,
    private val config: TokenBucketConfig = TokenBucketConfig()
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val algorithm = RateLimitAlgorithm.TOKEN_BUCKET

    override suspend fun tryAcquire(key: String, permits: Long): RateLimitResult {
        require(permits > 0) { "permits must be positive, got: $permits" }
        require(permits <= config.capacity) { "permits cannot exceed capacity (${config.capacity}), got: $permits" }

        val redisKey = "${config.keyPrefix}$key"
        val now = System.currentTimeMillis() / 1000.0

        return try {
            @Suppress("UNCHECKED_CAST")
            val result = redisClient.executeScript(
                script = TOKEN_BUCKET_SCRIPT,
                keys = listOf(redisKey),
                args = listOf(
                    config.capacity.toString(),
                    config.refillRate.toString(),
                    now.toString(),
                    permits.toString()
                ),
                resultType = List::class.java as Class<List<Any>>
            )

            val flatResult = flattenResult(result)
            val allowed = flatResult[0] == 1L
            val remaining = flatResult[1]
            val resetTime = flatResult[2]
            val retryAfter = flatResult[3]

            if (allowed) {
                logger.debug("Request allowed for key: $key, remaining: $remaining")
                RateLimitResult.allowed(remaining, resetTime)
            } else {
                logger.debug("Request denied for key: $key, retry after: $retryAfter seconds")
                RateLimitResult.denied(resetTime, retryAfter)
            }
        } catch (e: Exception) {
            logger.warn("Error executing rate limit check for key: $key, applying fail-open policy", e)
            RateLimitResult.allowed(config.capacity, 0)
        }
    }

    override suspend fun getRemainingLimit(key: String): Long {
        val redisKey = "${config.keyPrefix}$key"
        val now = System.currentTimeMillis() / 1000.0

        return try {
            val result = redisClient.executeScript(
                script = GET_REMAINING_SCRIPT,
                keys = listOf(redisKey),
                args = listOf(
                    config.capacity.toString(),
                    config.refillRate.toString(),
                    now.toString()
                ),
                resultType = Long::class.java
            )
            result
        } catch (e: Exception) {
            logger.warn("Error getting remaining limit for key: $key, applying fail-open policy", e)
            config.capacity
        }
    }

    override suspend fun reset(key: String) {
        val redisKey = "${config.keyPrefix}$key"
        try {
            redisClient.delete(redisKey)
            logger.info("Rate limit reset for key: $key")
        } catch (e: Exception) {
            logger.warn("Error resetting rate limit for key: $key, applying fail-open policy", e)
        }
    }

    private fun flattenResult(result: Any): List<Long> {
        return when (result) {
            is List<*> -> result.flatMap { item ->
                when (item) {
                    is List<*> -> item.map { it.toString().toLong() }
                    is Number -> listOf(item.toLong())
                    else -> listOf(item.toString().toLong())
                }
            }
            is Number -> listOf(result.toLong())
            else -> listOf(result.toString().toLong())
        }
    }

    companion object {
        internal val TOKEN_BUCKET_SCRIPT = """
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
            local retry_after = 0
            local reset_time = math.ceil((capacity - tokens) / refill_rate)

            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
                remaining = tokens
                reset_time = math.ceil((capacity - remaining) / refill_rate)
            else
                local tokens_needed = requested - tokens
                retry_after = math.ceil(tokens_needed / refill_rate)
            end

            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
            redis.call('EXPIRE', key, math.ceil(capacity / refill_rate) + 1)

            return {allowed, math.floor(remaining), reset_time, retry_after}
        """.trimIndent()

        internal val GET_REMAINING_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])

            if tokens == nil then
                return capacity
            end

            local time_passed = math.max(0, now - last_refill)
            local tokens_to_add = time_passed * refill_rate
            tokens = math.min(capacity, tokens + tokens_to_add)

            return math.floor(tokens)
        """.trimIndent()
    }
}
