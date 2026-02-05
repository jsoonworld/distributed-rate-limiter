package com.jsoonworld.ratelimiter.monitoring

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30000")
@ActiveProfiles("test")
@Testcontainers
class MonitoringTest {

    companion object {
        @Container
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2))

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.data.redis.timeout") { "10000ms" }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @Test
    fun `should expose prometheus metrics endpoint`() {
        // Verify prometheus endpoint is available and returns metrics in prometheus format
        // Prometheus requires authentication in production
        webTestClient.get()
            .uri("/actuator/prometheus")
            .headers { it.setBasicAuth("test", "test") }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                // Prometheus endpoint should contain standard JVM metrics
                assertThat(body).contains("jvm_")
            }
    }

    @Test
    fun `should expose rate_limiter metrics after requests`() {
        val key = "prometheus-test-${UUID.randomUUID()}"

        // Make a rate limit request to generate rate_limiter metrics
        webTestClient.get()
            .uri("/api/v1/rate-limit/check?key=$key")
            .exchange()
            .expectStatus().isOk

        // Verify rate_limiter metrics are present (requires authentication)
        webTestClient.get()
            .uri("/actuator/prometheus")
            .headers { it.setBasicAuth("test", "test") }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                assertThat(body).contains("rate_limiter")
            }
    }

    @Test
    fun `should report healthy when Redis is available`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `should include redis in health check`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.components.redis.status").isEqualTo("UP")
    }

    @Test
    fun `should include rateLimiter in health check`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.components.rateLimiter.status").isEqualTo("UP")
            .jsonPath("$.components.rateLimiter.details.ping").isEqualTo("PONG")
    }

    @Test
    fun `should increment request counter on rate limit check`() {
        runBlocking {
            val key = "test-metrics-${UUID.randomUUID()}"

            // Get initial counter value
            val initialCount = meterRegistry.counter(
                "rate_limiter.requests",
                "algorithm", "TOKEN_BUCKET",
                "allowed", "true"
            ).count()

            // Call rate limit check endpoint
            webTestClient.get()
                .uri("/api/v1/rate-limit/check?key=$key")
                .exchange()
                .expectStatus().isOk

            // Verify counter incremented
            val finalCount = meterRegistry.counter(
                "rate_limiter.requests",
                "algorithm", "TOKEN_BUCKET",
                "allowed", "true"
            ).count()

            assertThat(finalCount).isGreaterThan(initialCount)
        }
    }

    @Test
    fun `should require authentication for prometheus endpoint`() {
        // Without authentication, should return 401
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `should require authentication for metrics endpoint`() {
        // Without authentication, should return 401
        webTestClient.get()
            .uri("/actuator/metrics")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `should allow health endpoint without authentication`() {
        // Health endpoint should be publicly accessible
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }
}
