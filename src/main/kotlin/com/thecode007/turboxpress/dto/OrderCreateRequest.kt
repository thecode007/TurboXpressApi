package com.thecode007.turboxpress.dto

data class OrderCreateRequest(
    val restaurantId: Long,
    val items: List<OrderItemRequest>
)
