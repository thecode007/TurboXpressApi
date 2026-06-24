package com.thecode007.turboxpress.dto

data class AppSettingResponse(
    val deliveryProfitPercent: Double,
    val restaurantSubscriptionFee: Double,
    val driverSubscriptionFee: Double,
    val pricePerKm: Double,
    val baseFare: Double
)

data class UpdateAppSettingRequest(
    val deliveryProfitPercent: Double,
    val restaurantSubscriptionFee: Double,
    val driverSubscriptionFee: Double,
    val pricePerKm: Double,
    val baseFare: Double
)
