package com.thecode007.turboxpress.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Enables Spring's annotation-driven cache abstraction.
 *
 * Uses [CaffeineCacheManager] for robust in-memory caching with TTL and size limits.
 */
@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val caffeine = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(60, TimeUnit.MINUTES)

        val cacheManager = CaffeineCacheManager("driver-work-pages", "restaurants", "zones", "settings")
        cacheManager.setCaffeine(caffeine)
        return cacheManager
    }
}
