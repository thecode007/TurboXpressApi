package com.thecode007.turboxpress.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Enables Spring's annotation-driven cache abstraction.
 *
 * Uses [ConcurrentMapCacheManager] (in-JVM, no external dependency).
 * For a production cluster you can swap this for a Redis-backed manager
 * without changing any service code.
 *
 * Caches registered here:
 *
 * - "driver-work-pages"
 *   Key  → "<driverId>:<YYYY-MM-DD>"
 *   Populated by  → [OrderService.getDriverWorkPage]  (@Cacheable)
 *   Evicted by    → [OrderService.updateOrderStatus]  (@CacheEvict, allEntries=true)
 *                   [OrderService.updateDeliveryStatus](@CacheEvict, allEntries=true)
 *
 * Cache lifetime: in-memory, cleared on server restart.
 * Entries are also force-evicted on every order status change, so drivers
 * always see fresh data when a new delivery completes.
 */
@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager =
        ConcurrentMapCacheManager("driver-work-pages")
}
