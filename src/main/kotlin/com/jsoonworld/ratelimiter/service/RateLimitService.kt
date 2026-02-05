package com.jsoonworld.ratelimiter.service

import com.jsoonworld.ratelimiter.algorithm.RateLimiter
import com.jsoonworld.ratelimiter.algorithm.SlidingWindowRateLimiter
import com.jsoonworld.ratelimiter.algorithm.TokenBucketRateLimiter
import com.jsoonworld.ratelimiter.config.RateLimiterProperties
import com.jsoonworld.ratelimiter.exception.UnsupportedAlgorithmException
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
    private val meterRegistry: MeterRegistry,
    private val properties: RateLimiterProperties
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    val defaultAlgorithm: RateLimitAlgorithm
        get() = properties.algorithm

    suspend fun checkRateLimit(
        key: String,
        algorithm: RateLimitAlgorithm? = null,
        permits: Long = 1
    ): RateLimitResult {
        val effectiveAlgorithm = algorithm ?: defaultAlgorithm
        logger.debug("Checking rate limit for key: $key, algorithm: $effectiveAlgorithm, permits: $permits")
        val timer = Timer.start(meterRegistry)

        val rateLimiter: RateLimiter = selectAlgorithm(effectiveAlgorithm)
        val result = rateLimiter.tryAcquire(key, permits)

        recordMetrics(timer, effectiveAlgorithm, result)

        return result
    }

    suspend fun getRemainingLimit(
        key: String,
        algorithm: RateLimitAlgorithm? = null
    ): Long {
        val effectiveAlgorithm = algorithm ?: defaultAlgorithm
        logger.debug("Getting remaining limit for key: $key, algorithm: $effectiveAlgorithm")
        val rateLimiter = selectAlgorithm(effectiveAlgorithm)
        return rateLimiter.getRemainingLimit(key)
    }

    suspend fun resetLimit(
        key: String,
        algorithm: RateLimitAlgorithm? = null
    ) {
        val effectiveAlgorithm = algorithm ?: defaultAlgorithm
        logger.info("Resetting rate limit for key: $key, algorithm: $effectiveAlgorithm")
        val rateLimiter = selectAlgorithm(effectiveAlgorithm)
        rateLimiter.reset(key)
    }

    private fun selectAlgorithm(algorithm: RateLimitAlgorithm): RateLimiter {
        return when (algorithm) {
            RateLimitAlgorithm.TOKEN_BUCKET -> tokenBucketRateLimiter
            RateLimitAlgorithm.SLIDING_WINDOW_LOG -> slidingWindowRateLimiter
            RateLimitAlgorithm.SLIDING_WINDOW_COUNTER,
            RateLimitAlgorithm.FIXED_WINDOW,
            RateLimitAlgorithm.LEAKY_BUCKET -> {
                throw UnsupportedAlgorithmException(
                    algorithm = algorithm,
                    supportedAlgorithms = RateLimitAlgorithm.implementedAlgorithms()
                )
            }
        }
    }

    private fun recordMetrics(
        timer: Timer.Sample,
        algorithm: RateLimitAlgorithm,
        result: RateLimitResult
    ) {
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
    }
}
