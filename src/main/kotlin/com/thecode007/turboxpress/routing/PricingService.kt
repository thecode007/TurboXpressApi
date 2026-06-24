package com.thecode007.turboxpress.routing

import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import org.springframework.stereotype.Service

import com.thecode007.turboxpress.service.AppSettingService

@Service
class PricingService(
    private val graphHopper: GraphHopper,
    private val appSettingService: AppSettingService
) {

    fun calculateDeliveryPrice(
        driverLat: Double, 
        driverLon: Double, 
        customerLat: Double, 
        customerLon: Double, 
        pricePerKm: Double
    ): Pair<Double, Double> {
        val request = GHRequest(driverLat, driverLon, customerLat, customerLon)
            .setProfile("car")
        
        val response = graphHopper.route(request)
        
        if (response.hasErrors()) {
            throw UnroutableLocationException(
                "Could not calculate a route between the given locations.", 
                response.errors[0]
            )
        }
        
        val path = response.best ?: throw UnroutableLocationException("No path found between the given locations.")
        
        val distanceInMeters = path.distance
        val distanceInKm = distanceInMeters / 1000.0
        
        val settings = appSettingService.getSettings()
        val rawPrice = settings.baseFare + (distanceInKm * pricePerKm)
        val finalPrice = java.math.BigDecimal(rawPrice).setScale(2, java.math.RoundingMode.HALF_EVEN).toDouble()
        return Pair(finalPrice, distanceInKm)
    }


}
