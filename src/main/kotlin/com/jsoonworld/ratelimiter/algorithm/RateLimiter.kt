package com.jsoonworld.ratelimiter.algorithm

import com.jsoonworld.ratelimiter.model.RateLimitResult

interface RateLimiter {

    /**
     * 요청을 허용할지 결정합니다.
     * @param key 클라이언트 식별자 (IP, API Key, User ID 등)
     * @param permits 소비할 토큰/요청 수
     * @return RateLimitResult 허용 여부와 남은 한도 정보
     */
    suspend fun tryAcquire(key: String, permits: Long = 1): RateLimitResult

    /**
     * 현재 남은 토큰/요청 수를 확인합니다.
     */
    suspend fun getRemainingLimit(key: String): Long

    /**
     * 특정 키의 rate limit을 리셋합니다.
     */
    suspend fun reset(key: String)
}
