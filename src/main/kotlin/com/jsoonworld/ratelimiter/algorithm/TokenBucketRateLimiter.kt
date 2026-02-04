package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.model.RateLimitResult
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull

/**
 * Token Bucket Algorithm 구현
 *
 * 특징:
 * - 버킷에 일정 용량의 토큰 저장
 * - 요청마다 토큰 소비
 * - 일정 속도로 토큰 리필
 * - 버스트 트래픽 허용 (버킷이 가득 차 있을 때)
 */
@Component
class TokenBucketRateLimiter(
    private val redisTemplate: ReactiveStringRedisTemplate
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val KEY_PREFIX = "rate_limiter:token_bucket:"
        private const val DEFAULT_CAPACITY = 100L
        private const val DEFAULT_REFILL_RATE = 10L // tokens per second

        // Lua script for atomic token bucket operation
        private val TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])

            -- Initialize if not exists
            if tokens == nil then
                tokens = capacity
                last_refill = now
            end

            -- Calculate tokens to add based on time passed
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

            -- Save state
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
            redis.call('EXPIRE', key, math.ceil(capacity / refill_rate) + 1)

            return {allowed, math.floor(remaining), math.ceil((capacity - remaining) / refill_rate)}
        """.trimIndent()
    }

    override suspend fun tryAcquire(key: String, permits: Long): RateLimitResult {
        val redisKey = "$KEY_PREFIX$key"
        val now = System.currentTimeMillis() / 1000.0

        return try {
            val script = RedisScript.of<List<Long>>(TOKEN_BUCKET_SCRIPT, List::class.java as Class<List<Long>>)

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
            // Fail open - allow request if Redis is unavailable
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
}
