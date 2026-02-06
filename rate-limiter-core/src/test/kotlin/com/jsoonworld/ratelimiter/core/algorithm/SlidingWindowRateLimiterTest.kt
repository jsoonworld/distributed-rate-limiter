package com.jsoonworld.ratelimiter.core.algorithm

import com.jsoonworld.ratelimiter.core.SlidingWindowConfig
import com.jsoonworld.ratelimiter.core.redis.RedisClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlidingWindowRateLimiterTest {

    private val redisClient = mockk<RedisClient>()
    private val config = SlidingWindowConfig(windowSize = 60, maxRequests = 10)
    private val rateLimiter = SlidingWindowRateLimiter(redisClient, config)

    @Test
    fun `should allow request within limit`() = runTest {
        coEvery {
            redisClient.executeScript<List<Any>>(any(), any(), any(), any())
        } returns listOf(listOf(1L, 9L, 60L))

        val result = rateLimiter.tryAcquire("test-key")

        assertTrue(result.allowed)
        assertEquals(9L, result.remaining)
    }

    @Test
    fun `should deny request when limit exceeded`() = runTest {
        coEvery {
            redisClient.executeScript<List<Any>>(any(), any(), any(), any())
        } returns listOf(listOf(0L, 0L, 30L))

        val result = rateLimiter.tryAcquire("test-key")

        assertFalse(result.allowed)
        assertEquals(0L, result.remaining)
        assertEquals(30L, result.retryAfterSeconds)
    }

    @Test
    fun `should fail open when Redis error occurs`() = runTest {
        coEvery {
            redisClient.executeScript<List<Any>>(any(), any(), any(), any())
        } throws RuntimeException("Redis connection failed")

        val result = rateLimiter.tryAcquire("test-key")

        assertTrue(result.allowed)
        assertEquals(config.maxRequests, result.remaining)
    }

    @Test
    fun `should reject negative permits`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { rateLimiter.tryAcquire("test-key", permits = -1) }
        }
    }

    @Test
    fun `should reject permits exceeding maxRequests`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { rateLimiter.tryAcquire("test-key", permits = config.maxRequests + 1) }
        }
    }

    @Test
    fun `should reset key successfully`() = runTest {
        coEvery { redisClient.delete(any()) } returns true

        rateLimiter.reset("test-key")

        coVerify { redisClient.delete("rate_limiter:sliding_window:test-key") }
    }

    @Test
    fun `should get remaining limit`() = runTest {
        coEvery {
            redisClient.executeScript<List<Any>>(any(), any(), any(), any())
        } returns listOf(5L)

        val remaining = rateLimiter.getRemainingLimit("test-key")

        assertEquals(5L, remaining)
    }

    @Test
    fun `should return maxRequests on getRemainingLimit error`() = runTest {
        coEvery {
            redisClient.executeScript<List<Any>>(any(), any(), any(), any())
        } throws RuntimeException("Redis error")

        val remaining = rateLimiter.getRemainingLimit("test-key")

        assertEquals(config.maxRequests, remaining)
    }
}
