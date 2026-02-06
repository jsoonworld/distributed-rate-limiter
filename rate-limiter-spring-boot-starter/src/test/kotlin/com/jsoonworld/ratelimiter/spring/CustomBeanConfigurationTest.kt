package com.jsoonworld.ratelimiter.spring

import com.jsoonworld.ratelimiter.core.SlidingWindowConfig
import com.jsoonworld.ratelimiter.core.TokenBucketConfig
import com.jsoonworld.ratelimiter.core.algorithm.SlidingWindowRateLimiter
import com.jsoonworld.ratelimiter.core.algorithm.TokenBucketRateLimiter
import com.jsoonworld.ratelimiter.core.redis.RedisClient
import com.jsoonworld.ratelimiter.spring.redis.SpringRedisClient
import com.jsoonworld.ratelimiter.spring.service.RateLimitService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for @ConditionalOnMissingBean behavior with custom rate limiter beans.
 * Verifies that user-defined beans are not overridden by auto-configuration.
 */
@SpringBootTest(classes = [CustomBeanConfigurationTest.TestConfig::class])
@Testcontainers
class CustomBeanConfigurationTest {

    @Configuration
    @EnableAutoConfiguration
    class TestConfig {
        companion object {
            const val CUSTOM_TOKEN_BUCKET_CAPACITY = 999L
            const val CUSTOM_SLIDING_WINDOW_MAX_REQUESTS = 888L
        }

        @Bean
        fun tokenBucketRateLimiter(redisClient: RedisClient): TokenBucketRateLimiter {
            return TokenBucketRateLimiter(
                redisClient = redisClient,
                config = TokenBucketConfig(
                    capacity = CUSTOM_TOKEN_BUCKET_CAPACITY,
                    refillRate = 10L,
                    keyPrefix = "custom_token_bucket"
                )
            )
        }

        @Bean
        fun slidingWindowRateLimiter(redisClient: RedisClient): SlidingWindowRateLimiter {
            return SlidingWindowRateLimiter(
                redisClient = redisClient,
                config = SlidingWindowConfig(
                    windowSize = 30000,
                    maxRequests = CUSTOM_SLIDING_WINDOW_MAX_REQUESTS,
                    keyPrefix = "custom_sliding_window"
                )
            )
        }
    }

    companion object {
        @Container
        @JvmStatic
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    private lateinit var tokenBucketRateLimiter: TokenBucketRateLimiter

    @Autowired
    private lateinit var slidingWindowRateLimiter: SlidingWindowRateLimiter

    @Autowired
    private lateinit var rateLimitService: RateLimitService

    @Test
    fun `should use custom TokenBucketRateLimiter instead of auto-configured one`() = runBlocking {
        assertNotNull(tokenBucketRateLimiter)
        // Verify custom configuration by checking remaining limit on a fresh key
        val remaining = tokenBucketRateLimiter.getRemainingLimit("custom-test-key-token-${System.nanoTime()}")
        assertEquals(TestConfig.CUSTOM_TOKEN_BUCKET_CAPACITY, remaining)
    }

    @Test
    fun `should use custom SlidingWindowRateLimiter instead of auto-configured one`() = runBlocking {
        assertNotNull(slidingWindowRateLimiter)
        // Verify custom configuration by checking remaining limit on a fresh key
        val remaining = slidingWindowRateLimiter.getRemainingLimit("custom-test-key-sliding-${System.nanoTime()}")
        assertEquals(TestConfig.CUSTOM_SLIDING_WINDOW_MAX_REQUESTS, remaining)
    }

    @Test
    fun `should create RateLimitService with custom rate limiters`() {
        assertNotNull(rateLimitService)
    }

    @Test
    fun `should check rate limit successfully with custom configuration`() = runBlocking {
        val result = rateLimitService.checkRateLimit("custom-test-key-service-${System.nanoTime()}")
        assertNotNull(result)
        assertTrue(result.allowed)
    }
}

/**
 * Tests for @ConditionalOnMissingBean behavior with custom RedisClient bean.
 */
@SpringBootTest(classes = [CustomRedisClientConfigurationTest.TestConfig::class])
@Testcontainers
class CustomRedisClientConfigurationTest {

    @Configuration
    @EnableAutoConfiguration
    class TestConfig {
        @Bean
        fun redisClient(redisTemplate: ReactiveStringRedisTemplate): RedisClient {
            return SpringRedisClient(redisTemplate)
        }
    }

    companion object {
        @Container
        @JvmStatic
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    private lateinit var redisClient: RedisClient

    @Autowired
    private lateinit var rateLimitService: RateLimitService

    @Test
    fun `should use custom RedisClient instead of auto-configured one`() {
        assertNotNull(redisClient)
        assertTrue(redisClient is SpringRedisClient)
    }

    @Test
    fun `should create rate limiters with custom RedisClient`() = runBlocking {
        val result = rateLimitService.checkRateLimit("custom-redis-test-key-${System.nanoTime()}")
        assertNotNull(result)
        assertTrue(result.allowed)
    }
}
