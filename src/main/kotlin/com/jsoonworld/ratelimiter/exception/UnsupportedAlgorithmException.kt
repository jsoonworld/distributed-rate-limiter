package com.jsoonworld.ratelimiter.exception

import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm

class UnsupportedAlgorithmException(
    val algorithm: RateLimitAlgorithm,
    val supportedAlgorithms: List<RateLimitAlgorithm>
) : RuntimeException(
    "Algorithm '${algorithm.name}' is not implemented. Supported algorithms: ${supportedAlgorithms.map { it.name }}"
)
