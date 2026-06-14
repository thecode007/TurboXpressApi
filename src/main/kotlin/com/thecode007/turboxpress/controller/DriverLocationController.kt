package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.NearestDriverResponse
import com.thecode007.turboxpress.dto.UpdateLocationRequest
import com.thecode007.turboxpress.service.DriverLocationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/drivers")
class DriverLocationController(
    private val driverLocationService: DriverLocationService
) {

    @PutMapping("/location")
    fun updateLocation(@RequestBody request: UpdateLocationRequest): ResponseEntity<Void> {
        driverLocationService.updateLocation(request)
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
