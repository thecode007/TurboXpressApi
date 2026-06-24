package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.RouteResponseDto
import com.thecode007.turboxpress.routing.RoutingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/routing")
class RoutingController(private val routingService: RoutingService) {

    @GetMapping("/path")
    fun getPath(
        @RequestParam startLat: Double,
        @RequestParam startLon: Double,
        @RequestParam endLat: Double,
        @RequestParam endLon: Double
    ): ResponseEntity<BaseResponse<RouteResponseDto>> {
        return try {
            val route = routingService.getRoute(startLat, startLon, endLat, endLon)
            ResponseEntity.ok(BaseResponse.success("Route fetched successfully", route))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(BaseResponse.error("Routing error: ${e.message}"))
        }
    }
}
