package com.jsoonworld.ratelimiter.core.redis

/**
 * Redis client abstraction for rate limiting operations.
 * Supports various implementations: Spring Data Redis, Lettuce, Jedis, etc.
 */
interface RedisClient {

    /**
     * Execute a Lua script atomically.
     * @param script Lua script content
     * @param keys KEYS array
     * @param args ARGV array
     * @param resultType Expected result type class
     * @return Script execution result
     */
    suspend fun <T> executeScript(
        script: String,
        keys: List<String>,
        args: List<String>,
        resultType: Class<T>
    ): T

    /**
     * Delete a key.
     * @param key Redis key to delete
     * @return true if key was deleted
     */
    suspend fun delete(key: String): Boolean
}
