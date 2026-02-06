package com.jsoonworld.ratelimiter.app

import com.jsoonworld.ratelimiter.app.controller.RateLimitResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateLimitIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

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
            // Use small capacity for testing rate limit exceeded scenario
            registry.add("rate-limiter.token-bucket.capacity") { 10 }
            registry.add("rate-limiter.token-bucket.refill-rate") { 1 }
        }
    }

    @Test
    fun `should allow request when within rate limit`() {
        val url = "http://localhost:$port/api/v1/rate-limit/check?key=test-user-${System.currentTimeMillis()}"

        val response = restTemplate.getForEntity(url, RateLimitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.allowed)
    }

    @Test
    fun `should return remaining limit`() {
        val key = "remaining-test-${System.currentTimeMillis()}"
        val url = "http://localhost:$port/api/v1/rate-limit/remaining?key=$key"

        val response = restTemplate.getForEntity(url, Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue((response.body!!["remaining"] as Number).toLong() > 0)
    }

    @Test
    fun `health endpoint should be accessible`() {
        val url = "http://localhost:$port/actuator/health"

        val response = restTemplate.getForEntity(url, Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `should return 429 when rate limit exceeded`() {
        val key = "rate-limit-test-${System.currentTimeMillis()}"
        val url = "http://localhost:$port/api/v1/rate-limit/check?key=$key"

        // Exhaust the limit (test configured with capacity=10)
        repeat(10) {
            restTemplate.getForEntity(url, RateLimitResponse::class.java)
        }

        // This request should be denied
        val response = restTemplate.getForEntity(url, RateLimitResponse::class.java)

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.statusCode)
        assertNotNull(response.body)
        assertEquals(false, response.body!!.allowed)
    }
}
