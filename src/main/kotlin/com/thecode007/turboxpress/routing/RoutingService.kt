package com.thecode007.turboxpress.routing

import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.thecode007.turboxpress.dto.RoutePointDto
import com.thecode007.turboxpress.dto.RouteResponseDto
import org.springframework.stereotype.Service

@Service
class RoutingService(private val graphHopper: GraphHopper) {

    fun getRoute(startLat: Double, startLon: Double, endLat: Double, endLon: Double): RouteResponseDto {
        val request = GHRequest(startLat, startLon, endLat, endLon).setProfile("car")
        val response = graphHopper.route(request)
        
        if (response.hasErrors()) {
            throw RuntimeException("Routing failed: ${response.errors.firstOrNull()?.message}")
        }
        
        val path = response.best ?: throw RuntimeException("No path found")
        val points = path.points
        val routePoints = mutableListOf<RoutePointDto>()
        
        for (i in 0 until points.size()) {
            routePoints.add(RoutePointDto(points.getLat(i), points.getLon(i)))
        }
        
        return RouteResponseDto(
            distanceKm = path.distance / 1000.0,
            timeMs = path.time,
            points = routePoints
        )
    }
}
