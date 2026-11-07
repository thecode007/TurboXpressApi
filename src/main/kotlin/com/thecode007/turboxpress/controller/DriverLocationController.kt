package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.DriverLocationUpdateRequest
import com.thecode007.turboxpress.dto.NearestDriverResponse
import com.thecode007.turboxpress.dto.UpdateLocationRequest
import com.thecode007.turboxpress.service.DriverLocationService
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/drivers")
class DriverLocationController(
    private val driverLocationService: DriverLocationService
) {

    @PutMapping("/location")
    fun updateLocation(
        @AuthenticationPrincipal principal: PermissionDecorator,
        @RequestBody request: DriverLocationUpdateRequest
    ): ResponseEntity<Void> {
        val driverId = UUID.fromString(principal.getUserId())
        val fullRequest = UpdateLocationRequest(driverId, request.latitude, request.longitude)
        driverLocationService.updateLocation(fullRequest)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/heartbeat")
    fun heartbeat(
        @AuthenticationPrincipal principal: PermissionDecorator,
        @RequestBody(required = false) request: DriverLocationUpdateRequest?
    ): ResponseEntity<Void> {
        val driverId = UUID.fromString(principal.getUserId())
        if (request != null) {
            driverLocationService.updateLocation(
                UpdateLocationRequest(driverId, request.latitude, request.longitude)
            )
        } else {
            driverLocationService.recordHeartbeat(driverId)
        }
        return ResponseEntity.ok().build()
    }

    @GetMapping("/nearest")
    fun getNearestDriver(@RequestParam restaurantId: Long): ResponseEntity<NearestDriverResponse> {
        val response = driverLocationService.findNearestDriver(restaurantId)
        return if (response != null) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
