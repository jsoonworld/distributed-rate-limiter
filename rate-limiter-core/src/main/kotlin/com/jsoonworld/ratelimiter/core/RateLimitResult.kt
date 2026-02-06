package com.jsoonworld.ratelimiter.core

/**
 * Result of a rate limit check.
 *
 * @property allowed Whether the request is allowed
 * @property remaining Number of remaining requests/tokens
 * @property resetAfterSeconds Seconds until the bucket/window is fully replenished (duration, not epoch timestamp)
 * @property retryAfterSeconds Seconds to wait before retrying (only meaningful when denied)
 */
data class RateLimitResult(
    val allowed: Boolean,
    val remaining: Long,
    val resetAfterSeconds: Long,
    val retryAfterSeconds: Long = 0
) {
    companion object {
        fun allowed(remaining: Long, resetAfterSeconds: Long) = RateLimitResult(
            allowed = true,
            remaining = remaining,
            resetAfterSeconds = resetAfterSeconds,
            retryAfterSeconds = 0
        )

        fun denied(resetAfterSeconds: Long, retryAfterSeconds: Long) = RateLimitResult(
            allowed = false,
            remaining = 0,
            resetAfterSeconds = resetAfterSeconds,
            retryAfterSeconds = retryAfterSeconds
        )
    }
}
