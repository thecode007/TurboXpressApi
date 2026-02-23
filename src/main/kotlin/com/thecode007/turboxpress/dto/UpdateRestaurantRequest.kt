package com.thecode007.turboxpress.dto

data class UpdateRestaurantRequest(
    val name: String? = null,
    val logoUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val ownerPhoneNumber: String? = null
)
