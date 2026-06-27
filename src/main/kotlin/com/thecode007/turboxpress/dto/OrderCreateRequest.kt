package com.thecode007.turboxpress.dto

data class OrderCreateRequest(
    val restaurantId: Long,
    val items: List<OrderItemRequest>,
    /** Customer phone number — used to look up or create the customer record. */
    val customerPhone: String,
    val customerName: String,
    /** Optional: override the customer's zone for this order (also saves back to customer). */
    val deliveryZoneId: Long? = null,
    val detailedAddress: String? = null,
    val routeDistanceKm: Double? = null,
    val deliveryFee: Double = 0.0
)
