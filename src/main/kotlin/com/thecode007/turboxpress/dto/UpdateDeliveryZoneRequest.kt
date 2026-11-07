package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpdateDeliveryZoneRequest(
    val name: String? = null,

    val wktPolygon: String? = null,

    val isActive: Boolean? = null,

    val centerLat: Double? = null,

    val centerLng: Double? = null
)
