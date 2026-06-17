package com.thecode007.turboxpress.dto

data class OrderCreateRequest(
    val restaurantId: Long,
    val items: List<OrderItemRequest>,
    val customerName: String,
    val customerPhone: String,
    val locationMethod: String,
    val deliveryZoneId: Long? = null,
    val whatsappMapLink: String? = null,
    val detailedAddress: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val routeDistanceKm: Double? = null,
    val deliveryFee: Double = 0.0
)
