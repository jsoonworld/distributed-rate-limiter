package com.jsoonworld.ratelimiter.core.algorithm

import com.jsoonworld.ratelimiter.core.TokenBucketConfig
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

class TokenBucketRateLimiterTest {

    private val redisClient = mockk<RedisClient>()
    private val config = TokenBucketConfig(capacity = 10, refillRate = 1)
    private val rateLimiter = TokenBucketRateLimiter(redisClient, config)

    @Test
    fun `should allow request when tokens available`() = runTest {
        coEvery {
            redisClient.executeScript<List<Any>>(any(), any(), any(), any())
        } returns listOf(listOf(1L, 9L, 1L, 0L))

        val result = rateLimiter.tryAcquire("test-key")

        assertTrue(result.allowed)
        assertEquals(9L, result.remaining)
    }

    @Test
    fun `should deny request when no tokens available`() = runTest {
        coEvery {
            redisClient.executeScript<List<Any>>(any(), any(), any(), any())
        } returns listOf(listOf(0L, 0L, 10L, 5L))

        val result = rateLimiter.tryAcquire("test-key")

        assertFalse(result.allowed)
        assertEquals(0L, result.remaining)
        assertEquals(5L, result.retryAfterSeconds)
    }

    @Test
    fun `should fail open when Redis error occurs`() = runTest {
        coEvery {
            redisClient.executeScript<List<Any>>(any(), any(), any(), any())
        } throws RuntimeException("Redis connection failed")

        val result = rateLimiter.tryAcquire("test-key")

        assertTrue(result.allowed)
        assertEquals(config.capacity, result.remaining)
    }

    @Test
    fun `should reject negative permits`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { rateLimiter.tryAcquire("test-key", permits = -1) }
        }
    }

    @Test
    fun `should reject permits exceeding capacity`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { rateLimiter.tryAcquire("test-key", permits = config.capacity + 1) }
        }
    }

    @Test
    fun `should reset key successfully`() = runTest {
        coEvery { redisClient.delete(any()) } returns true

        rateLimiter.reset("test-key")

        coVerify { redisClient.delete("rate_limiter:token_bucket:test-key") }
    }

    @Test
    fun `should get remaining limit`() = runTest {
        coEvery {
            redisClient.executeScript<Long>(any(), any(), any(), any())
        } returns 5L

        val remaining = rateLimiter.getRemainingLimit("test-key")

        assertEquals(5L, remaining)
    }

    @Test
    fun `should return capacity on getRemainingLimit error`() = runTest {
        coEvery {
            redisClient.executeScript<Long>(any(), any(), any(), any())
        } throws RuntimeException("Redis error")

        val remaining = rateLimiter.getRemainingLimit("test-key")

        assertEquals(config.capacity, remaining)
    }
}
