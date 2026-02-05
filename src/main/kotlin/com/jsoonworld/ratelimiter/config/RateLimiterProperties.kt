package com.jsoonworld.ratelimiter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rate-limiter.default")
data class RateLimiterProperties(
    val algorithm: String = "TOKEN_BUCKET",
    val capacity: Long = 100,
    val refillRate: Long = 10,
    val windowSize: Long = 60
)
