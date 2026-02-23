package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.CreateDeliveryZoneRequest
import com.thecode007.turboxpress.dto.DeliveryZoneResponse
import com.thecode007.turboxpress.dto.UpdateDeliveryZoneRequest
import com.thecode007.turboxpress.service.DeliveryZoneService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/delivery-zones")
@PreAuthorize("hasRole('ADMIN')")
class DeliveryZoneController(
    private val deliveryZoneService: DeliveryZoneService
) {

    @GetMapping
    fun getAllDeliveryZones(): ResponseEntity<BaseResponse<List<DeliveryZoneResponse>>> {
        val zones = deliveryZoneService.getAllDeliveryZones()
        return ResponseEntity.ok(BaseResponse.success("Delivery zones retrieved successfully", zones))
    }

    @PostMapping
    fun createDeliveryZone(
        @Valid @RequestBody request: CreateDeliveryZoneRequest
    ): ResponseEntity<BaseResponse<DeliveryZoneResponse>> {
        val zone = deliveryZoneService.createDeliveryZone(request)
        return ResponseEntity.ok(BaseResponse.success("Delivery zone created successfully", zone))
    }

    @PutMapping("/{id}")
    fun updateDeliveryZone(
        @PathVariable id: Long,
        @RequestBody request: UpdateDeliveryZoneRequest
    ): ResponseEntity<BaseResponse<DeliveryZoneResponse>> {
        val zone = deliveryZoneService.updateDeliveryZone(id, request)
        return ResponseEntity.ok(BaseResponse.success("Delivery zone updated successfully", zone))
    }

    @DeleteMapping("/{id}")
    fun deleteDeliveryZone(
        @PathVariable id: Long
    ): ResponseEntity<BaseResponse<Nothing>> {
        deliveryZoneService.deleteDeliveryZone(id)
        return ResponseEntity.ok(BaseResponse.success("Delivery zone deleted successfully", null))
    }
}
