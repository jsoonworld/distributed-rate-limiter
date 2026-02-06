package com.jsoonworld.ratelimiter.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Rate Limiter example application.
 * Demonstrates Spring Boot Starter usage.
 */
@SpringBootApplication
class RateLimiterApplication

fun main(args: Array<String>) {
    runApplication<RateLimiterApplication>(*args)
}
