package com.jsoonworld.ratelimiter.model

enum class RateLimitAlgorithm {
    TOKEN_BUCKET,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER,
    FIXED_WINDOW,
    LEAKY_BUCKET
}
