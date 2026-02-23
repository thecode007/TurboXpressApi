package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.service.OwnerService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/owners")
@PreAuthorize("hasRole('ADMIN')")
class OwnerController(
    private val ownerService: OwnerService
) {

    @GetMapping
    fun getAllOwners(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<BaseResponse<PageResponse<OwnerResponse>>> {
        val pageable = PageRequest.of(page, size)
        val owners = ownerService.getAllOwners(pageable)
        return ResponseEntity.ok(BaseResponse.success("Owners retrieved successfully", owners))
    }
    
    @GetMapping("/{phoneNumber}")
    fun getOwnerByPhone(
        @PathVariable phoneNumber: String
    ): ResponseEntity<BaseResponse<OwnerResponse>> {
        val owner = ownerService.getOwnerByPhone(phoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Owner retrieved successfully", owner))
    }
    
    @PostMapping
    fun createOwner(
        @Valid @RequestBody request: CreateOwnerRequest
    ): ResponseEntity<BaseResponse<OwnerResponse>> {
        val owner = ownerService.createOwner(request)
        return ResponseEntity.ok(BaseResponse.success("Owner created successfully", owner))
    }
    
    @PutMapping("/{phoneNumber}")
    fun updateOwner(
        @PathVariable phoneNumber: String,
        @Valid @RequestBody request: UpdateOwnerRequest
    ): ResponseEntity<BaseResponse<OwnerResponse>> {
        val owner = ownerService.updateOwner(phoneNumber, request)
        return ResponseEntity.ok(BaseResponse.success("Owner updated successfully", owner))
    }
    
    @DeleteMapping("/{phoneNumber}")
    fun deleteOwner(
        @PathVariable phoneNumber: String
    ): ResponseEntity<BaseResponse<Nothing>> {
        ownerService.deleteOwner(phoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Owner deleted successfully", null))
    }
}
