package com.jsoonworld.ratelimiter.spring

import com.jsoonworld.ratelimiter.core.RateLimitAlgorithm
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Rate Limiter configuration properties.
 * Configurable via application.yml.
 */
@ConfigurationProperties(prefix = "rate-limiter")
data class RateLimiterProperties(
    /**
     * Enable or disable rate limiter.
     */
    val enabled: Boolean = true,

    /**
     * Default algorithm for rate limiting.
     */
    val algorithm: RateLimitAlgorithm = RateLimitAlgorithm.TOKEN_BUCKET,

    /**
     * Token Bucket algorithm settings.
     */
    val tokenBucket: TokenBucketProperties = TokenBucketProperties(),

    /**
     * Sliding Window algorithm settings.
     */
    val slidingWindow: SlidingWindowProperties = SlidingWindowProperties(),

    /**
     * Metrics collection settings.
     */
    val metrics: MetricsProperties = MetricsProperties()
)

data class TokenBucketProperties(
    val capacity: Long = 100L,
    val refillRate: Long = 10L,
    val keyPrefix: String = "rate_limiter:token_bucket:"
)

data class SlidingWindowProperties(
    val windowSize: Long = 60L,
    val maxRequests: Long = 100L,
    val keyPrefix: String = "rate_limiter:sliding_window:"
)

data class MetricsProperties(
    val enabled: Boolean = true,
    val prefix: String = "rate_limiter"
)
