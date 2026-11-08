package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.BatchAssignDriverRequest
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
class OrderController(
    private val orderService: OrderService
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

    @GetMapping("/active-deliveries")
    fun getActiveDeliveries(
        @AuthenticationPrincipal principal: PermissionDecorator
    ): ResponseEntity<BaseResponse<List<OrderResponse>>> {
        val userId = UUID.fromString(principal.getUserId())
        val orders = orderService.getActiveDeliveriesForDriver(userId)
        return ResponseEntity.ok(BaseResponse.success("Active deliveries retrieved successfully", orders))
    }


    @GetMapping("/driver/history")
    fun getDriverHistory(
        @AuthenticationPrincipal principal: PermissionDecorator
    ): ResponseEntity<BaseResponse<com.thecode007.turboxpress.dto.DriverWorkResponse>> {
        val userId = UUID.fromString(principal.getUserId())
        val orders = orderService.getDriverOrderHistory(userId)
        return ResponseEntity.ok(BaseResponse.success("Driver work retrieved successfully", orders))
    }

    /**
     * GET /api/orders/driver/work?date=2026-08-08
     *
     * Returns a single day of this driver's DELIVERED orders.
     * Omit [date] to request today's page.
     *
     * Response is served from the server-side cache on repeated requests;
     * cache is evicted automatically when any order transitions to DELIVERED.
     */
    @GetMapping("/driver/work")
    fun getDriverWorkPage(
        @AuthenticationPrincipal principal: PermissionDecorator,
        @RequestParam(required = false) date: String?
    ): ResponseEntity<BaseResponse<com.thecode007.turboxpress.dto.DailyWorkPage>> {
        val userId = UUID.fromString(principal.getUserId())
        val page = orderService.getDriverWorkPage(userId, date)
        return ResponseEntity.ok(BaseResponse.success("Driver work page retrieved successfully", page))
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

    @GetMapping("/{id:\\d+}")
    fun getOrderById(@PathVariable id: Long): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.getOrderById(id)
        return ResponseEntity.ok(BaseResponse.success("Order retrieved successfully", order))
    }

    private fun parseInstant(raw: String?): java.time.Instant? {
        return raw?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            val normalized = value.replace(' ', '+')
            runCatching { java.time.Instant.parse(normalized) }
                .recoverCatching { java.time.OffsetDateTime.parse(normalized).toInstant() }
                .recoverCatching {
                    java.time.LocalDateTime.parse(value)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                }
                .recoverCatching {
                    java.time.LocalDate.parse(value)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                }
                .getOrNull()
        }
    }

    @GetMapping
    fun getAllOrders(
        @RequestParam(required = false) since: String? = null,
        @RequestParam(required = false, defaultValue = "false") archive: Boolean = false,
        @RequestParam(required = false) query: String? = null,
        @RequestParam(required = false) status: OrderStatus? = null,
        @RequestParam(required = false) restaurantId: Long? = null
    ): ResponseEntity<BaseResponse<List<OrderResponse>>> {
        if (archive) {
            val orders = orderService.getArchivedOrders(
                query = query,
                status = status,
                restaurantId = restaurantId
            )
            return ResponseEntity.ok(BaseResponse.success("Archived orders retrieved successfully", orders))
        }

        val sinceInstant = parseInstant(since)
        val orders = orderService.getAllOrders(sinceInstant)
        return ResponseEntity.ok(BaseResponse.success("Orders retrieved successfully", orders))
    }

    @GetMapping("/archive")
    fun getArchivedOrders(
        @RequestParam(required = false) query: String? = null,
        @RequestParam(required = false) status: OrderStatus? = null,
        @RequestParam(required = false) restaurantId: Long? = null,
        @RequestParam(required = false) from: String? = null,
        @RequestParam(required = false) to: String? = null
    ): ResponseEntity<BaseResponse<List<OrderResponse>>> {
        val fromInstant = parseInstant(from)
        val toInstant = parseInstant(to)
        val orders = orderService.getArchivedOrders(
            query = query,
            status = status,
            restaurantId = restaurantId,
            from = fromInstant,
            to = toInstant
        )
        return ResponseEntity.ok(BaseResponse.success("Archived orders retrieved successfully", orders))
    }

    @PutMapping("/assign-driver/batch")
    fun batchAssignDriver(
        @RequestBody request: BatchAssignDriverRequest
    ): ResponseEntity<BaseResponse<List<OrderResponse>>> {
        val orders = orderService.batchAssignDriver(request.orderIds, request.driverPhoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Drivers assigned successfully to ${orders.size} orders", orders))
    }

    @PostMapping("/assign-driver/batch")
    fun batchAssignDriverPost(
        @RequestBody request: BatchAssignDriverRequest
    ): ResponseEntity<BaseResponse<List<OrderResponse>>> {
        val orders = orderService.batchAssignDriver(request.orderIds, request.driverPhoneNumber)
        return ResponseEntity.ok(BaseResponse.success("Drivers assigned successfully to ${orders.size} orders", orders))
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

    @PatchMapping("/{id}/delivery-status")
    fun updateDeliveryStatus(
        @PathVariable id: Long,
        @RequestParam status: com.thecode007.turboxpress.entity.DeliveryStatus,
        @RequestParam(required = false) photoUrl: String? = null
    ): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.updateDeliveryStatus(id, status, photoUrl)
        return ResponseEntity.ok(BaseResponse.success("Delivery status updated successfully", order))
    }

    @PatchMapping("/{id}/pickup-photo")
    fun updatePickupPhoto(
        @PathVariable id: Long,
        @RequestParam photoUrl: String
    ): ResponseEntity<BaseResponse<OrderResponse>> {
        val order = orderService.setPickupPhoto(id, photoUrl)
        return ResponseEntity.ok(BaseResponse.success("Pickup photo updated successfully", order))
    }
}
