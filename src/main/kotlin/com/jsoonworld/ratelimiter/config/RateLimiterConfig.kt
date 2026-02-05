package com.jsoonworld.ratelimiter.config

import com.jsoonworld.ratelimiter.algorithm.RateLimiter
import com.jsoonworld.ratelimiter.algorithm.SlidingWindowRateLimiter
import com.jsoonworld.ratelimiter.algorithm.TokenBucketRateLimiter
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Configuration class for rate limiter algorithm selection.
 *
 * The algorithm is selected based on the rate-limiter.default.algorithm property:
 * - TOKEN_BUCKET: Uses Token Bucket algorithm (default)
 * - SLIDING_WINDOW: Uses Sliding Window Log algorithm
 */
@Configuration
class RateLimiterConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    @Primary
    @ConditionalOnProperty(
        name = ["rate-limiter.default.algorithm"],
        havingValue = "TOKEN_BUCKET",
        matchIfMissing = true
    )
    fun tokenBucketRateLimiter(
        redisTemplate: ReactiveStringRedisTemplate,
        properties: RateLimiterProperties
    ): RateLimiter {
        logger.info("Creating TokenBucketRateLimiter with capacity: ${properties.capacity}, refillRate: ${properties.refillRate}")
        return TokenBucketRateLimiter(redisTemplate, properties)
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
        name = ["rate-limiter.default.algorithm"],
        havingValue = "SLIDING_WINDOW"
    )
    fun slidingWindowRateLimiter(
        redisTemplate: ReactiveStringRedisTemplate,
        properties: RateLimiterProperties
    ): RateLimiter {
        logger.info("Creating SlidingWindowRateLimiter with capacity: ${properties.capacity}, windowSize: ${properties.windowSize}")
        return SlidingWindowRateLimiter(redisTemplate, properties)
    }
}
