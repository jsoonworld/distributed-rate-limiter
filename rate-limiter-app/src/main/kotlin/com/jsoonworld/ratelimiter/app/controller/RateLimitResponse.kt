package com.jsoonworld.ratelimiter.app.controller

import com.jsoonworld.ratelimiter.core.RateLimitAlgorithm
import com.jsoonworld.ratelimiter.core.RateLimitResult

/**
 * Rate Limit response DTO.
 *
 * @property resetAfterSeconds Seconds until the bucket/window is fully replenished (duration, not epoch timestamp)
 */
data class RateLimitResponse(
    val allowed: Boolean,
    val key: String,
    val algorithm: String,
    val remaining: Long,
    val resetAfterSeconds: Long,
    val retryAfterSeconds: Long,
    val message: String
) {
    companion object {
        fun fromResult(
            result: RateLimitResult,
            key: String,
            algorithm: RateLimitAlgorithm
        ) = RateLimitResponse(
            allowed = result.allowed,
            key = key,
            algorithm = algorithm.name,
            remaining = result.remaining,
            resetAfterSeconds = result.resetAfterSeconds,
            retryAfterSeconds = result.retryAfterSeconds,
            message = if (result.allowed) "Request allowed" else "Rate limit exceeded"
        )
    }
}
