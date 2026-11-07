package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

data class AppSettingResponse(
    val deliveryProfitPercent: Double,
    val restaurantSubscriptionFee: Double,
    val driverSubscriptionFee: Double,
    val pricePerKm: Double,
    val baseFare: Double,
    @get:JsonProperty("isAutoAssignEnabled")
    @param:JsonProperty("isAutoAssignEnabled")
    val isAutoAssignEnabled: Boolean
)

data class UpdateAppSettingRequest(
    val deliveryProfitPercent: Double,
    val restaurantSubscriptionFee: Double,
    val driverSubscriptionFee: Double,
    val pricePerKm: Double,
    val baseFare: Double,
    @get:JsonProperty("isAutoAssignEnabled")
    @param:JsonProperty("isAutoAssignEnabled")
    @JsonAlias("autoAssignEnabled", "isAutoAssignEnabled")
    val isAutoAssignEnabled: Boolean
)
