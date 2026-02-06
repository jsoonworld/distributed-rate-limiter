package com.jsoonworld.ratelimiter.core

/**
 * Exception thrown when an unsupported rate limiting algorithm is requested.
 *
 * @property algorithm The algorithm that was requested but is not supported.
 * @property supportedAlgorithms List of algorithms that are currently implemented.
 */
class UnsupportedAlgorithmException(
    val algorithm: RateLimitAlgorithm,
    val supportedAlgorithms: List<RateLimitAlgorithm>
) : RuntimeException(
    "Algorithm '${algorithm.name}' is not implemented. Supported algorithms: ${supportedAlgorithms.joinToString { it.name }}"
)
