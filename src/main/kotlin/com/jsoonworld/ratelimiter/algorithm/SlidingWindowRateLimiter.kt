package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.config.RateLimiterProperties
import com.jsoonworld.ratelimiter.exception.RateLimitException
import com.jsoonworld.ratelimiter.model.RateLimitResult
import io.lettuce.core.RedisException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript

class SlidingWindowRateLimiter(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val properties: RateLimiterProperties
) : RateLimiter {

    private val maxRequests: Long = properties.capacity
    private val windowSizeSeconds: Long = properties.windowSize
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        require(properties.capacity > 0) { "capacity must be positive, got: ${properties.capacity}" }
        require(properties.windowSize > 0) { "windowSize must be positive, got: ${properties.windowSize}" }
    }

    override suspend fun tryAcquire(key: String, permits: Long): RateLimitResult {
        require(permits > 0) { "permits must be positive, got: $permits" }
        require(permits <= maxRequests) { "permits cannot exceed maxRequests ($maxRequests), got: $permits" }

        val redisKey = "$KEY_PREFIX$key"
        val now = System.currentTimeMillis() / 1000.0

        return try {
            val script = RedisScript.of<List<*>>(
                SLIDING_WINDOW_SCRIPT,
                List::class.java
            )

            val result = redisTemplate.execute(
                script,
                listOf(redisKey),
                listOf(
                    windowSizeSeconds.toString(),
                    maxRequests.toString(),
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
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed for key: $key, applying fail-open policy", e)
            RateLimitResult.allowed(maxRequests, 0)
        } catch (e: RedisException) {
            logger.warn("Redis error for key: $key, applying fail-open policy", e)
            RateLimitResult.allowed(maxRequests, 0)
        } catch (e: Exception) {
            logger.error("Unexpected error executing rate limit check for key: $key", e)
            throw RateLimitException("Rate limit check failed", e)
        }
    }

    override suspend fun getRemainingLimit(key: String): Long {
        val redisKey = "$KEY_PREFIX$key"
        val now = System.currentTimeMillis() / 1000.0

        return try {
            val script = RedisScript.of<Long>(
                GET_REMAINING_SCRIPT,
                Long::class.java
            )

            val result = redisTemplate.execute(
                script,
                listOf(redisKey),
                listOf(
                    windowSizeSeconds.toString(),
                    maxRequests.toString(),
                    now.toString()
                )
            ).collectList().awaitSingle()

            result.firstOrNull() ?: maxRequests
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed for key: $key, applying fail-open policy", e)
            maxRequests
        } catch (e: RedisException) {
            logger.warn("Redis error for key: $key, applying fail-open policy", e)
            maxRequests
        } catch (e: Exception) {
            logger.error("Unexpected error getting remaining limit for key: $key", e)
            throw RateLimitException("Failed to get remaining limit", e)
        }
    }

    override suspend fun reset(key: String) {
        val redisKey = "$KEY_PREFIX$key"
        try {
            redisTemplate.delete(redisKey).awaitSingle()
            logger.info("Rate limit reset for key: $key")
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed while resetting key: $key, applying fail-open policy", e)
        } catch (e: RedisException) {
            logger.warn("Redis error while resetting key: $key, applying fail-open policy", e)
        } catch (e: Exception) {
            logger.error("Unexpected error resetting rate limit for key: $key", e)
            throw RateLimitException("Failed to reset rate limit", e)
        }
    }

    companion object {
        private const val KEY_PREFIX = "rate_limiter:sliding_window:"
        private const val DEFAULT_WINDOW_SIZE = 60L
        private const val DEFAULT_MAX_REQUESTS = 100L

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
                -- Add new requests
                for i = 1, requested do
                    redis.call('ZADD', key, now, now .. ':' .. i .. ':' .. math.random())
                end
                allowed = 1
                remaining = max_requests - current_count - requested
            end

            -- Set TTL
            redis.call('EXPIRE', key, window_size + 1)

            -- Calculate reset time (retry after)
            -- For denied requests, we need to find when N permits will be available
            -- This is when the Nth oldest entry expires (0-indexed: requested - 1)
            local reset_time = window_size
            if allowed == 0 then
                -- Need to wait for 'requested' permits to become available
                -- The Nth oldest entry (index: entries_needed - 1) will free up the Nth permit
                local entries_needed = current_count + requested - max_requests
                if entries_needed > 0 then
                    local nth_oldest = redis.call('ZRANGE', key, entries_needed - 1, entries_needed - 1, 'WITHSCORES')
                    if nth_oldest[2] then
                        reset_time = math.ceil(tonumber(nth_oldest[2]) + window_size - now)
                    end
                end
            else
                -- For allowed requests, reset time is when the oldest entry expires
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                if oldest[2] then
                    reset_time = math.ceil(tonumber(oldest[2]) + window_size - now)
                end
            end

            return {allowed, math.max(0, remaining), math.max(0, reset_time)}
        """.trimIndent()

        private val GET_REMAINING_SCRIPT = """
            local key = KEYS[1]
            local window_size = tonumber(ARGV[1])
            local max_requests = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            -- Remove expired entries atomically
            local window_start = now - window_size
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

            -- Count current requests in window
            local current_count = redis.call('ZCARD', key)

            -- Return remaining with max(0, ...) clamping to prevent negative values
            return math.max(0, max_requests - current_count)
        """.trimIndent()
    }
}
