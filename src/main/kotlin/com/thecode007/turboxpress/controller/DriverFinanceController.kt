package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.DriverFinanceSummary
import com.thecode007.turboxpress.service.FinanceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/drivers")
class DriverFinanceController(
    private val financeService: FinanceService
) {

    @GetMapping("/{phoneNumber}/finance")
    fun getFinanceSummary(@PathVariable phoneNumber: String): ResponseEntity<BaseResponse<DriverFinanceSummary>> {
        val summary = financeService.getDriverSummary(phoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Driver finance summary retrieved successfully", summary))
    }

    @PostMapping("/{phoneNumber}/settle")
    fun settleDriver(
        @PathVariable phoneNumber: String,
        @RequestParam(required = false) amount: Double?
    ): ResponseEntity<BaseResponse<DriverFinanceSummary>> {
        val summary = financeService.settleDriver(phoneNumber, amount)
        return ResponseEntity.ok(BaseResponse.success("Driver account settled successfully", summary))
    }
}
