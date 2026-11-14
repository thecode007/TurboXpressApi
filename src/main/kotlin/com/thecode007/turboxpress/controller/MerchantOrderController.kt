package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.OrderResponse
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import com.thecode007.turboxpress.service.OrderService
import com.thecode007.turboxpress.service.RestaurantService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/merchant/orders")
class MerchantOrderController(
    private val orderService: OrderService,
    private val restaurantService: RestaurantService
) {
    @GetMapping
    fun getMyOrders(
        @AuthenticationPrincipal principal: PermissionDecorator
    ): ResponseEntity<BaseResponse<List<OrderResponse>>> {
        val restaurant = restaurantService.getRestaurantByOwner(principal.username)
        val orders = orderService.getOrdersForRestaurant(restaurant.id)
        return ResponseEntity.ok(BaseResponse.success("Orders retrieved successfully", orders))
    }
}

