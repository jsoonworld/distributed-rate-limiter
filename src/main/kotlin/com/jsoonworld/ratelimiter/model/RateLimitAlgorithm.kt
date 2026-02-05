package com.jsoonworld.ratelimiter.model

/**
 * Rate limiting algorithms.
 *
 * @property implemented Whether this algorithm is currently implemented.
 */
enum class RateLimitAlgorithm(val implemented: Boolean) {
    /** Token bucket algorithm - allows burst traffic with token refill mechanism. */
    TOKEN_BUCKET(implemented = true),

    /** Sliding window log - accurate rate limiting using request timestamps. */
    SLIDING_WINDOW_LOG(implemented = true),

    /** Sliding window counter - approximate sliding window using fixed windows. Not yet implemented. */
    SLIDING_WINDOW_COUNTER(implemented = false),

    /** Fixed window - simple time-based windows. Not yet implemented. */
    FIXED_WINDOW(implemented = false),

    /** Leaky bucket - smooth rate limiting with queue. Not yet implemented. */
    LEAKY_BUCKET(implemented = false);

    companion object {
        /** Returns all currently implemented algorithms. */
        fun implementedAlgorithms(): List<RateLimitAlgorithm> = entries.filter { it.implemented }
    }
}
