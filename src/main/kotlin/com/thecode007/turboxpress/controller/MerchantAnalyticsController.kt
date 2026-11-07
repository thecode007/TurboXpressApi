package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.AveragePrepareTimeResponse
import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.OrdersPerDayResponse
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import com.thecode007.turboxpress.service.AnalyticsService
import com.thecode007.turboxpress.service.RestaurantService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/merchant/restaurants/my/analytics")
class MerchantAnalyticsController(
    private val analyticsService: AnalyticsService,
    private val restaurantService: RestaurantService
) {

    @GetMapping("/average-prepare-time")
    fun getAveragePrepareTime(
        @AuthenticationPrincipal principal: PermissionDecorator,
        @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<BaseResponse<AveragePrepareTimeResponse>> {
        val restaurant = restaurantService.getRestaurantByOwner(principal.username)
        val stats = analyticsService.getAveragePrepareTime(restaurant.id, days)
        return ResponseEntity.ok(BaseResponse.success("Average prepare time retrieved successfully", stats))
    }

    @GetMapping("/orders-per-day")
    fun getOrdersPerDay(
        @AuthenticationPrincipal principal: PermissionDecorator,
        @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<BaseResponse<OrdersPerDayResponse>> {
        val restaurant = restaurantService.getRestaurantByOwner(principal.username)
        val stats = analyticsService.getOrdersPerDay(restaurant.id, days)
        return ResponseEntity.ok(BaseResponse.success("Orders per day retrieved successfully", stats))
    }
}
