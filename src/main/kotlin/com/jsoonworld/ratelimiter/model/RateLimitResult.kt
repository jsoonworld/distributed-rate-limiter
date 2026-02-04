package com.jsoonworld.ratelimiter.model

data class RateLimitResult(
    val allowed: Boolean,
    val remainingTokens: Long,
    val resetTimeSeconds: Long,
    val retryAfterSeconds: Long? = null
) {
    companion object {
        fun allowed(remaining: Long, resetTime: Long) = RateLimitResult(
            allowed = true,
            remainingTokens = remaining,
            resetTimeSeconds = resetTime
        )

        fun denied(resetTime: Long, retryAfter: Long) = RateLimitResult(
            allowed = false,
            remainingTokens = 0,
            resetTimeSeconds = resetTime,
            retryAfterSeconds = retryAfter
        )
    }
}
