package com.jsoonworld.ratelimiter.config

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class RedisConfigTest {

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
    }

    @Autowired
    lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Test
    fun `should connect to Redis`(): Unit = runBlocking {
        val key = "test:connect:${System.currentTimeMillis()}"
        val result = redisTemplate.opsForValue()
            .set(key, "value")
            .awaitSingle()
        assertThat(result).isTrue()
    }

    @Test
    fun `should read value from Redis`(): Unit = runBlocking {
        val key = "test:read:${System.currentTimeMillis()}"
        val value = "test-value"

        redisTemplate.opsForValue().set(key, value).awaitSingle()
        val retrieved = redisTemplate.opsForValue().get(key).awaitSingle()

        assertThat(retrieved).isEqualTo(value)
    }

    @Test
    fun `should delete key from Redis`(): Unit = runBlocking {
        val key = "test:delete:${System.currentTimeMillis()}"

        redisTemplate.opsForValue().set(key, "value").awaitSingle()
        val deleted = redisTemplate.delete(key).awaitSingle()

        assertThat(deleted).isEqualTo(1L)
    }
}
