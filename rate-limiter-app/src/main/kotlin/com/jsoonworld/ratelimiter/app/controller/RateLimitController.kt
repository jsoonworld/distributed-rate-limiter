package com.jsoonworld.ratelimiter.app.controller

import com.jsoonworld.ratelimiter.core.RateLimitAlgorithm
import com.jsoonworld.ratelimiter.spring.RateLimiterProperties
import com.jsoonworld.ratelimiter.spring.service.RateLimitService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.*

/**
 * Rate Limit REST API Controller.
 */
@RestController
@RequestMapping("/api/v1/rate-limit")
class RateLimitController(
    private val rateLimitService: RateLimitService,
    private val properties: RateLimiterProperties
) {

    /**
     * Check rate limit.
     * GET /api/v1/rate-limit/check?key=xxx&algorithm=TOKEN_BUCKET
     */
    @GetMapping("/check")
    suspend fun checkRateLimit(
        @RequestParam(required = false) algorithm: RateLimitAlgorithm?,
        @RequestParam(required = false) key: String?,
        request: ServerHttpRequest
    ): ResponseEntity<RateLimitResponse> {
        val clientKey = if (key.isNullOrBlank()) extractClientKey(request) else key
        val effectiveAlgorithm = algorithm ?: properties.algorithm
        val result = rateLimitService.checkRateLimit(clientKey, effectiveAlgorithm)

        // Convert resetAfterSeconds (duration) to epoch timestamp for X-RateLimit-Reset header
        val resetEpoch = System.currentTimeMillis() / 1000 + result.resetAfterSeconds

        return if (result.allowed) {
            ResponseEntity.ok()
                .header("X-RateLimit-Remaining", result.remaining.toString())
                .header("X-RateLimit-Reset", resetEpoch.toString())
                .body(RateLimitResponse.fromResult(result, clientKey, effectiveAlgorithm))
        } else {
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", resetEpoch.toString())
                .header("Retry-After", result.retryAfterSeconds.toString())
                .body(RateLimitResponse.fromResult(result, clientKey, effectiveAlgorithm))
        }
    }

    /**
     * Get remaining limit.
     * GET /api/v1/rate-limit/remaining?key=xxx&algorithm=TOKEN_BUCKET
     */
    @GetMapping("/remaining")
    suspend fun getRemainingLimit(
        @RequestParam(required = false) algorithm: RateLimitAlgorithm?,
        @RequestParam(required = false) key: String?,
        request: ServerHttpRequest
    ): ResponseEntity<Map<String, Any>> {
        val clientKey = if (key.isNullOrBlank()) extractClientKey(request) else key
        val effectiveAlgorithm = algorithm ?: properties.algorithm
        val remaining = rateLimitService.getRemainingLimit(clientKey, effectiveAlgorithm)

        return ResponseEntity.ok(
            mapOf(
                "key" to clientKey,
                "remaining" to remaining,
                "algorithm" to effectiveAlgorithm.name
            )
        )
    }

    /**
     * Reset rate limit.
     * DELETE /api/v1/rate-limit/reset?key=xxx&algorithm=TOKEN_BUCKET
     */
    @DeleteMapping("/reset")
    suspend fun resetRateLimit(
        @RequestParam(required = false) algorithm: RateLimitAlgorithm?,
        @RequestParam key: String
    ): ResponseEntity<Map<String, String>> {
        val effectiveAlgorithm = algorithm ?: properties.algorithm
        rateLimitService.resetLimit(key, effectiveAlgorithm)

        return ResponseEntity.ok(
            mapOf(
                "status" to "reset",
                "key" to key
            )
        )
    }

    private fun extractClientKey(request: ServerHttpRequest): String {
        // X-Forwarded-For header (first IP)
        val forwardedFor = request.headers.getFirst("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            return forwardedFor.split(",").first().trim()
        }

        // X-Real-IP header
        val realIp = request.headers.getFirst("X-Real-IP")
        if (!realIp.isNullOrBlank()) {
            return realIp
        }

        // Remote Address
        return request.remoteAddress?.address?.hostAddress ?: "unknown"
    }
}
