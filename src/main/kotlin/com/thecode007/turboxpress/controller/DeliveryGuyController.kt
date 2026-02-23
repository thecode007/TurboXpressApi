package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.service.DeliveryGuyService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/delivery-guys")
@PreAuthorize("hasRole('ADMIN')")
class DeliveryGuyController(
    private val deliveryGuyService: DeliveryGuyService
) {

    @GetMapping
    fun getAllDeliveryGuys(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<BaseResponse<PageResponse<DeliveryGuyResponse>>> {
        val pageable = PageRequest.of(page, size)
        val deliveryGuys = deliveryGuyService.getAllDeliveryGuys(pageable)
        return ResponseEntity.ok(BaseResponse.success("Delivery guys retrieved successfully", deliveryGuys))
    }
    
    @GetMapping("/{phoneNumber}")
    fun getDeliveryGuyByPhone(
        @PathVariable phoneNumber: String
    ): ResponseEntity<BaseResponse<DeliveryGuyResponse>> {
        val deliveryGuy = deliveryGuyService.getDeliveryGuyByPhone(phoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Delivery guy retrieved successfully", deliveryGuy))
    }
    
    @PostMapping
    fun createDeliveryGuy(
        @Valid @RequestBody request: CreateDeliveryGuyRequest
    ): ResponseEntity<BaseResponse<DeliveryGuyResponse>> {
        val deliveryGuy = deliveryGuyService.createDeliveryGuy(request)
        return ResponseEntity.ok(BaseResponse.success("Delivery guy created successfully", deliveryGuy))
    }
    
    @PutMapping("/{phoneNumber}")
    fun updateDeliveryGuy(
        @PathVariable phoneNumber: String,
        @Valid @RequestBody request: UpdateDeliveryGuyRequest
    ): ResponseEntity<BaseResponse<DeliveryGuyResponse>> {
        val deliveryGuy = deliveryGuyService.updateDeliveryGuy(phoneNumber, request)
        return ResponseEntity.ok(BaseResponse.success("Delivery guy updated successfully", deliveryGuy))
    }
    
    @DeleteMapping("/{phoneNumber}")
    fun deleteDeliveryGuy(
        @PathVariable phoneNumber: String
    ): ResponseEntity<BaseResponse<Nothing>> {
        deliveryGuyService.deleteDeliveryGuy(phoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Delivery guy deleted successfully", null))
    }
}
