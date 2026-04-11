package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.RestaurantFinanceSummary
import com.thecode007.turboxpress.service.FinanceService
import com.thecode007.turboxpress.service.RestaurantService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/restaurants")
class FinanceController(
    private val financeService: FinanceService,
    private val restaurantService: RestaurantService
) {

    @GetMapping("/{id}/finance")
    fun getFinanceSummary(@PathVariable id: Long): ResponseEntity<BaseResponse<RestaurantFinanceSummary>> {
        val summary = financeService.getRestaurantSummary(id)
        return ResponseEntity.ok(BaseResponse.success("Finance summary retrieved successfully", summary))
    }

    @PostMapping("/{id}/settle")
    fun settleRestaurant(
        @PathVariable id: Long,
        @RequestParam(required = false) amount: Double?
    ): ResponseEntity<BaseResponse<RestaurantFinanceSummary>> {
        val summary = financeService.settleRestaurant(id, amount)
        return ResponseEntity.ok(BaseResponse.success("Account settled successfully", summary))
    }
}
