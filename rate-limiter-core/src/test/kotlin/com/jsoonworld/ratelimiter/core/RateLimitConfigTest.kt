package com.jsoonworld.ratelimiter.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class RateLimitConfigTest {

    @Test
    fun `TokenBucketConfig should use default values`() {
        val config = TokenBucketConfig()

        assertEquals(100L, config.capacity)
        assertEquals(10L, config.refillRate)
        assertEquals("rate_limiter:token_bucket:", config.keyPrefix)
    }

    @Test
    fun `TokenBucketConfig should accept custom values`() {
        val config = TokenBucketConfig(
            capacity = 50L,
            refillRate = 5L,
            keyPrefix = "custom:"
        )

        assertEquals(50L, config.capacity)
        assertEquals(5L, config.refillRate)
        assertEquals("custom:", config.keyPrefix)
    }

    @Test
    fun `TokenBucketConfig should reject non-positive capacity`() {
        assertThrows<IllegalArgumentException> {
            TokenBucketConfig(capacity = 0)
        }
        assertThrows<IllegalArgumentException> {
            TokenBucketConfig(capacity = -1)
        }
    }

    @Test
    fun `TokenBucketConfig should reject non-positive refillRate`() {
        assertThrows<IllegalArgumentException> {
            TokenBucketConfig(refillRate = 0)
        }
        assertThrows<IllegalArgumentException> {
            TokenBucketConfig(refillRate = -1)
        }
    }

    @Test
    fun `SlidingWindowConfig should use default values`() {
        val config = SlidingWindowConfig()

        assertEquals(60L, config.windowSize)
        assertEquals(100L, config.maxRequests)
        assertEquals("rate_limiter:sliding_window:", config.keyPrefix)
    }

    @Test
    fun `SlidingWindowConfig should accept custom values`() {
        val config = SlidingWindowConfig(
            windowSize = 120L,
            maxRequests = 50L,
            keyPrefix = "custom:"
        )

        assertEquals(120L, config.windowSize)
        assertEquals(50L, config.maxRequests)
        assertEquals("custom:", config.keyPrefix)
    }

    @Test
    fun `SlidingWindowConfig should reject non-positive windowSize`() {
        assertThrows<IllegalArgumentException> {
            SlidingWindowConfig(windowSize = 0)
        }
    }

    @Test
    fun `SlidingWindowConfig should reject non-positive maxRequests`() {
        assertThrows<IllegalArgumentException> {
            SlidingWindowConfig(maxRequests = 0)
        }
    }

    @Test
    fun `RateLimitConfig should use default algorithm`() {
        val config = RateLimitConfig()

        assertEquals(RateLimitAlgorithm.TOKEN_BUCKET, config.algorithm)
    }
}
