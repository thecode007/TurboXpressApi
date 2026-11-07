package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.NearestDriverResponse
import com.thecode007.turboxpress.dto.UpdateLocationRequest
import com.thecode007.turboxpress.exception.ResourceNotFoundException
import com.thecode007.turboxpress.repository.DriverProfileRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.*

@Service
class DriverLocationService(
    private val driverProfileRepository: DriverProfileRepository,
    private val restaurantRepository: RestaurantRepository,
    private val cacheManager: CacheManager,
    @Value("\${driver.location.db-sync-interval-seconds:15}")
    private val dbSyncIntervalSeconds: Long = 15,
    @Value("\${driver.location.min-distance-meters:25.0}")
    private val minDistanceMeters: Double = 25.0,
    @Value("\${driver.online.stale-threshold-seconds:45}")
    private val staleThresholdSeconds: Long = 45
) {
    data class DriverPresenceEntry(
        val latitude: Double?,
        val longitude: Double?,
        val lastSeenAt: Instant,
        val lastDbSyncAt: Instant
    )

    // SRID 4326 is standard for WGS 84 (GPS coordinates)
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    private fun presenceCache() = cacheManager.getCache("driver-presence")

    /**
     * Updates the driver's real-time presence in the in-memory Caffeine cache and
     * throttles PostgreSQL spatial/heartbeat writes unless [minDistanceMeters] or
     * [dbSyncIntervalSeconds] has been exceeded.
     */
    @Transactional
    fun updateLocation(request: UpdateLocationRequest) {
        val now = Instant.now()
        val cache = presenceCache()
        val existing = cache?.get(request.driverId, DriverPresenceEntry::class.java)

        val shouldPersistToDb = existing == null ||
                existing.latitude == null ||
                existing.longitude == null ||
                Duration.between(existing.lastDbSyncAt, now).seconds >= dbSyncIntervalSeconds ||
                haversineMeters(existing.latitude, existing.longitude, request.latitude, request.longitude) >= minDistanceMeters

        if (!shouldPersistToDb) {
            cache?.put(
                request.driverId,
                existing!!.copy(
                    latitude = request.latitude,
                    longitude = request.longitude,
                    lastSeenAt = now
                )
            )
            return
        }

        val driverProfile = driverProfileRepository.findById(request.driverId)
            .orElseThrow { ResourceNotFoundException("Driver not found") }

        // Note: Coordinate takes (longitude, latitude) usually, PostGIS takes (lon, lat)
        val point = geometryFactory.createPoint(Coordinate(request.longitude, request.latitude))

        driverProfile.currentLocation = point
        driverProfile.recordHeartbeat(now)
        driverProfileRepository.save(driverProfile)

        cache?.put(
            request.driverId,
            DriverPresenceEntry(
                latitude = request.latitude,
                longitude = request.longitude,
                lastSeenAt = now,
                lastDbSyncAt = now
            )
        )
    }

    /**
     * Lightweight heartbeat recorder for non-GPS driver API calls (e.g., polling active delivery
     * or checking online status). Throttles DB writes using the in-memory presence cache.
     */
    @Transactional
    fun recordHeartbeat(driverId: UUID) {
        val now = Instant.now()
        val cache = presenceCache()
        val existing = cache?.get(driverId, DriverPresenceEntry::class.java)

        val shouldSyncDb = existing == null ||
                Duration.between(existing.lastDbSyncAt, now).seconds >= dbSyncIntervalSeconds

        if (shouldSyncDb) {
            driverProfileRepository.touchLastSeenAt(driverId, now)
            cache?.put(
                driverId,
                DriverPresenceEntry(
                    latitude = existing?.latitude,
                    longitude = existing?.longitude,
                    lastSeenAt = now,
                    lastDbSyncAt = now
                )
            )
        } else {
            cache?.put(driverId, existing!!.copy(lastSeenAt = now))
        }
    }

    fun evictPresence(driverId: UUID) {
        presenceCache()?.evict(driverId)
    }

    @Transactional(readOnly = true)
    fun findNearestDriver(restaurantId: Long): NearestDriverResponse? {
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { ResourceNotFoundException("Restaurant not found") }

        val cutoff = Instant.now().minusSeconds(staleThresholdSeconds)
        val nearestDriver = driverProfileRepository.findNearestIdleDriver(restaurant.location, cutoff)
            ?: return null

        val cached = presenceCache()?.get(nearestDriver.userId, DriverPresenceEntry::class.java)
        val lat = cached?.latitude ?: nearestDriver.currentLocation!!.y
        val lng = cached?.longitude ?: nearestDriver.currentLocation!!.x

        return NearestDriverResponse(
            driverId = nearestDriver.userId,
            driverName = nearestDriver.displayName,
            latitude = lat,
            longitude = lng,
            vehiclePlate = nearestDriver.vehiclePlate
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
}
