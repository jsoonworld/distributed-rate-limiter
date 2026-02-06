package com.jsoonworld.ratelimiter.core

/**
 * Rate Limiter interface.
 * All rate limiting algorithms must implement this interface.
 */
interface RateLimiter {

    /**
     * Determines whether a request should be allowed.
     * @param key Client identifier (IP, API Key, User ID, etc.)
     * @param permits Number of tokens/requests to consume
     * @return RateLimitResult with allowed status and remaining quota info
     */
    suspend fun tryAcquire(key: String, permits: Long = 1): RateLimitResult

    /**
     * Gets the current remaining tokens/requests for a key.
     * @param key Client identifier
     * @return Remaining quota
     */
    suspend fun getRemainingLimit(key: String): Long

    /**
     * Resets the rate limit for a specific key.
     * @param key Client identifier
     */
    suspend fun reset(key: String)

    /**
     * Returns the algorithm type used by this rate limiter.
     */
    val algorithm: RateLimitAlgorithm
}
