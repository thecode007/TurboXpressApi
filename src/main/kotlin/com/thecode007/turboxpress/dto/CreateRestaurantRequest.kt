package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateRestaurantRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    val logoUrl: String? = null,

    @field:NotNull(message = "Latitude is required")
    val latitude: Double,

    @field:NotNull(message = "Longitude is required")
    val longitude: Double,

    @field:NotBlank(message = "Owner phone number is required")
    val ownerPhoneNumber: String
)
