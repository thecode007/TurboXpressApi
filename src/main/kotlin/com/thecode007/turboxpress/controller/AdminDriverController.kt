package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.service.AdminDriverService
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/drivers")
class AdminDriverController(
    private val adminDriverService: AdminDriverService
) {

    @GetMapping
    fun getAllActiveDrivers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) search: String?
    ): ResponseEntity<BaseResponse<PageResponse<DriverResponse>>> {
        val pageable = PageRequest.of(page, size)
        val response = adminDriverService.getAllActiveDrivers(pageable, search)
        return ResponseEntity.ok(BaseResponse.success("Drivers retrieved successfully", response))
    }

    @GetMapping("/available")
    fun getAvailableDrivers(
        @RequestParam(required = false) search: String?
    ): ResponseEntity<BaseResponse<List<DriverResponse>>> {
        val response = adminDriverService.getAvailableDrivers(search)
        return ResponseEntity.ok(BaseResponse.success("Available drivers retrieved successfully", response))
    }

    @GetMapping("/{phoneNumber}")
    fun getDriverByPhone(@PathVariable phoneNumber: String): ResponseEntity<BaseResponse<DriverResponse>> {
        val driver = adminDriverService.getDriverByPhone(phoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Driver retrieved successfully", driver))
    }

    @PostMapping
    fun createDriver(@RequestBody request: CreateDriverRequest): ResponseEntity<BaseResponse<DriverResponse>> {
        val driver = adminDriverService.createDriver(request)
        return ResponseEntity.ok(BaseResponse.success("Driver created successfully", driver))
    }

    @PutMapping("/{phoneNumber}")
    fun updateDriver(
        @PathVariable phoneNumber: String,
        @RequestBody request: UpdateDriverRequest
    ): ResponseEntity<BaseResponse<DriverResponse>> {
        val driver = adminDriverService.updateDriver(phoneNumber, request)
        return ResponseEntity.ok(BaseResponse.success("Driver updated successfully", driver))
    }

    @DeleteMapping("/{phoneNumber}")
    fun deleteDriver(@PathVariable phoneNumber: String): ResponseEntity<BaseResponse<Nothing>> {
        adminDriverService.deleteDriver(phoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Driver deleted successfully"))
    }

    @PutMapping("/{phoneNumber}/password")
    fun changeDriverPassword(
        @PathVariable phoneNumber: String,
        @RequestBody request: ChangeDriverPasswordRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        adminDriverService.changeDriverPassword(phoneNumber, request)
        return ResponseEntity.ok(BaseResponse.success("Driver password updated successfully"))
    }
}
