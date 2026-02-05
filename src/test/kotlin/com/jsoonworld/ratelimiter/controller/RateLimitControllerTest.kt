package com.jsoonworld.ratelimiter.controller

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class RateLimitControllerTest {

    companion object {
        @Container
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            // Enable proxy header trust for testing proxy header extraction
            registry.add("rate-limiter.default.trust-proxy") { true }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `should return 200 when rate limit not exceeded`() {
        val key = "test-user-${UUID.randomUUID()}"

        webTestClient.get()
            .uri("/api/v1/rate-limit/check?key=$key")
            .exchange()
            .expectStatus().isOk
            .expectHeader().exists("X-RateLimit-Remaining")
            .expectHeader().exists("X-RateLimit-Reset")
            .expectBody()
            .jsonPath("$.allowed").isEqualTo(true)
            .jsonPath("$.key").isEqualTo(key)
            .jsonPath("$.message").isEqualTo("Request allowed")
    }

    @Test
    fun `should return 429 when rate limit exceeded`() {
        val key = "exhausted-user-${UUID.randomUUID()}"

        // Use SLIDING_WINDOW_LOG for predictable exhaustion (no refill during test)
        repeat(100) {
            webTestClient.get()
                .uri("/api/v1/rate-limit/check?algorithm=SLIDING_WINDOW_LOG&key=$key")
                .exchange()
        }

        // Should be rate limited
        webTestClient.get()
            .uri("/api/v1/rate-limit/check?algorithm=SLIDING_WINDOW_LOG&key=$key")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().exists("Retry-After")
            .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
            .expectBody()
            .jsonPath("$.allowed").isEqualTo(false)
            .jsonPath("$.message").isEqualTo("Rate limit exceeded")
    }

    @Test
    fun `should return remaining limit`() {
        val key = "remaining-test-${UUID.randomUUID()}"

        // Make some requests
        repeat(50) {
            webTestClient.get()
                .uri("/api/v1/rate-limit/check?key=$key")
                .exchange()
        }

        webTestClient.get()
            .uri("/api/v1/rate-limit/remaining?key=$key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.key").isEqualTo(key)
            .jsonPath("$.remaining").isNumber()
            .jsonPath("$.algorithm").isEqualTo("TOKEN_BUCKET")
    }

    @Test
    fun `should reset rate limit`() {
        val key = "reset-test-${UUID.randomUUID()}"

        // Exhaust rate limit
        repeat(100) {
            webTestClient.get()
                .uri("/api/v1/rate-limit/check?key=$key")
                .exchange()
        }

        // Reset
        webTestClient.delete()
            .uri("/api/v1/rate-limit/reset?key=$key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("reset")
            .jsonPath("$.key").isEqualTo(key)

        // Should be allowed again
        webTestClient.get()
            .uri("/api/v1/rate-limit/check?key=$key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allowed").isEqualTo(true)
    }

    @Test
    fun `should use sliding window algorithm`() {
        val key = "sw-test-${UUID.randomUUID()}"

        webTestClient.get()
            .uri("/api/v1/rate-limit/check?algorithm=SLIDING_WINDOW_LOG&key=$key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allowed").isEqualTo(true)
    }

    @Test
    fun `should use default algorithm when not specified`() {
        val key = "default-algo-${UUID.randomUUID()}"

        webTestClient.get()
            .uri("/api/v1/rate-limit/check?key=$key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allowed").isEqualTo(true)
    }

    @Test
    fun `should extract client key from X-Forwarded-For header`() {
        webTestClient.get()
            .uri("/api/v1/rate-limit/check")
            .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.key").isEqualTo("192.168.1.100")
    }

    @Test
    fun `should extract client key from X-Real-IP header`() {
        webTestClient.get()
            .uri("/api/v1/rate-limit/check")
            .header("X-Real-IP", "172.16.0.50")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.key").isEqualTo("172.16.0.50")
    }

    @Test
    fun `should prefer X-Forwarded-For over X-Real-IP`() {
        webTestClient.get()
            .uri("/api/v1/rate-limit/check")
            .header("X-Forwarded-For", "192.168.1.100")
            .header("X-Real-IP", "172.16.0.50")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.key").isEqualTo("192.168.1.100")
    }

    @Test
    fun `should include all required headers in response`() {
        val key = "header-test-${UUID.randomUUID()}"

        webTestClient.get()
            .uri("/api/v1/rate-limit/check?key=$key")
            .exchange()
            .expectStatus().isOk
            .expectHeader().exists("X-RateLimit-Remaining")
            .expectHeader().exists("X-RateLimit-Reset")
    }

    @Test
    fun `should return correct remaining count after multiple requests`() {
        val key = "count-test-${UUID.randomUUID()}"

        // Make 10 requests
        repeat(10) {
            webTestClient.get()
                .uri("/api/v1/rate-limit/check?key=$key")
                .exchange()
        }

        // Check remaining (should be around 90)
        webTestClient.get()
            .uri("/api/v1/rate-limit/remaining?key=$key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.remaining").value<Int> { remaining ->
                assertTrue(remaining in 85..95) { "Expected remaining between 85-95, got $remaining" }
            }
    }

    @Test
    fun `should return 400 for unimplemented algorithms`() {
        val key = "unsupported-algo-${UUID.randomUUID()}"

        webTestClient.get()
            .uri("/api/v1/rate-limit/check?algorithm=FIXED_WINDOW&key=$key")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("UNSUPPORTED_ALGORITHM")
            .jsonPath("$.requestedAlgorithm").isEqualTo("FIXED_WINDOW")
            .jsonPath("$.supportedAlgorithms").isArray
    }

    @Test
    fun `should return 400 for SLIDING_WINDOW_COUNTER algorithm`() {
        val key = "unsupported-swc-${UUID.randomUUID()}"

        webTestClient.get()
            .uri("/api/v1/rate-limit/check?algorithm=SLIDING_WINDOW_COUNTER&key=$key")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("UNSUPPORTED_ALGORITHM")
            .jsonPath("$.message").value<String> { message ->
                assertTrue(message.contains("SLIDING_WINDOW_COUNTER"))
                assertTrue(message.contains("TOKEN_BUCKET"))
                assertTrue(message.contains("SLIDING_WINDOW_LOG"))
            }
    }

    @Test
    fun `should return 400 for LEAKY_BUCKET algorithm`() {
        val key = "unsupported-lb-${UUID.randomUUID()}"

        webTestClient.get()
            .uri("/api/v1/rate-limit/check?algorithm=LEAKY_BUCKET&key=$key")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("UNSUPPORTED_ALGORITHM")
            .jsonPath("$.requestedAlgorithm").isEqualTo("LEAKY_BUCKET")
    }

    @Test
    fun `should return 400 for invalid algorithm parameter`() {
        val key = "invalid-algo-${UUID.randomUUID()}"

        webTestClient.get()
            .uri("/api/v1/rate-limit/check?algorithm=INVALID_ALGO&key=$key")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_PARAMETER")
            .jsonPath("$.message").isNotEmpty
    }

    @Test
    fun `should use configured default algorithm when none specified`() {
        val key = "default-config-${UUID.randomUUID()}"

        webTestClient.get()
            .uri("/api/v1/rate-limit/check?key=$key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.algorithm").isEqualTo("TOKEN_BUCKET")
    }
}
