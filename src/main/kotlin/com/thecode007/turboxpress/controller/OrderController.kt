package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.OrderCreateRequest
import com.thecode007.turboxpress.dto.OrderResponse
import com.thecode007.turboxpress.entity.OrderStatus
import com.thecode007.turboxpress.service.OrderService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import org.springframework.security.core.annotation.AuthenticationPrincipal
import java.util.UUID

@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {

    @GetMapping("/active-delivery")
    fun getActiveDelivery(
        @AuthenticationPrincipal principal: PermissionDecorator
    ): ResponseEntity<BaseResponse<OrderResponse>> {
        val userId = UUID.fromString(principal.getUserId())
        val order = orderService.getActiveDeliveryForDriver(userId)
        return if (order != null) {
            ResponseEntity.ok(BaseResponse.success("Active delivery retrieved successfully", order))
        } else {
            ResponseEntity.ok(BaseResponse.success("No active delivery found", null))
        }
    }

    @PostMapping
    fun createOrder(@RequestBody request: OrderCreateRequest): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.createOrder(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(BaseResponse.created("Order created successfully", order))
    }

    @GetMapping("/{id}")
    fun getOrderById(@PathVariable id: Long): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.getOrderById(id)
        return ResponseEntity.ok(BaseResponse.success("Order retrieved successfully", order))
    }

    @GetMapping
    fun getAllOrders(): ResponseEntity<BaseResponse<List<OrderResponse>>> {
        val orders = orderService.getAllOrders()
        return ResponseEntity.ok(BaseResponse.success("Orders retrieved successfully", orders))
    }

    @PutMapping("/{id}/assign-driver")
    fun assignDriver(
        @PathVariable id: Long,
        @RequestParam driverPhoneNumber: String
    ): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.assignDriver(id, driverPhoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Driver assigned successfully", order))
    }

    @PostMapping("/{id}/auto-assign")
    fun autoAssignDriver(
        @PathVariable id: Long
    ): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.autoAssignDriver(id)
        return ResponseEntity.ok(BaseResponse.success("Nearest driver auto-assigned successfully", order))
    }

    @PostMapping("/{id}/reject")
    fun rejectOrder(
        @PathVariable id: Long
    ): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.rejectOrder(id)
        return ResponseEntity.ok(BaseResponse.success("Order rejected successfully", order))
    }

    @PatchMapping("/{id}/status")
    fun updateOrderStatus(
        @PathVariable id: Long,
        @RequestParam status: OrderStatus
    ): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.updateOrderStatus(id, status)
        return ResponseEntity.ok(BaseResponse.success("Order status updated successfully", order))
    }
}
