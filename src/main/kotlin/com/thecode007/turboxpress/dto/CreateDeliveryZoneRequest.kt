package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.validation.constraints.NotBlank

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateDeliveryZoneRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:NotBlank(message = "WKT Polygon is required")
    val wktPolygon: String,

    val isActive: Boolean = true,

    val centerLat: Double? = null,

    val centerLng: Double? = null
)
