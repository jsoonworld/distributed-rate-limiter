package com.jsoonworld.ratelimiter.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RateLimitResultTest {

    @Test
    fun `allowed should create result with allowed true`() {
        val result = RateLimitResult.allowed(remaining = 99, resetTime = 10)

        assertThat(result.allowed).isTrue()
        assertThat(result.remainingTokens).isEqualTo(99)
        assertThat(result.resetTimeSeconds).isEqualTo(10)
        assertThat(result.retryAfterSeconds).isNull()
    }

    @Test
    fun `denied should create result with allowed false`() {
        val result = RateLimitResult.denied(resetTime = 10, retryAfter = 5)

        assertThat(result.allowed).isFalse()
        assertThat(result.remainingTokens).isEqualTo(0)
        assertThat(result.resetTimeSeconds).isEqualTo(10)
        assertThat(result.retryAfterSeconds).isEqualTo(5)
    }

    @Test
    fun `should create result with all parameters`() {
        val result = RateLimitResult(
            allowed = true,
            remainingTokens = 50,
            resetTimeSeconds = 30,
            retryAfterSeconds = 15
        )

        assertThat(result.allowed).isTrue()
        assertThat(result.remainingTokens).isEqualTo(50)
        assertThat(result.resetTimeSeconds).isEqualTo(30)
        assertThat(result.retryAfterSeconds).isEqualTo(15)
    }

    @Test
    fun `should support data class copy`() {
        val original = RateLimitResult.allowed(100, 10)
        val copied = original.copy(remainingTokens = 50)

        assertThat(copied.allowed).isTrue()
        assertThat(copied.remainingTokens).isEqualTo(50)
        assertThat(copied.resetTimeSeconds).isEqualTo(10)
    }

    @Test
    fun `should implement equals and hashCode`() {
        val result1 = RateLimitResult.allowed(99, 10)
        val result2 = RateLimitResult.allowed(99, 10)
        val result3 = RateLimitResult.allowed(98, 10)

        assertThat(result1).isEqualTo(result2)
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode())
        assertThat(result1).isNotEqualTo(result3)
    }

    @Test
    fun `should implement toString`() {
        val result = RateLimitResult.allowed(99, 10)
        val toString = result.toString()

        assertThat(toString).contains("allowed=true")
        assertThat(toString).contains("remainingTokens=99")
        assertThat(toString).contains("resetTimeSeconds=10")
    }
}
