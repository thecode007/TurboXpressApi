package com.thecode007.turboxpress.routing

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/pricing")
class PricingController(private val pricingService: PricingService) {

    @GetMapping("/calculate")
    fun calculate(
        @RequestParam driverLat: Double,
        @RequestParam driverLon: Double,
        @RequestParam customerLat: Double,
        @RequestParam customerLon: Double,
        @RequestParam pricePerKm: Double
    ): ResponseEntity<*> {
        if (pricePerKm <= 0) {
            return ResponseEntity.badRequest().body(mapOf("error" to "pricePerKm must be greater than 0"))
        }
        
        return try {
            val result = pricingService.calculateDeliveryPrice(driverLat, driverLon, customerLat, customerLon, pricePerKm)
            ResponseEntity.ok(mapOf("fare" to result.first, "distance" to result.second))
        } catch (e: UnroutableLocationException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
