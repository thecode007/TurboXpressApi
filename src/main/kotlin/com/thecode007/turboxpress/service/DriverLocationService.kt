package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.NearestDriverResponse
import com.thecode007.turboxpress.dto.UpdateLocationRequest
import com.thecode007.turboxpress.repository.DriverProfileRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import com.thecode007.turboxpress.controller.DriverLocationWebSocketHandler

@Service
class DriverLocationService(
    private val driverProfileRepository: DriverProfileRepository,
    private val restaurantRepository: RestaurantRepository,
    private val driverLocationWebSocketHandler: DriverLocationWebSocketHandler
) {
    // SRID 4326 is standard for WGS 84 (GPS coordinates)
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Transactional
    fun updateLocation(request: UpdateLocationRequest) {
        val driverProfile = driverProfileRepository.findById(request.driverId)
            .orElseThrow { IllegalArgumentException("Driver not found") }

        // Note: Coordinate takes (longitude, latitude) usually, PostGIS takes (lon, lat)
        val point = geometryFactory.createPoint(Coordinate(request.longitude, request.latitude))
        
        driverProfile.currentLocation = point
        driverProfileRepository.save(driverProfile)
        
        driverLocationWebSocketHandler.broadcastLocation(
            driverId = driverProfile.userId.toString(),
            latitude = request.latitude,
            longitude = request.longitude
        )
    }

    @Transactional(readOnly = true)
    fun findNearestDriver(restaurantId: Long): NearestDriverResponse? {
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { IllegalArgumentException("Restaurant not found") }

        val nearestDriver = driverProfileRepository.findNearestIdleDriver(restaurant.location)
            ?: return null

        return NearestDriverResponse(
            driverId = nearestDriver.userId,
            driverName = nearestDriver.displayName,
            latitude = nearestDriver.currentLocation!!.y,
            longitude = nearestDriver.currentLocation!!.x,
            vehiclePlate = nearestDriver.vehiclePlate
        )
    }
}
