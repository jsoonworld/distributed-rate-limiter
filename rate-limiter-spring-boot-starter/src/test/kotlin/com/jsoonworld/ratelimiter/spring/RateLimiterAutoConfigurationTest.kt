package com.jsoonworld.ratelimiter.spring

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
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for RateLimiterAutoConfiguration with default settings.
 */
@SpringBootTest(classes = [RateLimiterAutoConfigurationTest.TestConfig::class])
@Testcontainers
class RateLimiterAutoConfigurationTest {

    @Configuration
    @EnableAutoConfiguration
    class TestConfig

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
    private lateinit var tokenBucketRateLimiter: TokenBucketRateLimiter

    @Autowired
    private lateinit var slidingWindowRateLimiter: SlidingWindowRateLimiter

    @Autowired
    private lateinit var rateLimitService: RateLimitService

    @Test
    fun `should create RedisClient bean with default settings`() {
        assertNotNull(redisClient)
        assertTrue(redisClient is SpringRedisClient)
    }

    @Test
    fun `should create TokenBucketRateLimiter bean with default settings`() {
        assertNotNull(tokenBucketRateLimiter)
    }

    @Test
    fun `should create SlidingWindowRateLimiter bean with default settings`() {
        assertNotNull(slidingWindowRateLimiter)
    }

    @Test
    fun `should create RateLimitService bean with default settings`() {
        assertNotNull(rateLimitService)
    }

    @Test
    fun `should check rate limit successfully with default configuration`() = runBlocking {
        val result = rateLimitService.checkRateLimit("test-key-default")
        assertNotNull(result)
        assertTrue(result.allowed)
    }
}
