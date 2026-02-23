package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.Positive

data class UpdateDeliveryZoneRequest(
    val name: String?,

    @field:Positive(message = "Base fee must be positive")
    val baseFee: Double?,

    val wktPolygon: String?,

    val isActive: Boolean?
)
