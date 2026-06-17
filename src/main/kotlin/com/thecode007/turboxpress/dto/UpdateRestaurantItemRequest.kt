package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateRestaurantItemRequest(
    val title: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val category: String? = null,
    @get:JsonProperty("available")
    val isAvailable: Boolean? = null,
    val photoUrls: List<String>? = null
)
