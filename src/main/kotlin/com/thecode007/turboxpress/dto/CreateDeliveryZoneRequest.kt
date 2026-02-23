package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class CreateDeliveryZoneRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:NotNull(message = "Base fee is required")
    @field:Positive(message = "Base fee must be positive")
    val baseFee: Double,

    @field:NotBlank(message = "WKT Polygon is required")
    val wktPolygon: String,

    val isActive: Boolean = true
)
