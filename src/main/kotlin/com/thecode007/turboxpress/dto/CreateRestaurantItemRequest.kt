package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class CreateRestaurantItemRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,

    val description: String? = null,

    @field:NotNull(message = "Price is required")
    @field:Positive(message = "Price must be positive")
    val price: Double,

    val photoUrls: List<String> = emptyList()
)
