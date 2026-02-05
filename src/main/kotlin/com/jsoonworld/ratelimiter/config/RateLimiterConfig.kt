package com.jsoonworld.ratelimiter.config

import com.jsoonworld.ratelimiter.algorithm.SlidingWindowRateLimiter
import com.jsoonworld.ratelimiter.algorithm.TokenBucketRateLimiter
import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Configuration class for rate limiter beans.
 *
 * Creates both TokenBucketRateLimiter and SlidingWindowRateLimiter beans
 * so they can be selected at runtime via RateLimitService (Strategy Pattern).
 */
@Configuration
@EnableConfigurationProperties(RateLimiterProperties::class)
class RateLimiterConfig(
    private val properties: RateLimiterProperties
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun validateConfiguration() {
        val defaultAlgorithm = properties.algorithm
        val implementedAlgorithms = RateLimitAlgorithm.implementedAlgorithms()

        if (defaultAlgorithm !in implementedAlgorithms) {
            throw IllegalStateException(
                "Default algorithm '${defaultAlgorithm.name}' is not implemented. " +
                    "Supported algorithms: ${implementedAlgorithms.map { it.name }}"
            )
        }

        logger.info("Rate limiter configuration validated. Default algorithm: ${defaultAlgorithm.name}")
    }

    @Bean
    fun tokenBucketRateLimiter(
        redisTemplate: ReactiveStringRedisTemplate,
        properties: RateLimiterProperties
    ): TokenBucketRateLimiter {
        logger.info("Creating TokenBucketRateLimiter with capacity: ${properties.capacity}, refillRate: ${properties.refillRate}")
        return TokenBucketRateLimiter(redisTemplate, properties)
    }

    @Bean
    fun slidingWindowRateLimiter(
        redisTemplate: ReactiveStringRedisTemplate,
        properties: RateLimiterProperties
    ): SlidingWindowRateLimiter {
        logger.info("Creating SlidingWindowRateLimiter with capacity: ${properties.capacity}, windowSize: ${properties.windowSize}")
        return SlidingWindowRateLimiter(redisTemplate, properties)
    }
}
