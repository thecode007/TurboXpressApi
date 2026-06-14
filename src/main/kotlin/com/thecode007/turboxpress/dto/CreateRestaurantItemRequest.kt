package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import com.fasterxml.jackson.annotation.JsonProperty

data class CreateRestaurantItemRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,

    val description: String? = null,

    @field:NotNull(message = "Price is required")
    @field:Positive(message = "Price must be positive")
    val price: Double,

    val category: String? = null,

    @get:JsonProperty("isAvailable")
    val isAvailable: Boolean = true,

    val photoUrls: List<String>? = emptyList()
)
