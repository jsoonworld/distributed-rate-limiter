package com.jsoonworld.ratelimiter.core.algorithm

import com.jsoonworld.ratelimiter.core.*
import com.jsoonworld.ratelimiter.core.redis.RedisClient
import org.slf4j.LoggerFactory

/**
 * Sliding Window Log algorithm Rate Limiter.
 * Provides accurate rate limiting using request timestamps.
 */
class SlidingWindowRateLimiter(
    private val redisClient: RedisClient,
    private val config: SlidingWindowConfig = SlidingWindowConfig()
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val algorithm = RateLimitAlgorithm.SLIDING_WINDOW

    override suspend fun tryAcquire(key: String, permits: Long): RateLimitResult {
        require(permits > 0) { "permits must be positive, got: $permits" }
        require(permits <= config.maxRequests) { "permits cannot exceed maxRequests (${config.maxRequests}), got: $permits" }

        val redisKey = "${config.keyPrefix}$key"
        val now = System.currentTimeMillis() / 1000.0

        return try {
            @Suppress("UNCHECKED_CAST")
            val result = redisClient.executeScript(
                script = SLIDING_WINDOW_SCRIPT,
                keys = listOf(redisKey),
                args = listOf(
                    config.windowSize.toString(),
                    config.maxRequests.toString(),
                    now.toString(),
                    permits.toString()
                ),
                resultType = List::class.java as Class<List<Any>>
            )

            val flatResult = flattenResult(result)
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
            logger.warn("Error executing rate limit check for key: $key, applying fail-open policy", e)
            RateLimitResult.allowed(config.maxRequests, 0)
        }
    }

    override suspend fun getRemainingLimit(key: String): Long {
        val redisKey = "${config.keyPrefix}$key"
        val now = System.currentTimeMillis() / 1000.0

        return try {
            @Suppress("UNCHECKED_CAST")
            val result = redisClient.executeScript(
                script = GET_REMAINING_SCRIPT,
                keys = listOf(redisKey),
                args = listOf(
                    config.windowSize.toString(),
                    config.maxRequests.toString(),
                    now.toString()
                ),
                resultType = List::class.java as Class<List<Any>>
            )

            val flatResult = flattenResult(result)
            flatResult.firstOrNull() ?: config.maxRequests
        } catch (e: Exception) {
            logger.warn("Error getting remaining limit for key: $key, applying fail-open policy", e)
            config.maxRequests
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
        internal val SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local window_size = tonumber(ARGV[1])
            local max_requests = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            local window_start = now - window_size
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

            local current_count = redis.call('ZCARD', key)

            local allowed = 0
            local remaining = max_requests - current_count

            if current_count + requested <= max_requests then
                for i = 1, requested do
                    redis.call('ZADD', key, now, now .. ':' .. i .. ':' .. math.random())
                end
                allowed = 1
                remaining = max_requests - current_count - requested
            end

            redis.call('EXPIRE', key, window_size + 1)

            local reset_time = window_size
            if allowed == 0 then
                local entries_needed = current_count + requested - max_requests
                if entries_needed > 0 then
                    local nth_oldest = redis.call('ZRANGE', key, entries_needed - 1, entries_needed - 1, 'WITHSCORES')
                    if nth_oldest[2] then
                        reset_time = math.ceil(tonumber(nth_oldest[2]) + window_size - now)
                    end
                end
            else
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                if oldest[2] then
                    reset_time = math.ceil(tonumber(oldest[2]) + window_size - now)
                end
            end

            return {allowed, math.max(0, remaining), math.max(0, reset_time)}
        """.trimIndent()

        internal val GET_REMAINING_SCRIPT = """
            local key = KEYS[1]
            local window_size = tonumber(ARGV[1])
            local max_requests = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local window_start = now - window_size
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

            local current_count = redis.call('ZCARD', key)

            return math.max(0, max_requests - current_count)
        """.trimIndent()
    }
}
