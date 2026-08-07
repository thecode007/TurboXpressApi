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

/**
 * Single-day page returned by GET /api/orders/driver/work?date=YYYY-MM-DD.
 *
 * [hasPrevious] = delivered orders exist on a day before [date] for this driver.
 * [hasNext]     = delivered orders exist on a day after  [date] for this driver.
 *
 * Both flags are derived from lightweight COUNT queries — no extra order data loaded.
 */
data class DailyWorkPage(
    val date: String,
    val orderCount: Int,
    val dailyFees: Double,
    val orders: List<OrderResponse>,
    val hasPrevious: Boolean,
    val hasNext: Boolean
)
