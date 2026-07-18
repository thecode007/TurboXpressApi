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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import com.thecode007.turboxpress.service.OrderEventBroadcaster
import java.util.UUID

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderEventBroadcaster: OrderEventBroadcaster
) {

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

    @GetMapping("/events", produces = ["text/event-stream"])
    fun streamOrderEvents(): SseEmitter {
        val activeOrders = orderService.getAllOrders()
        return orderEventBroadcaster.register(activeOrders)
    }

    @GetMapping("/driver/history")
    fun getDriverHistory(
        @AuthenticationPrincipal principal: PermissionDecorator
    ): ResponseEntity<BaseResponse<List<OrderResponse>>> {
        val userId = UUID.fromString(principal.getUserId())
        val orders = orderService.getDriverOrderHistory(userId)
        return ResponseEntity.ok(BaseResponse.success("Driver history retrieved successfully", orders))
    }

    @PostMapping
    fun createOrder(@RequestBody request: OrderCreateRequest): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.createOrder(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(BaseResponse.created("Order created successfully", order))
    }

    @PutMapping("/{id}")
    fun updateOrder(
        @PathVariable id: Long, 
        @RequestBody request: OrderCreateRequest
    ): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.updateOrder(id, request)
        return ResponseEntity.ok(BaseResponse.success("Order updated successfully", order))
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

    @PostMapping("/{id}/accept-broadcast")
    fun acceptBroadcastOrder(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: PermissionDecorator
    ): ResponseEntity<BaseResponse<OrderResponse>> {
        return try {
            val driverPhone = principal.username
            val order = orderService.acceptBroadcastOrder(id, driverPhone)
            ResponseEntity.ok(BaseResponse.success("Order accepted successfully", order))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(BaseResponse.error(e.message ?: "Could not accept order"))
        }
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
