package com.jsoonworld.ratelimiter.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RateLimitAlgorithmTest {

    @Test
    fun `should have TOKEN_BUCKET algorithm`() {
        val algorithm = RateLimitAlgorithm.TOKEN_BUCKET
        assertThat(algorithm.name).isEqualTo("TOKEN_BUCKET")
    }

    @Test
    fun `should have SLIDING_WINDOW_LOG algorithm`() {
        val algorithm = RateLimitAlgorithm.SLIDING_WINDOW_LOG
        assertThat(algorithm.name).isEqualTo("SLIDING_WINDOW_LOG")
    }

    @Test
    fun `should have SLIDING_WINDOW_COUNTER algorithm`() {
        val algorithm = RateLimitAlgorithm.SLIDING_WINDOW_COUNTER
        assertThat(algorithm.name).isEqualTo("SLIDING_WINDOW_COUNTER")
    }

    @Test
    fun `should have FIXED_WINDOW algorithm`() {
        val algorithm = RateLimitAlgorithm.FIXED_WINDOW
        assertThat(algorithm.name).isEqualTo("FIXED_WINDOW")
    }

    @Test
    fun `should have LEAKY_BUCKET algorithm`() {
        val algorithm = RateLimitAlgorithm.LEAKY_BUCKET
        assertThat(algorithm.name).isEqualTo("LEAKY_BUCKET")
    }

    @Test
    fun `should have 5 algorithms`() {
        val algorithms = RateLimitAlgorithm.entries
        assertThat(algorithms).hasSize(5)
    }

    @Test
    fun `should convert from string using valueOf`() {
        val algorithm = RateLimitAlgorithm.valueOf("TOKEN_BUCKET")
        assertThat(algorithm).isEqualTo(RateLimitAlgorithm.TOKEN_BUCKET)
    }
}
