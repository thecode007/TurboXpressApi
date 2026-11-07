package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.CreateDeliveryZoneRequest
import com.thecode007.turboxpress.dto.DeliveryZoneResponse
import com.thecode007.turboxpress.dto.UpdateDeliveryZoneRequest
import com.thecode007.turboxpress.service.DeliveryZoneService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/delivery-zones")
@PreAuthorize("hasRole('ADMIN')")
class DeliveryZoneController(
    private val deliveryZoneService: DeliveryZoneService
) {
    private val log = LoggerFactory.getLogger(DeliveryZoneController::class.java)

    @GetMapping
    fun getAllDeliveryZones(): ResponseEntity<BaseResponse<List<DeliveryZoneResponse>>> {
        val zones = deliveryZoneService.getAllDeliveryZones()
        return ResponseEntity.ok(BaseResponse.success("Delivery zones retrieved successfully", zones))
    }

    @PostMapping
    fun createDeliveryZone(
        @Valid @RequestBody request: CreateDeliveryZoneRequest
    ): ResponseEntity<BaseResponse<DeliveryZoneResponse>> {
        log.info("Received request to create delivery zone: name='{}', isActive={}, wktPolygon='{}'",
            request.name, request.isActive, request.wktPolygon)
        val zone = deliveryZoneService.createDeliveryZone(request)
        return ResponseEntity.ok(BaseResponse.success("Delivery zone created successfully", zone))
    }

    @PutMapping("/{id}")
    fun updateDeliveryZone(
        @PathVariable id: Long,
        @RequestBody request: UpdateDeliveryZoneRequest
    ): ResponseEntity<BaseResponse<DeliveryZoneResponse>> {
        log.info("Received request to update delivery zone id={}: name='{}', isActive={}, wktPolygon='{}'",
            id, request.name, request.isActive, request.wktPolygon)
        val zone = deliveryZoneService.updateDeliveryZone(id, request)
        return ResponseEntity.ok(BaseResponse.success("Delivery zone updated successfully", zone))
    }

    @DeleteMapping("/{id}")
    fun deleteDeliveryZone(
        @PathVariable id: Long
    ): ResponseEntity<BaseResponse<Nothing>> {
        log.info("Received request to delete delivery zone id={}", id)
        deliveryZoneService.deleteDeliveryZone(id)
        return ResponseEntity.ok(BaseResponse.success("Delivery zone deleted successfully", null))
    }
}

/**
 * Read-only delivery zone endpoint accessible to restaurant owners (MERCHANT role).
 * Used by the mobile app during order creation to populate the zone dropdown.
 */
@RestController
@RequestMapping("/api/merchant/delivery-zones")
class MerchantDeliveryZoneController(
    private val deliveryZoneService: DeliveryZoneService
) {
    @GetMapping
    fun getAllDeliveryZones(): ResponseEntity<BaseResponse<List<DeliveryZoneResponse>>> {
        val zones = deliveryZoneService.getAllDeliveryZones()
        return ResponseEntity.ok(BaseResponse.success("Delivery zones retrieved successfully", zones))
    }
}
