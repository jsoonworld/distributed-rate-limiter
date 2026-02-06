package com.jsoonworld.ratelimiter.app.health

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Rate Limiter health indicator.
 * Checks Redis connectivity.
 */
@Component
class RateLimiterHealthIndicator(
    private val redisTemplate: ReactiveStringRedisTemplate
) : ReactiveHealthIndicator {

    override fun health(): Mono<Health> {
        return redisTemplate.connectionFactory
            ?.reactiveConnection
            ?.ping()
            ?.map { response ->
                Health.up()
                    .withDetail("redis", "connected")
                    .withDetail("ping", response)
                    .build()
            }
            ?.onErrorResume { throwable ->
                Mono.just(
                    Health.down()
                        .withDetail("redis", "disconnected")
                        .withException(throwable)
                        .build()
                )
            }
            ?: Mono.just(
                Health.down()
                    .withDetail("error", "Redis connection factory not available")
                    .build()
            )
    }
}
