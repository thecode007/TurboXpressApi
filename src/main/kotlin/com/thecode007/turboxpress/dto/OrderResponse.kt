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
    val customerName: String,
    val customerPhone: String,
    val locationMethod: String,
    val deliveryZoneId: Long?,
    val whatsappMapLink: String?,
    val detailedAddress: String?,
    val latitude: Double?,
    val longitude: Double?,
    val driverLat: Double?,
    val driverLng: Double?,
    val routeDistanceKm: Double?,
    val deliveryFee: Double,
    val createdAt: Instant
)
