package com.thecode007.turboxpress.dto

import com.thecode007.turboxpress.entity.OrderStatus
import java.time.Instant

data class OrderResponse(
    val id: Long,
    val restaurantId: Long,
    val restaurantName: String,
    val driverPhoneNumber: String?,
    val driverFullName: String?,
    val status: OrderStatus,
    val totalAmount: Double,
    val items: List<OrderItemResponse>,
    val createdAt: Instant
)
