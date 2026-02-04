package com.jsoonworld.ratelimiter.service

import com.jsoonworld.ratelimiter.algorithm.RateLimiter
import com.jsoonworld.ratelimiter.algorithm.SlidingWindowRateLimiter
import com.jsoonworld.ratelimiter.algorithm.TokenBucketRateLimiter
import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm
import com.jsoonworld.ratelimiter.model.RateLimitResult
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RateLimitService(
    private val tokenBucketRateLimiter: TokenBucketRateLimiter,
    private val slidingWindowRateLimiter: SlidingWindowRateLimiter,
    private val meterRegistry: MeterRegistry
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun checkRateLimit(
        key: String,
        algorithm: RateLimitAlgorithm = RateLimitAlgorithm.TOKEN_BUCKET,
        permits: Long = 1
    ): RateLimitResult {
        val timer = Timer.start(meterRegistry)

        val rateLimiter: RateLimiter = when (algorithm) {
            RateLimitAlgorithm.TOKEN_BUCKET -> tokenBucketRateLimiter
            RateLimitAlgorithm.SLIDING_WINDOW_LOG -> slidingWindowRateLimiter
            else -> {
                logger.warn("Algorithm $algorithm not implemented, falling back to TOKEN_BUCKET")
                tokenBucketRateLimiter
            }
        }

        val result = rateLimiter.tryAcquire(key, permits)

        // Record metrics
        timer.stop(
            Timer.builder("rate_limiter.check")
                .tag("algorithm", algorithm.name)
                .tag("allowed", result.allowed.toString())
                .register(meterRegistry)
        )

        meterRegistry.counter(
            "rate_limiter.requests",
            "algorithm", algorithm.name,
            "allowed", result.allowed.toString()
        ).increment()

        return result
    }

    suspend fun getRemainingLimit(
        key: String,
        algorithm: RateLimitAlgorithm = RateLimitAlgorithm.TOKEN_BUCKET
    ): Long {
        val rateLimiter: RateLimiter = when (algorithm) {
            RateLimitAlgorithm.TOKEN_BUCKET -> tokenBucketRateLimiter
            RateLimitAlgorithm.SLIDING_WINDOW_LOG -> slidingWindowRateLimiter
            else -> tokenBucketRateLimiter
        }
        return rateLimiter.getRemainingLimit(key)
    }

    suspend fun resetLimit(
        key: String,
        algorithm: RateLimitAlgorithm = RateLimitAlgorithm.TOKEN_BUCKET
    ) {
        val rateLimiter: RateLimiter = when (algorithm) {
            RateLimitAlgorithm.TOKEN_BUCKET -> tokenBucketRateLimiter
            RateLimitAlgorithm.SLIDING_WINDOW_LOG -> slidingWindowRateLimiter
            else -> tokenBucketRateLimiter
        }
        rateLimiter.reset(key)
    }
}
