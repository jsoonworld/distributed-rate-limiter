package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.config.RateLimiterProperties
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

class TokenBucketValidationTest {

    private lateinit var redisTemplate: ReactiveStringRedisTemplate
    private lateinit var properties: RateLimiterProperties
    private lateinit var rateLimiter: TokenBucketRateLimiter

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk()
        properties = RateLimiterProperties(capacity = 100, refillRate = 10)
        rateLimiter = TokenBucketRateLimiter(redisTemplate, properties)
    }

    @Test
    fun `should throw exception when permits is zero`() {
        runBlocking {
            val exception = assertThrows<IllegalArgumentException> {
                rateLimiter.tryAcquire("test-key", 0)
            }
            assertThat(exception.message).contains("permits must be positive")
            assertThat(exception.message).contains("0")
        }
    }

    @Test
    fun `should throw exception when permits is negative`() {
        runBlocking {
            val exception = assertThrows<IllegalArgumentException> {
                rateLimiter.tryAcquire("test-key", -1)
            }
            assertThat(exception.message).contains("permits must be positive")
            assertThat(exception.message).contains("-1")
        }
    }

    @Test
    fun `should throw exception when permits exceeds capacity`() {
        runBlocking {
            val exception = assertThrows<IllegalArgumentException> {
                rateLimiter.tryAcquire("test-key", 101)
            }
            assertThat(exception.message).contains("permits cannot exceed capacity")
            assertThat(exception.message).contains("101")
        }
    }
}
