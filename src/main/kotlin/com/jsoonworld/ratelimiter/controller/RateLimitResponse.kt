package com.jsoonworld.ratelimiter.controller

import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm
import com.jsoonworld.ratelimiter.model.RateLimitResult

data class RateLimitResponse(
    val allowed: Boolean,
    val key: String,
    val algorithm: String,
    val remainingTokens: Long,
    val resetTimeSeconds: Long,
    val retryAfterSeconds: Long?,
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
            remainingTokens = result.remainingTokens,
            resetTimeSeconds = result.resetTimeSeconds,
            retryAfterSeconds = result.retryAfterSeconds,
            message = if (result.allowed) "Request allowed" else "Rate limit exceeded"
        )
    }
}
