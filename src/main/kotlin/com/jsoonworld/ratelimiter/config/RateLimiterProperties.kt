package com.jsoonworld.ratelimiter.config

import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rate-limiter.default")
data class RateLimiterProperties(
    val algorithm: RateLimitAlgorithm = RateLimitAlgorithm.TOKEN_BUCKET,
    val capacity: Long = 100,
    val refillRate: Long = 10,
    val windowSize: Long = 60,
    val trustProxy: Boolean = false
)
