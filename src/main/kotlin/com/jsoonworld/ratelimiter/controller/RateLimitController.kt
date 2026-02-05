package com.jsoonworld.ratelimiter.controller

import com.jsoonworld.ratelimiter.config.RateLimiterProperties
import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm
import com.jsoonworld.ratelimiter.service.RateLimitService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/rate-limit")
class RateLimitController(
    private val rateLimitService: RateLimitService,
    private val properties: RateLimiterProperties
) {

    @GetMapping("/check")
    suspend fun checkRateLimit(
        @RequestParam(required = false) algorithm: RateLimitAlgorithm?,
        @RequestParam(required = false) key: String?,
        request: ServerHttpRequest
    ): ResponseEntity<RateLimitResponse> {
        val clientKey = if (key.isNullOrBlank()) extractClientKey(request) else key
        val effectiveAlgorithm = algorithm ?: rateLimitService.defaultAlgorithm
        val result = rateLimitService.checkRateLimit(clientKey, effectiveAlgorithm)

        return if (result.allowed) {
            ResponseEntity.ok()
                .header("X-RateLimit-Remaining", result.remainingTokens.toString())
                .header("X-RateLimit-Reset", result.resetTimeSeconds.toString())
                .body(RateLimitResponse.fromResult(result, clientKey, effectiveAlgorithm))
        } else {
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", result.resetTimeSeconds.toString())
                .apply {
                    result.retryAfterSeconds?.let { retryAfter ->
                        header("Retry-After", retryAfter.toString())
                    }
                }
                .body(RateLimitResponse.fromResult(result, clientKey, effectiveAlgorithm))
        }
    }

    @GetMapping("/remaining")
    suspend fun getRemainingLimit(
        @RequestParam(required = false) algorithm: RateLimitAlgorithm?,
        @RequestParam(required = false) key: String?,
        request: ServerHttpRequest
    ): ResponseEntity<Map<String, Any>> {
        val clientKey = if (key.isNullOrBlank()) extractClientKey(request) else key
        val effectiveAlgorithm = algorithm ?: rateLimitService.defaultAlgorithm
        val remaining = rateLimitService.getRemainingLimit(clientKey, effectiveAlgorithm)

        return ResponseEntity.ok(
            mapOf(
                "key" to clientKey,
                "remaining" to remaining,
                "algorithm" to effectiveAlgorithm.name
            )
        )
    }

    @DeleteMapping("/reset")
    suspend fun resetRateLimit(
        @RequestParam(required = false) algorithm: RateLimitAlgorithm?,
        @RequestParam key: String
    ): ResponseEntity<Map<String, String>> {
        val effectiveAlgorithm = algorithm ?: rateLimitService.defaultAlgorithm
        rateLimitService.resetLimit(key, effectiveAlgorithm)

        return ResponseEntity.ok(
            mapOf(
                "status" to "reset",
                "key" to key
            )
        )
    }

    private fun extractClientKey(request: ServerHttpRequest): String {
        // Only trust proxy headers when explicitly configured
        if (properties.trustProxy) {
            // 1. X-Forwarded-For header (first IP)
            val forwardedFor = request.headers.getFirst("X-Forwarded-For")
            if (!forwardedFor.isNullOrBlank()) {
                return forwardedFor.split(",").first().trim()
            }

            // 2. X-Real-IP header
            val realIp = request.headers.getFirst("X-Real-IP")
            if (!realIp.isNullOrBlank()) {
                return realIp
            }
        }

        // 3. Remote Address (always used when trustProxy is false)
        return request.remoteAddress?.address?.hostAddress ?: "unknown"
    }
}
