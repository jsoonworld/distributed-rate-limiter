package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.config.RateLimiterProperties
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

class SlidingWindowValidationTest {

    private lateinit var redisTemplate: ReactiveStringRedisTemplate
    private lateinit var rateLimiter: SlidingWindowRateLimiter

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk()
        val properties = RateLimiterProperties(
            algorithm = "SLIDING_WINDOW",
            capacity = 100,
            refillRate = 10,
            windowSize = 60
        )
        rateLimiter = SlidingWindowRateLimiter(redisTemplate, properties)
    }

    @Test
    fun `should throw exception when permits is zero`() {
        runBlocking {
            // Given
            val key = "test-key"

            // When & Then
            val exception = assertThrows<IllegalArgumentException> {
                rateLimiter.tryAcquire(key, 0)
            }
            assertThat(exception.message).contains("permits must be positive")
            assertThat(exception.message).contains("0")
        }
    }

    @Test
    fun `should throw exception when permits is negative`() {
        runBlocking {
            // Given
            val key = "test-key"

            // When & Then
            val exception = assertThrows<IllegalArgumentException> {
                rateLimiter.tryAcquire(key, -1)
            }
            assertThat(exception.message).contains("permits must be positive")
            assertThat(exception.message).contains("-1")
        }
    }

    @Test
    fun `should throw exception when permits exceeds maxRequests`() {
        runBlocking {
            // Given
            val key = "test-key"

            // When & Then
            val exception = assertThrows<IllegalArgumentException> {
                rateLimiter.tryAcquire(key, 101)
            }
            assertThat(exception.message).contains("permits cannot exceed maxRequests")
            assertThat(exception.message).contains("101")
        }
    }

    @Test
    fun `should throw exception when permits is large negative number`() {
        runBlocking {
            // Given
            val key = "test-key"

            // When & Then
            val exception = assertThrows<IllegalArgumentException> {
                rateLimiter.tryAcquire(key, -100)
            }
            assertThat(exception.message).contains("permits must be positive")
            assertThat(exception.message).contains("-100")
        }
    }

    @Test
    fun `should throw exception when permits greatly exceeds maxRequests`() {
        runBlocking {
            // Given
            val key = "test-key"

            // When & Then
            val exception = assertThrows<IllegalArgumentException> {
                rateLimiter.tryAcquire(key, 1000)
            }
            assertThat(exception.message).contains("permits cannot exceed maxRequests")
            assertThat(exception.message).contains("1000")
        }
    }
}
