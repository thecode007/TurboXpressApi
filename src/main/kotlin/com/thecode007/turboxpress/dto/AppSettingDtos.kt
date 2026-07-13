package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class AppSettingResponse(
    val deliveryProfitPercent: Double,
    val restaurantSubscriptionFee: Double,
    val driverSubscriptionFee: Double,
    val pricePerKm: Double,
    val baseFare: Double,
    @JsonProperty("isAutoAssignEnabled")
    val isAutoAssignEnabled: Boolean
)

data class UpdateAppSettingRequest(
    val deliveryProfitPercent: Double,
    val restaurantSubscriptionFee: Double,
    val driverSubscriptionFee: Double,
    val pricePerKm: Double,
    val baseFare: Double,
    @JsonProperty("isAutoAssignEnabled")
    val isAutoAssignEnabled: Boolean
)
