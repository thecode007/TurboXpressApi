package com.thecode007.turboxpress.dto

data class OrderItemResponse(
    val id: Long,
    val menuItemId: Long,
    val menuItemTitle: String,
    val quantity: Int,
    val priceAtOrder: Double
)
