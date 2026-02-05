package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.config.RateLimiterProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@Testcontainers
class AlgorithmComparisonTest {

    companion object {
        @Container
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }

        // Test-specific configuration for faster tests
        private const val TEST_CAPACITY = 10L
        private const val TEST_REFILL_RATE = 10L
        private const val TEST_WINDOW_SIZE_SECONDS = 2L
        private const val TEST_MAX_REQUESTS = 10L
    }

    @Autowired
    lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Autowired
    lateinit var properties: RateLimiterProperties

    private lateinit var tokenBucket: TokenBucketRateLimiter
    private lateinit var slidingWindow: SlidingWindowRateLimiter

    // Test-specific instances with shorter time windows for faster CI execution
    private lateinit var fastTokenBucket: TokenBucketRateLimiter
    private lateinit var fastSlidingWindow: SlidingWindowRateLimiter

    @BeforeEach
    fun setUp() {
        tokenBucket = TokenBucketRateLimiter(redisTemplate, properties)
        slidingWindow = SlidingWindowRateLimiter(redisTemplate, properties)

        // Create test-specific instances with smaller capacity and shorter window for fast tests
        val fastProperties = RateLimiterProperties(
            algorithm = "TOKEN_BUCKET",
            capacity = TEST_CAPACITY,
            refillRate = TEST_REFILL_RATE,
            windowSize = TEST_WINDOW_SIZE_SECONDS
        )
        fastTokenBucket = TokenBucketRateLimiter(redisTemplate, fastProperties)
        fastSlidingWindow = SlidingWindowRateLimiter(redisTemplate, fastProperties)
    }

    @Test
    fun `token bucket allows burst then refills, sliding window blocks`(): Unit = runBlocking {
        // Given - unique keys for test isolation
        val tbKey = "tb-burst-test-${UUID.randomUUID()}"
        val swKey = "sw-burst-test-${UUID.randomUUID()}"

        // Step 1: Exhaust requests from both algorithms using fast instances
        // Exhaust Sliding Window (TEST_MAX_REQUESTS = 10 requests)
        repeat(TEST_MAX_REQUESTS.toInt()) {
            fastSlidingWindow.tryAcquire(swKey)
        }

        // For Token Bucket with small capacity (TEST_CAPACITY = 10), exhaust quickly
        repeat(TEST_CAPACITY.toInt()) {
            fastTokenBucket.tryAcquire(tbKey)
        }

        // Verify both are exhausted
        val tbExhausted = fastTokenBucket.tryAcquire(tbKey)
        assertThat(tbExhausted.allowed).isFalse()
        val swExhausted = fastSlidingWindow.tryAcquire(swKey)
        assertThat(swExhausted.allowed).isFalse()

        // Step 2: Wait 1 second for token refill
        delay(1000)

        // Step 3: Retry requests
        // Token Bucket: 10 tokens should be refilled (refill_rate=10)
        val tbResult = fastTokenBucket.tryAcquire(tbKey)
        assertThat(tbResult.allowed).isTrue()

        // Sliding Window: Still has requests in the 2-second window, should deny
        val swResult = fastSlidingWindow.tryAcquire(swKey)
        assertThat(swResult.allowed).isFalse()

        Unit
    }

    @Test
    fun `both allow after full reset period`(): Unit = runBlocking {
        // Given - unique keys for test isolation
        val tbKey = "tb-reset-test-${UUID.randomUUID()}"
        val swKey = "sw-reset-test-${UUID.randomUUID()}"

        // Step 1: Exhaust all requests using fast instances with short window (2 seconds)
        // Exhaust Sliding Window (TEST_MAX_REQUESTS = 10 requests)
        repeat(TEST_MAX_REQUESTS.toInt()) {
            fastSlidingWindow.tryAcquire(swKey)
        }

        // For Token Bucket with small capacity (TEST_CAPACITY = 10), exhaust quickly
        repeat(TEST_CAPACITY.toInt()) {
            fastTokenBucket.tryAcquire(tbKey)
        }

        // Verify both are exhausted
        val tbExhausted = fastTokenBucket.tryAcquire(tbKey)
        assertThat(tbExhausted.allowed).isFalse()
        val swExhausted = fastSlidingWindow.tryAcquire(swKey)
        assertThat(swExhausted.allowed).isFalse()

        // Step 2: Wait for full window size (2 seconds + buffer)
        // Using TEST_WINDOW_SIZE_SECONDS = 2, so we wait 3 seconds to ensure full reset
        delay(3000)

        // Step 3: Both algorithms should allow requests
        val tbResult = fastTokenBucket.tryAcquire(tbKey)
        val swResult = fastSlidingWindow.tryAcquire(swKey)

        assertThat(tbResult.allowed).isTrue()
        assertThat(swResult.allowed).isTrue()

        Unit
    }
}
