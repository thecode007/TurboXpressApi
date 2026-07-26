package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.CustomerSearchResponse
import com.thecode007.turboxpress.dto.UpdateCustomerRequest
import com.thecode007.turboxpress.repository.CustomerRepository
import com.thecode007.turboxpress.repository.DeliveryZoneRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/customers")
class CustomerController(
    private val customerRepository: CustomerRepository,
    private val deliveryZoneRepository: DeliveryZoneRepository
) {

    /** Search for a customer by exact phone number. Returns null-data 404 if not found. */
    @GetMapping("/search")
    fun searchCustomerByPhone(@RequestParam phone: String): ResponseEntity<BaseResponse<CustomerSearchResponse>> {
        val customerOpt = customerRepository.findByPhoneNumber(phone)
        if (customerOpt.isEmpty) {
            return ResponseEntity.ok(BaseResponse.notFound(message = "Customer not found"))
        }
        return ResponseEntity.ok(BaseResponse.success(data = customerOpt.get().toDto()))
    }

    /** Get a customer by ID. */
    @GetMapping("/{id}")
    fun getCustomerById(@PathVariable id: Long): ResponseEntity<BaseResponse<CustomerSearchResponse>> {
        val customer = customerRepository.findById(id).orElse(null)
            ?: return ResponseEntity.ok(BaseResponse.notFound(message = "Customer not found"))
        return ResponseEntity.ok(BaseResponse.success(data = customer.toDto()))
    }

    /**
     * Update a customer's zone and/or clear their pinned coordinates.
     *
     * Typical use-cases:
     * - Customer moved to a new area: set deliveryZoneId + resetCoordinates=true
     * - Driver delivered and the pin is already saved automatically — no action needed here
     */
    @PutMapping("/{id}")
    fun updateCustomer(
        @PathVariable id: Long,
        @RequestBody request: UpdateCustomerRequest
    ): ResponseEntity<BaseResponse<CustomerSearchResponse>> {
        val customer = customerRepository.findById(id).orElse(null)
            ?: return ResponseEntity.ok(BaseResponse.notFound(message = "Customer not found"))

        if (request.fullName != null) customer.fullName = request.fullName
        if (request.detailedAddress != null) customer.detailedAddress = request.detailedAddress

        if (request.deliveryZoneId != null) {
            val zone = deliveryZoneRepository.findById(request.deliveryZoneId).orElse(null)
            if (zone != null) {
                customer.deliveryZone = zone
            }
        }

        if (request.resetCoordinates) {
            customer.latitude = null
            customer.longitude = null
        }

        return ResponseEntity.ok(BaseResponse.success(data = customerRepository.save(customer).toDto()))
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun com.thecode007.turboxpress.entity.Customer.toDto() = CustomerSearchResponse(
        id = id,
        userId = userId,
        fullName = fullName ?: "",
        phoneNumber = phoneNumber,
        deliveryZoneId = deliveryZone?.id,
        deliveryZoneName = deliveryZone?.name,
        latitude = latitude,
        longitude = longitude,
        detailedAddress = detailedAddress
    )
}
