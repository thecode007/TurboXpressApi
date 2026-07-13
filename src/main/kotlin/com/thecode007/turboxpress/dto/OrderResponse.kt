package com.thecode007.turboxpress.dto

import com.thecode007.turboxpress.entity.OrderStatus
import java.time.Instant

data class OrderResponse(
    val id: Long,
    val restaurantId: Long,
    val restaurantName: String,
    val driverPhoneNumber: String?,
    val driverFullName: String?,
    val driverId: String?,
    val status: OrderStatus,
    val totalAmount: Double,
    val items: List<OrderItemResponse>,
    // Customer info (denormalised from the customers table for easy reading)
    val customerId: Long,
    val customerName: String,
    val customerPhone: String,
    val deliveryZoneId: Long?,
    val deliveryZoneName: String?,
    val deliveryZonePolygon: List<RoutePointDto>? = null,
    val latitude: Double?,
    val longitude: Double?,
    val detailedAddress: String?,
    val locationMethod: String,
    // Geo helpers for the driver map
    val restaurantLat: Double?,
    val restaurantLng: Double?,
    val driverLat: Double?,
    val driverLng: Double?,
    val routeDistanceKm: Double?,
    val deliveryFee: Double,
    val createdAt: Instant,
    val acceptedAt: Instant? = null,
    val readyAt: Instant? = null,
    val pickedUpAt: Instant? = null,
    val deliveredAt: Instant? = null
)
