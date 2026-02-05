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

class TokenBucketRateLimiter(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val properties: RateLimiterProperties
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        require(properties.capacity > 0) { "capacity must be positive, got: ${properties.capacity}" }
        require(properties.refillRate > 0) { "refillRate must be positive, got: ${properties.refillRate}" }
    }

    override suspend fun tryAcquire(key: String, permits: Long): RateLimitResult {
        require(permits > 0) { "permits must be positive, got: $permits" }
        require(permits <= properties.capacity) { "permits cannot exceed capacity (${properties.capacity}), got: $permits" }

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
                    properties.capacity.toString(),
                    properties.refillRate.toString(),
                    now.toString(),
                    permits.toString()
                )
            ).collectList().awaitSingle()

            val flatResult = result.flatten().map { it.toString().toLong() }

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
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed for key: $key, applying fail-open policy", e)
            RateLimitResult.allowed(properties.capacity, 0)
        } catch (e: RedisException) {
            logger.warn("Redis error for key: $key, applying fail-open policy", e)
            RateLimitResult.allowed(properties.capacity, 0)
        } catch (e: Exception) {
            logger.error("Unexpected error executing rate limit check for key: $key", e)
            throw RateLimitException("Rate limit check failed", e)
        }
    }

    override suspend fun getRemainingLimit(key: String): Long {
        val redisKey = "$KEY_PREFIX$key"
        val now = System.currentTimeMillis() / 1000.0
        return try {
            val script = RedisScript.of(GET_REMAINING_SCRIPT, Long::class.java)
            redisTemplate.execute(
                script,
                listOf(redisKey),
                listOf(properties.capacity.toString(), properties.refillRate.toString(), now.toString())
            ).next().awaitSingleOrNull() ?: properties.capacity
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed for key: $key, applying fail-open policy", e)
            properties.capacity
        } catch (e: RedisException) {
            logger.warn("Redis error for key: $key, applying fail-open policy", e)
            properties.capacity
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
        private const val KEY_PREFIX = "rate_limiter:token_bucket:"

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
            local retry_after = 0
            local reset_time = math.ceil((capacity - tokens) / refill_rate)

            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
                remaining = tokens
                reset_time = math.ceil((capacity - remaining) / refill_rate)
            else
                -- Calculate time until we have enough tokens for this request
                local tokens_needed = requested - tokens
                retry_after = math.ceil(tokens_needed / refill_rate)
            end

            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
            redis.call('EXPIRE', key, math.ceil(capacity / refill_rate) + 1)

            return {allowed, math.floor(remaining), reset_time, retry_after}
        """.trimIndent()

        private val GET_REMAINING_SCRIPT = """
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
