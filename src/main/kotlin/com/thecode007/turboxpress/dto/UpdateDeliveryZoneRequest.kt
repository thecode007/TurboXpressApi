package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.Positive

data class UpdateDeliveryZoneRequest(
    val name: String?,

    val wktPolygon: String?,

    val isActive: Boolean?
)
