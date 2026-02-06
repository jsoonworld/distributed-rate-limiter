package com.jsoonworld.ratelimiter.spring

import com.jsoonworld.ratelimiter.core.RateLimitAlgorithm
import com.jsoonworld.ratelimiter.core.SlidingWindowConfig
import com.jsoonworld.ratelimiter.core.TokenBucketConfig
import com.jsoonworld.ratelimiter.core.algorithm.SlidingWindowRateLimiter
import com.jsoonworld.ratelimiter.core.algorithm.TokenBucketRateLimiter
import com.jsoonworld.ratelimiter.core.redis.RedisClient
import com.jsoonworld.ratelimiter.spring.redis.SpringRedisClient
import com.jsoonworld.ratelimiter.spring.service.RateLimitService
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Rate Limiter Auto-configuration.
 * Automatically registers Rate Limiter beans in Spring Boot applications.
 */
@AutoConfiguration(after = [RedisReactiveAutoConfiguration::class])
@ConditionalOnClass(ReactiveStringRedisTemplate::class)
@ConditionalOnProperty(
    prefix = "rate-limiter",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(RateLimiterProperties::class)
class RateLimiterAutoConfiguration(
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

        logger.info("Rate limiter auto-configuration initialized. Default algorithm: ${defaultAlgorithm.name}")
    }

    /**
     * RedisClient bean.
     * Uses user-defined bean if available.
     */
    @Bean
    @ConditionalOnMissingBean(RedisClient::class)
    fun redisClient(
        redisTemplate: ReactiveStringRedisTemplate
    ): RedisClient {
        logger.debug("Creating SpringRedisClient")
        return SpringRedisClient(redisTemplate)
    }

    /**
     * TokenBucketRateLimiter bean.
     */
    @Bean
    @ConditionalOnMissingBean(TokenBucketRateLimiter::class)
    fun tokenBucketRateLimiter(
        redisClient: RedisClient
    ): TokenBucketRateLimiter {
        logger.info(
            "Creating TokenBucketRateLimiter with capacity: ${properties.tokenBucket.capacity}, " +
                "refillRate: ${properties.tokenBucket.refillRate}"
        )
        return TokenBucketRateLimiter(
            redisClient = redisClient,
            config = TokenBucketConfig(
                capacity = properties.tokenBucket.capacity,
                refillRate = properties.tokenBucket.refillRate,
                keyPrefix = properties.tokenBucket.keyPrefix
            )
        )
    }

    /**
     * SlidingWindowRateLimiter bean.
     */
    @Bean
    @ConditionalOnMissingBean(SlidingWindowRateLimiter::class)
    fun slidingWindowRateLimiter(
        redisClient: RedisClient
    ): SlidingWindowRateLimiter {
        logger.info(
            "Creating SlidingWindowRateLimiter with windowSize: ${properties.slidingWindow.windowSize}, " +
                "maxRequests: ${properties.slidingWindow.maxRequests}"
        )
        return SlidingWindowRateLimiter(
            redisClient = redisClient,
            config = SlidingWindowConfig(
                windowSize = properties.slidingWindow.windowSize,
                maxRequests = properties.slidingWindow.maxRequests,
                keyPrefix = properties.slidingWindow.keyPrefix
            )
        )
    }

    /**
     * RateLimitService bean.
     */
    @Bean
    @ConditionalOnMissingBean(RateLimitService::class)
    fun rateLimitService(
        tokenBucketRateLimiter: TokenBucketRateLimiter,
        slidingWindowRateLimiter: SlidingWindowRateLimiter,
        meterRegistry: MeterRegistry?
    ): RateLimitService {
        logger.debug("Creating RateLimitService with default algorithm: ${properties.algorithm}")
        return RateLimitService(
            tokenBucketRateLimiter = tokenBucketRateLimiter,
            slidingWindowRateLimiter = slidingWindowRateLimiter,
            defaultAlgorithm = properties.algorithm,
            meterRegistry = meterRegistry,
            metricsEnabled = properties.metrics.enabled,
            metricsPrefix = properties.metrics.prefix
        )
    }
}
