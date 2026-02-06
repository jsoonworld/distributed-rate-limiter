package com.jsoonworld.ratelimiter.core

import com.jsoonworld.ratelimiter.core.algorithm.SlidingWindowRateLimiter
import com.jsoonworld.ratelimiter.core.algorithm.TokenBucketRateLimiter
import com.jsoonworld.ratelimiter.core.redis.RedisClient

/**
 * Factory for creating RateLimiter instances.
 */
object RateLimiterFactory {

    /**
     * Create a RateLimiter based on the configuration.
     */
    fun create(
        redisClient: RedisClient,
        config: RateLimitConfig = RateLimitConfig()
    ): RateLimiter {
        return when (config.algorithm) {
            RateLimitAlgorithm.TOKEN_BUCKET -> TokenBucketRateLimiter(
                redisClient = redisClient,
                config = config.tokenBucket
            )
            RateLimitAlgorithm.SLIDING_WINDOW -> SlidingWindowRateLimiter(
                redisClient = redisClient,
                config = config.slidingWindow
            )
            else -> throw IllegalArgumentException("Algorithm ${config.algorithm} is not implemented")
        }
    }

    /**
     * Create a TokenBucketRateLimiter.
     */
    fun createTokenBucket(
        redisClient: RedisClient,
        config: TokenBucketConfig = TokenBucketConfig()
    ): TokenBucketRateLimiter = TokenBucketRateLimiter(redisClient, config)

    /**
     * Create a SlidingWindowRateLimiter.
     */
    fun createSlidingWindow(
        redisClient: RedisClient,
        config: SlidingWindowConfig = SlidingWindowConfig()
    ): SlidingWindowRateLimiter = SlidingWindowRateLimiter(redisClient, config)
}
