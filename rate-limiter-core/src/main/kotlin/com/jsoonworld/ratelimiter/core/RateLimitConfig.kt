package com.jsoonworld.ratelimiter.core

/**
 * Token Bucket algorithm configuration.
 */
data class TokenBucketConfig(
    val capacity: Long = DEFAULT_CAPACITY,
    val refillRate: Long = DEFAULT_REFILL_RATE,
    val keyPrefix: String = DEFAULT_KEY_PREFIX
) {
    init {
        require(capacity > 0) { "capacity must be positive, got: $capacity" }
        require(refillRate > 0) { "refillRate must be positive, got: $refillRate" }
    }

    companion object {
        const val DEFAULT_CAPACITY = 100L
        const val DEFAULT_REFILL_RATE = 10L
        const val DEFAULT_KEY_PREFIX = "rate_limiter:token_bucket:"
    }
}

/**
 * Sliding Window algorithm configuration.
 */
data class SlidingWindowConfig(
    val windowSize: Long = DEFAULT_WINDOW_SIZE,
    val maxRequests: Long = DEFAULT_MAX_REQUESTS,
    val keyPrefix: String = DEFAULT_KEY_PREFIX
) {
    init {
        require(windowSize > 0) { "windowSize must be positive, got: $windowSize" }
        require(maxRequests > 0) { "maxRequests must be positive, got: $maxRequests" }
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 60L
        const val DEFAULT_MAX_REQUESTS = 100L
        const val DEFAULT_KEY_PREFIX = "rate_limiter:sliding_window:"
    }
}

/**
 * Combined rate limiter configuration.
 */
data class RateLimitConfig(
    val algorithm: RateLimitAlgorithm = RateLimitAlgorithm.TOKEN_BUCKET,
    val tokenBucket: TokenBucketConfig = TokenBucketConfig(),
    val slidingWindow: SlidingWindowConfig = SlidingWindowConfig()
)
