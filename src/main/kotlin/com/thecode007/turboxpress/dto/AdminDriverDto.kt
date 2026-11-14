package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class DriverResponse(
    val phoneNumber: String,
    val username: String,
    val fullName: String,
    val profilePictureUrl: String?,
    @field:JsonProperty("isActive")
    val isActive: Boolean,
    val onlineStatus: String,
    val monthlySubFee: Double,
    val billingCycle: String,
    val nextBillingDate: String?,
    val createdAt: String?,
    val adminDebtBalance: Double,
    val collectedCashBalance: Double,
    val dailyRate: Double,
    val orderCount: Int = 0,
    val activeOrderCount: Int = 0,
    val todayOnlineMinutes: Long = 0L,
    val todayOnlineHoursFormatted: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val territoryId: Long? = null,
    val territoryName: String? = null
)

data class CreateDriverRequest(
    val phoneNumber: String,
    val username: String,
    val fullName: String,
    val password: String,
    val profilePictureUrl: String? = null,
    val dailyRate: Double = 0.0,
    val territoryId: Long? = null
)

data class UpdateDriverRequest(
    val username: String,
    val fullName: String,
    val profilePictureUrl: String? = null,
    @field:JsonProperty("isActive")
    val isActive: Boolean,
    val dailyRate: Double = 0.0,
    val territoryId: Long? = null
)

data class ChangeDriverPasswordRequest(
    val newPassword: String
)
