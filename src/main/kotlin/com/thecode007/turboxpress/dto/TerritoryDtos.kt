package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank

data class TerritoryResponse(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val polygon: List<GeoPointDto>
)

data class CreateTerritoryRequest(
    @field:NotBlank(message = "Territory name is required")
    val name: String,

    @field:NotBlank(message = "Polygon WKT is required")
    val polygonWkt: String
)

data class UpdateTerritoryRequest(
    @field:NotBlank(message = "Territory name is required")
    val name: String,

    @field:NotBlank(message = "Polygon WKT is required")
    val polygonWkt: String,

    val isActive: Boolean
)
