package com.thecode007.turboxpress.dto

data class DailyAveragePrepareTime(
    val dayName: String,
    val averageMinutes: Double
)

data class AveragePrepareTimeResponse(
    val targetMinutes: Int = 15,
    val overallAverageMinutes: Double,
    val dailyAverages: List<DailyAveragePrepareTime>
)

data class DailyOrderCount(
    val date: String,
    val count: Int
)

data class OrdersPerDayResponse(
    val totalOrders: Int,
    val durationDays: Int,
    val dailyCounts: List<DailyOrderCount>
)

data class DriverDeliveryStatDto(
    val driverId: String?,
    val driverFullName: String,
    val driverPhoneNumber: String?,
    val deliveryCount: Long,
    val totalRevenue: Double = 0.0
)

data class DriverDeliveriesResponse(
    val period: String, // "DAILY" or "MONTHLY"
    val totalDeliveries: Long,
    val driverStats: List<DriverDeliveryStatDto>
)
