package com.thecode007.turboxpress.dto

data class UpdateRestaurantItemRequest(
    val title: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val photoUrls: List<String>? = null
)
