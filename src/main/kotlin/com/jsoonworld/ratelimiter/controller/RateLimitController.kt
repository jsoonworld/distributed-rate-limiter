package com.jsoonworld.ratelimiter.controller

import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm
import com.jsoonworld.ratelimiter.model.RateLimitResult
import com.jsoonworld.ratelimiter.service.RateLimitService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/rate-limit")
class RateLimitController(
    private val rateLimitService: RateLimitService
) {

    @GetMapping("/check")
    suspend fun checkRateLimit(
        @RequestParam(defaultValue = "TOKEN_BUCKET") algorithm: RateLimitAlgorithm,
        @RequestParam(required = false) key: String?,
        request: ServerHttpRequest
    ): ResponseEntity<RateLimitResponse> {
        val clientKey = key ?: extractClientKey(request)
        val result = rateLimitService.checkRateLimit(clientKey, algorithm)

        return if (result.allowed) {
            ResponseEntity.ok()
                .headers { headers ->
                    headers.set("X-RateLimit-Remaining", result.remainingTokens.toString())
                    headers.set("X-RateLimit-Reset", result.resetTimeSeconds.toString())
                }
                .body(RateLimitResponse.fromResult(result, clientKey))
        } else {
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers { headers ->
                    headers.set("X-RateLimit-Remaining", "0")
                    headers.set("X-RateLimit-Reset", result.resetTimeSeconds.toString())
                    headers.set("Retry-After", result.retryAfterSeconds.toString())
                }
                .body(RateLimitResponse.fromResult(result, clientKey))
        }
    }

    @GetMapping("/remaining")
    suspend fun getRemainingLimit(
        @RequestParam(defaultValue = "TOKEN_BUCKET") algorithm: RateLimitAlgorithm,
        @RequestParam(required = false) key: String?,
        request: ServerHttpRequest
    ): ResponseEntity<Map<String, Any>> {
        val clientKey = key ?: extractClientKey(request)
        val remaining = rateLimitService.getRemainingLimit(clientKey, algorithm)

        return ResponseEntity.ok(
            mapOf(
                "key" to clientKey,
                "remaining" to remaining,
                "algorithm" to algorithm.name
            )
        )
    }

    @DeleteMapping("/reset")
    suspend fun resetRateLimit(
        @RequestParam(defaultValue = "TOKEN_BUCKET") algorithm: RateLimitAlgorithm,
        @RequestParam key: String
    ): ResponseEntity<Map<String, String>> {
        rateLimitService.resetLimit(key, algorithm)
        return ResponseEntity.ok(
            mapOf(
                "status" to "reset",
                "key" to key
            )
        )
    }

    private fun extractClientKey(request: ServerHttpRequest): String {
        val headers = request.headers

        // X-Forwarded-For header (proxy/load balancer environment)
        val forwardedFor = headers.getFirst("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            return forwardedFor.split(",").first().trim()
        }

        // X-Real-IP header
        val realIp = headers.getFirst("X-Real-IP")
        if (!realIp.isNullOrBlank()) {
            return realIp
        }

        return request.remoteAddress?.address?.hostAddress ?: "unknown"
    }
}

data class RateLimitResponse(
    val allowed: Boolean,
    val key: String,
    val remainingTokens: Long,
    val resetTimeSeconds: Long,
    val retryAfterSeconds: Long?,
    val message: String
) {
    companion object {
        fun fromResult(result: RateLimitResult, key: String) = RateLimitResponse(
            allowed = result.allowed,
            key = key,
            remainingTokens = result.remainingTokens,
            resetTimeSeconds = result.resetTimeSeconds,
            retryAfterSeconds = result.retryAfterSeconds,
            message = if (result.allowed) "Request allowed" else "Rate limit exceeded"
        )
    }
}
