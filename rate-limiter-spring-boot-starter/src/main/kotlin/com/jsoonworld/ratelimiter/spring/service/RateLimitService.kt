package com.jsoonworld.ratelimiter.spring.service

import com.jsoonworld.ratelimiter.core.RateLimitAlgorithm
import com.jsoonworld.ratelimiter.core.RateLimitResult
import com.jsoonworld.ratelimiter.core.RateLimiter
import com.jsoonworld.ratelimiter.core.UnsupportedAlgorithmException
import com.jsoonworld.ratelimiter.core.algorithm.SlidingWindowRateLimiter
import com.jsoonworld.ratelimiter.core.algorithm.TokenBucketRateLimiter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory

/**
 * Rate Limit service with metrics collection.
 * Provides a high-level API for rate limiting operations.
 */
class RateLimitService(
    private val tokenBucketRateLimiter: TokenBucketRateLimiter,
    private val slidingWindowRateLimiter: SlidingWindowRateLimiter,
    private val defaultAlgorithm: RateLimitAlgorithm,
    private val meterRegistry: MeterRegistry?,
    private val metricsEnabled: Boolean = true,
    private val metricsPrefix: String = "rate_limiter"
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Check rate limit for a key.
     */
    suspend fun checkRateLimit(
        key: String,
        algorithm: RateLimitAlgorithm = defaultAlgorithm,
        permits: Long = 1
    ): RateLimitResult {
        logger.debug("Checking rate limit for key: ${maskKey(key)}, algorithm: $algorithm, permits: $permits")

        val timer = if (metricsEnabled && meterRegistry != null) {
            Timer.start(meterRegistry)
        } else null

        val rateLimiter = selectRateLimiter(algorithm)
        val result = rateLimiter.tryAcquire(key, permits)

        timer?.let { recordMetrics(it, algorithm, result) }

        return result
    }

    /**
     * Get remaining limit for a key.
     */
    suspend fun getRemainingLimit(
        key: String,
        algorithm: RateLimitAlgorithm = defaultAlgorithm
    ): Long {
        logger.debug("Getting remaining limit for key: ${maskKey(key)}, algorithm: $algorithm")
        val rateLimiter = selectRateLimiter(algorithm)
        return rateLimiter.getRemainingLimit(key)
    }

    /**
     * Reset rate limit for a key.
     */
    suspend fun resetLimit(
        key: String,
        algorithm: RateLimitAlgorithm = defaultAlgorithm
    ) {
        logger.info("Resetting rate limit for key: ${maskKey(key)}, algorithm: $algorithm")
        val rateLimiter = selectRateLimiter(algorithm)
        rateLimiter.reset(key)
    }

    private fun selectRateLimiter(algorithm: RateLimitAlgorithm): RateLimiter {
        return when (algorithm) {
            RateLimitAlgorithm.TOKEN_BUCKET -> tokenBucketRateLimiter
            RateLimitAlgorithm.SLIDING_WINDOW -> slidingWindowRateLimiter
            else -> throw UnsupportedAlgorithmException(
                algorithm = algorithm,
                supportedAlgorithms = RateLimitAlgorithm.implementedAlgorithms()
            )
        }
    }

    private fun maskKey(key: String): String =
        if (key.length <= 6) "***" else key.take(3) + "***" + key.takeLast(2)

    private fun recordMetrics(
        timer: Timer.Sample,
        algorithm: RateLimitAlgorithm,
        result: RateLimitResult
    ) {
        meterRegistry?.let { registry ->
            timer.stop(
                Timer.builder("$metricsPrefix.check")
                    .tag("algorithm", algorithm.name)
                    .tag("allowed", result.allowed.toString())
                    .register(registry)
            )

            registry.counter(
                "$metricsPrefix.requests",
                "algorithm", algorithm.name,
                "allowed", result.allowed.toString()
            ).increment()
        }
    }
}
