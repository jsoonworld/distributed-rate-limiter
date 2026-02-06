package com.jsoonworld.ratelimiter.spring.redis

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringRedisClientTest {

    companion object {
        @Container
        @JvmStatic
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
    }

    private lateinit var springRedisClient: SpringRedisClient
    private lateinit var redisTemplate: ReactiveStringRedisTemplate
    private lateinit var connectionFactory: LettuceConnectionFactory

    @BeforeAll
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(
            redis.host,
            redis.getMappedPort(6379)
        ).apply { afterPropertiesSet() }

        redisTemplate = ReactiveStringRedisTemplate(connectionFactory)
        springRedisClient = SpringRedisClient(redisTemplate)
    }

    @Test
    fun `should execute script returning Long correctly`() = runBlocking {
        // Given - A Lua script that returns a single Long value
        val script = """
            return 42
        """.trimIndent()

        // When
        val result = springRedisClient.executeScript(
            script = script,
            keys = emptyList(),
            args = emptyList(),
            resultType = Long::class.java
        )

        // Then
        assertNotNull(result)
        assertEquals(42L, result)
    }

    @Test
    fun `should execute script returning List correctly`() = runBlocking {
        // Given - A Lua script that returns a list of values (matching token bucket format)
        val script = """
            return {1, 2, 3}
        """.trimIndent()

        // When
        @Suppress("UNCHECKED_CAST")
        val result = springRedisClient.executeScript(
            script = script,
            keys = emptyList(),
            args = emptyList(),
            resultType = List::class.java as Class<List<Any>>
        )

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // Spring Data Redis collectList() collects the single List emission into [[1, 2, 3]]
        // This is the expected behavior - the inner list contains the actual values
        assertEquals(1, result.size)
        @Suppress("UNCHECKED_CAST")
        val innerList = result[0] as List<Long>
        assertEquals(3, innerList.size)
        assertEquals(listOf(1L, 2L, 3L), innerList)
    }

    @Test
    fun `should execute script with keys and args returning Long`() = runBlocking {
        // Given - A Lua script that sets a value and returns it
        val key = "test:script:long:${System.currentTimeMillis()}"
        val script = """
            redis.call('SET', KEYS[1], ARGV[1])
            return tonumber(redis.call('GET', KEYS[1]))
        """.trimIndent()

        // When
        val result = springRedisClient.executeScript(
            script = script,
            keys = listOf(key),
            args = listOf("100"),
            resultType = Long::class.java
        )

        // Then
        assertNotNull(result)
        assertEquals(100L, result)
    }

    @Test
    fun `should execute script with keys and args returning List`() = runBlocking {
        // Given - A Lua script that returns multiple values as a list
        val key = "test:script:list:${System.currentTimeMillis()}"
        val script = """
            redis.call('SET', KEYS[1], ARGV[1])
            local value = redis.call('GET', KEYS[1])
            return {value, ARGV[2]}
        """.trimIndent()

        // When
        @Suppress("UNCHECKED_CAST")
        val result = springRedisClient.executeScript(
            script = script,
            keys = listOf(key),
            args = listOf("hello", "world"),
            resultType = List::class.java as Class<List<Any>>
        )

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // Spring Data Redis collectList() collects the single List emission into [[...]]
        assertEquals(1, result.size)
        @Suppress("UNCHECKED_CAST")
        val innerList = result[0] as List<String>
        assertEquals(2, innerList.size)
        assertEquals("hello", innerList[0])
        assertEquals("world", innerList[1])
    }

    @Test
    fun `should handle token bucket style script returning remaining tokens`() = runBlocking {
        // Given - A simplified token bucket script that returns remaining tokens
        val key = "rate_limiter:token_bucket:test:${System.currentTimeMillis()}"
        val script = """
            local tokens = tonumber(ARGV[1])
            redis.call('SET', KEYS[1], tokens)
            return tokens
        """.trimIndent()

        // When
        val result = springRedisClient.executeScript(
            script = script,
            keys = listOf(key),
            args = listOf("50"),
            resultType = Long::class.java
        )

        // Then
        assertNotNull(result)
        assertEquals(50L, result)
    }

    @Test
    fun `should delete key correctly`() = runBlocking {
        // Given
        val key = "test:delete:${System.currentTimeMillis()}"
        redisTemplate.opsForValue().set(key, "value").block()

        // When
        val result = springRedisClient.delete(key)

        // Then
        assertTrue(result)
    }

    @Test
    fun `should return false when deleting non-existent key`() = runBlocking {
        // Given
        val key = "test:non-existent:${System.currentTimeMillis()}"

        // When
        val result = springRedisClient.delete(key)

        // Then
        assertEquals(false, result)
    }
}
