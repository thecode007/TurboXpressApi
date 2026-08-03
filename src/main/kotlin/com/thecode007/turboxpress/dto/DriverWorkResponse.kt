package com.thecode007.turboxpress.dto

data class DailyWorkSummary(
    val date: String,
    val orderCount: Int,
    val dailyFees: Double,
    val orders: List<OrderResponse>
)

data class DriverWorkResponse(
    val totalOrders: Int,
    val totalDeliveryFees: Double,
    val groupedByDay: List<DailyWorkSummary>
)
