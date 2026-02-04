package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.model.RateLimitResult
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull

/**
 * Sliding Window Log Algorithm 구현
 *
 * 특징:
 * - 각 요청의 타임스탬프를 저장
 * - 정확한 rate limiting (경계 문제 없음)
 * - 메모리 사용량이 높을 수 있음
 */
@Component
class SlidingWindowRateLimiter(
    private val redisTemplate: ReactiveStringRedisTemplate
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val KEY_PREFIX = "rate_limiter:sliding_window:"
        private const val DEFAULT_WINDOW_SIZE = 60L // seconds
        private const val DEFAULT_MAX_REQUESTS = 100L

        // Lua script for atomic sliding window operation
        private val SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local window_size = tonumber(ARGV[1])
            local max_requests = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            -- Remove expired entries
            local window_start = now - window_size
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

            -- Count current requests in window
            local current_count = redis.call('ZCARD', key)

            local allowed = 0
            local remaining = max_requests - current_count

            if current_count + requested <= max_requests then
                -- Add new request(s)
                for i = 1, requested do
                    redis.call('ZADD', key, now, now .. ':' .. i .. ':' .. math.random())
                end
                allowed = 1
                remaining = max_requests - current_count - requested
            end

            -- Set expiration
            redis.call('EXPIRE', key, window_size + 1)

            -- Calculate when the oldest request will expire
            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local reset_time = window_size
            if oldest[2] then
                reset_time = math.ceil(tonumber(oldest[2]) + window_size - now)
            end

            return {allowed, math.max(0, remaining), reset_time}
        """.trimIndent()
    }

    override suspend fun tryAcquire(key: String, permits: Long): RateLimitResult {
        val redisKey = "$KEY_PREFIX$key"
        val now = System.currentTimeMillis() / 1000.0

        return try {
            val script = RedisScript.of<List<Long>>(SLIDING_WINDOW_SCRIPT, List::class.java as Class<List<Long>>)

            val result = redisTemplate.execute(
                script,
                listOf(redisKey),
                listOf(
                    DEFAULT_WINDOW_SIZE.toString(),
                    DEFAULT_MAX_REQUESTS.toString(),
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
            RateLimitResult.allowed(DEFAULT_MAX_REQUESTS, 0)
        }
    }

    override suspend fun getRemainingLimit(key: String): Long {
        val redisKey = "$KEY_PREFIX$key"
        val now = System.currentTimeMillis() / 1000.0
        val windowStart = now - DEFAULT_WINDOW_SIZE

        return try {
            // Remove expired and count
            redisTemplate.opsForZSet()
                .removeRangeByScore(redisKey, Double.NEGATIVE_INFINITY, windowStart)
                .awaitSingleOrNull()

            val count = redisTemplate.opsForZSet()
                .size(redisKey)
                .awaitSingleOrNull() ?: 0

            DEFAULT_MAX_REQUESTS - count
        } catch (e: Exception) {
            logger.error("Error getting remaining limit for key: $key", e)
            DEFAULT_MAX_REQUESTS
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
}
