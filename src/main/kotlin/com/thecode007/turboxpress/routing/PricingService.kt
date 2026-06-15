package com.thecode007.turboxpress.routing

import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import org.springframework.stereotype.Service

@Service
class PricingService(private val graphHopper: GraphHopper) {

    fun calculateDeliveryPrice(
        driverLat: Double, 
        driverLon: Double, 
        customerLat: Double, 
        customerLon: Double, 
        pricePerKm: Double
    ): Double {
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
        
        val rawPrice = BASE_FARE + (distanceInKm * pricePerKm)
        return java.math.BigDecimal(rawPrice).setScale(2, java.math.RoundingMode.HALF_EVEN).toDouble()
    }

    companion object {
        private const val BASE_FARE = 2.50
    }
}
