package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.config.RateLimiterProperties
import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
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

        // Test-specific configuration for CI stability
        // Token Bucket: refills quickly (10 tokens/sec) for fast refill tests
        // Sliding Window: longer window (3 sec) to ensure requests stay in window during test
        private const val TEST_CAPACITY = 5L
        private const val TEST_REFILL_RATE = 10L  // Fast refill for Token Bucket
        private const val TEST_WINDOW_SIZE_SECONDS = 3L  // Longer window for Sliding Window
        private const val TEST_MAX_REQUESTS = 5L
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
            algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
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
        // Exhaust Sliding Window (TEST_MAX_REQUESTS = 5 requests)
        repeat(TEST_MAX_REQUESTS.toInt()) {
            fastSlidingWindow.tryAcquire(swKey)
        }

        // For Token Bucket with small capacity (TEST_CAPACITY = 5), exhaust quickly
        repeat(TEST_CAPACITY.toInt()) {
            fastTokenBucket.tryAcquire(tbKey)
        }

        // Verify both are exhausted
        val tbExhausted = fastTokenBucket.tryAcquire(tbKey)
        assertThat(tbExhausted.allowed).isFalse()
        val swExhausted = fastSlidingWindow.tryAcquire(swKey)
        assertThat(swExhausted.allowed).isFalse()

        // Step 2: Wait for token refill (1 second should give us 10 tokens with refill_rate=10)
        delay(1200)

        // Step 3: Retry requests
        // Token Bucket: 10 tokens should be refilled (refill_rate=10/sec)
        val tbResult = fastTokenBucket.tryAcquire(tbKey)
        assertThat(tbResult.allowed)
            .describedAs("Token Bucket should allow after refill period (waited 1.2s, refill_rate=10/sec)")
            .isTrue()

        // Sliding Window: Window is 3 seconds, we only waited 1.2s, so requests are still in window
        val swResult = fastSlidingWindow.tryAcquire(swKey)
        assertThat(swResult.allowed)
            .describedAs("Sliding Window should deny (window=3s, waited=1.2s, requests still in window)")
            .isFalse()

        Unit
    }

    @Test
    fun `both allow after full reset period`(): Unit = runBlocking {
        // Given - unique keys for test isolation
        val tbKey = "tb-reset-test-${UUID.randomUUID()}"
        val swKey = "sw-reset-test-${UUID.randomUUID()}"

        // Step 1: Exhaust all requests using fast instances with short window (3 seconds)
        // Exhaust Sliding Window (TEST_MAX_REQUESTS = 5 requests)
        repeat(TEST_MAX_REQUESTS.toInt()) {
            fastSlidingWindow.tryAcquire(swKey)
        }

        // For Token Bucket with small capacity (TEST_CAPACITY = 5), exhaust quickly
        repeat(TEST_CAPACITY.toInt()) {
            fastTokenBucket.tryAcquire(tbKey)
        }

        // Verify both are exhausted
        val tbExhausted = fastTokenBucket.tryAcquire(tbKey)
        assertThat(tbExhausted.allowed).isFalse()
        val swExhausted = fastSlidingWindow.tryAcquire(swKey)
        assertThat(swExhausted.allowed).isFalse()

        // Step 2: Wait for full window size (3 seconds + buffer for CI)
        // Using TEST_WINDOW_SIZE_SECONDS = 3, so we wait 4 seconds to ensure full reset
        delay(4000)

        // Step 3: Both algorithms should allow requests
        val tbResult = fastTokenBucket.tryAcquire(tbKey)
        val swResult = fastSlidingWindow.tryAcquire(swKey)

        assertThat(tbResult.allowed)
            .describedAs("Token Bucket should allow after full reset period")
            .isTrue()
        assertThat(swResult.allowed)
            .describedAs("Sliding Window should allow after window expires")
            .isTrue()

        Unit
    }
}
